package ai.speckitty.charter.packcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@link PackCheckMain#run}, in process, over the example packs and planted packs (#478, T017). */
class PackCheckMainTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path temp;

    /** What one run answered. */
    private record Run(int exit, String out, String err) {
        JsonNode report() {
            return JSON.readTree(out);
        }

        List<String> codes() {
            List<String> codes = new ArrayList<>();
            report().get("problems")
                    .forEach(problem -> codes.add(problem.get("code").asString()));
            return codes;
        }
    }

    private Run run(String... args) {
        return run(temp.resolve("tmp"), args);
    }

    private static Run run(Path temporary, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            Files.createDirectories(temporary);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        int exit = PackCheckMain.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temporary);
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** The example packs under {@code root/packs/}, a config root. */
    private Path configRoot() {
        Path root = temp.resolve("root");
        ExamplePacks.names().forEach(name -> ExamplePacks.copyOf(name, root.resolve(ConfigRoot.PACKS)));
        return root;
    }

    private static List<String> names(JsonNode report) {
        List<String> names = new ArrayList<>();
        report.get("packs").forEach(pack -> names.add(pack.get("name").asString()));
        return names;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(node -> texts.add(node.asString()));
        return texts;
    }

    @Test
    void theExamplePacksAsAConfigRootAreValid() {
        Path root = configRoot();

        Run run = run("--config-root", root.toString());

        assertThat(run.exit()).isZero();
        JsonNode report = run.report();
        assertThat(texts(report.get("checks"))).containsExactly("PACK", "CLOSURE");
        assertThat(names(report))
                .containsExactlyElementsOf(
                        ExamplePacks.names().stream().sorted().toList());
        report.get("packs").forEach(pack -> {
            String name = pack.get("name").asString();
            assertThat(pack.get("level").asString())
                    .isEqualTo(ExamplePacks.level(name).token());
            assertThat(pack.get("path").asString())
                    .isEqualTo(root.resolve(ConfigRoot.PACKS).resolve(name).toString());
        });
        assertThat(report.has("skipped")).isFalse();
        assertThat(report.get("problems")).isEmpty();
        assertThat(report.propertyNames()).containsExactly("checks", "packs", "problems");
    }

    @Test
    void theSameSetAsRepeatedPackGivesTheSameReport() {
        Path root = configRoot();
        List<String> args = new ArrayList<>();
        for (String name : ExamplePacks.names().stream().sorted().toList()) {
            args.add("--pack");
            args.add(root.resolve(ConfigRoot.PACKS).resolve(name) + ":"
                    + ExamplePacks.level(name).token());
        }

        Run given = run(args.toArray(String[]::new));
        Run configured = run("--config-root", root.toString());

        assertThat(given.exit()).isZero();
        assertThat(given.out()).isEqualTo(configured.out());
        assertThat(given.err()).isEqualTo(configured.err());
    }

    @Test
    void aDependentPackAloneFailsOnItsUnknownDependencies() {
        Path project = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp.resolve("packs"));

        Run run = run("--pack", project + ":project");

        assertThat(run.exit()).isEqualTo(PackCheckMain.PROBLEMS);
        assertThat(run.codes()).containsExactly("pack_dependency_unknown", "pack_dependency_unknown");
        for (JsonNode problem : run.report().get("problems")) {
            assertThat(problem.get("status").asInt()).isEqualTo(422);
            assertThat(problem.get("title").asString()).isEqualTo("Pack dependency unknown");
            assertThat(problem.has("instance")).isFalse();
            assertThat(problem.get("packs")).isNotEmpty();
            assertThat(problem.get("packs").get(0).get("packId").asString()).isEqualTo(ExamplePacks.PROJECT);
        }
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE");
    }

    /** Error handling (#583, D5): a pack in a directory whose name is not a pack id. */
    @Test
    void aPackInADirectoryNamedBadNameExitsOneWithOnePackIdInvalidProblem() throws IOException {
        assertOnePackIdInvalidProblem(false);
    }

    /** Error handling (#583, D5): the round trip adds no second problem for the same name. */
    @Test
    void aPackInADirectoryNamedBadNameHasOneProblemWithTheRoundTripToo() throws IOException {
        assertOnePackIdInvalidProblem(true);
    }

    private void assertOnePackIdInvalidProblem(boolean roundTrip) throws IOException {
        Path pack = Files.move(
                ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs")),
                temp.resolve("packs").resolve("Bad_Name"));
        List<String> args = new ArrayList<>(List.of("--pack", pack.toString()));
        if (roundTrip) {
            args.add("--round-trip");
        }

        Run run = run(args.toArray(String[]::new));

        assertThat(run.exit()).as(run.err()).isEqualTo(PackCheckMain.PROBLEMS);
        assertThat(run.codes()).containsExactly("validation_failed");
        JsonNode problem = run.report().get("problems").get(0);
        assertThat(problem.get("status").asInt()).isEqualTo(422);
        assertThat(problem.get("instance").asString()).isEqualTo(pack.toUri().toString());
        assertThat(problem.get("violations")).singleElement().satisfies(violation -> {
            assertThat(violation.get("code").asString()).isEqualTo("pack_id_invalid");
            assertThat(violation.get("pointer").asString()).isEqualTo("pack");
            assertThat(violation.get("detail").asString()).contains("Bad_Name");
        });
        assertThat(names(run.report())).containsExactly("Bad_Name");
        JsonNode skip = run.report().get("skipped").get(0);
        assertThat(skip.get("check").asString()).isEqualTo("CLOSURE");
        assertThat(skip.get("reason").asString())
                .as("the skip names the cause, pack_id_invalid (PR #608 review L3)")
                .contains("Bad_Name (its composition facts cannot be read: pack_unreadable: pack_id_invalid: pack"
                        + " directory 'Bad_Name' is not a pack id.");
    }

    /**
     * PR #608 MEDIUM-3 (operator ruling B): a pack in a directory whose name is no pack id, given its registered id, is
     * read, judged and reported under that id; the directory's name plays no part. The id, {@code org-base}, is no level
     * token, so the report and the warnings name the id, not the level (PR #608 delta review L3).
     */
    @Test
    void aPackInADirectoryNamedBadNameGivenItsIdIsValid() throws IOException {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        Path pack = misnamed(ExamplePacks.ORG, "Bad_Name");

        // org-base depends on built-in, so the set holds both (ruling D5).
        Run run = run("--pack", builtIn + ":built-in", "--pack", pack + "=" + ExamplePacks.ORG);

        assertThat(run.exit()).as(run.err()).isEqualTo(PackCheckMain.VALID);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE");
        assertThat(run.report().get("problems")).isEmpty();
        JsonNode entry = run.report().get("packs").get(1);
        assertThat(entry.get("name").asString()).isEqualTo(ExamplePacks.ORG);
        assertThat(entry.get("level").asString()).isEqualTo("org");
        assertThat(entry.get("path").asString()).isEqualTo(pack.toString());
        assertThat(run.err().lines().filter(line -> !line.startsWith("warning: built-in: ")))
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).startsWith("warning: " + ExamplePacks.ORG + ": file_not_held "));
    }

    /** The round trip configures the pack under its given id too, not under its directory's name. */
    @Test
    void theRoundTripOfAPackGivenItsIdIsValid() throws IOException {
        Path pack = misnamed(ExamplePacks.BUILT_IN, "Bad_Name");
        Path temporary = temp.resolve("round-trip-tmp");

        Run run = run(temporary, "--pack", pack + ":built-in=" + ExamplePacks.BUILT_IN, "--round-trip");

        assertThat(run.exit()).as(run.out()).isEqualTo(PackCheckMain.VALID);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE", "ROUND_TRIP");
        assertThat(run.report().get("problems")).isEmpty();
        assertThat(names(run.report())).containsExactly(ExamplePacks.BUILT_IN);
    }

    /** The composition root hands the round trip the given id, never one taken from the directory. */
    @Test
    void theRoundTripIsHandedTheGivenId() throws IOException {
        Path pack = misnamed(ExamplePacks.BUILT_IN, "Bad_Name");
        List<String> handed = new ArrayList<>();
        PackCheckMain.Composition recording =
                new PackCheckMain.Composition(new FileSystemPackInspector(), (packId, source, target) -> {
                    handed.add(packId.value() + " " + source);
                    return PackRoundTrip.run(packId, source, target);
                });

        Path temporary = Files.createDirectories(temp.resolve("tmp"));

        Run run = run(temporary, recording, "--pack", pack + "=" + ExamplePacks.BUILT_IN, "--round-trip");

        assertThat(run.exit()).as(run.err()).isEqualTo(PackCheckMain.VALID);
        assertThat(handed).containsExactly(ExamplePacks.BUILT_IN + " " + pack);
    }

    /**
     * CLOSURE over a set whose depended-on packs are given by id (#583, D5): built-in and org-base sit in directories
     * whose names are no pack ids, and every other example pack depends on one of them.
     */
    @Test
    void aSetWhosePacksDependOnPacksGivenByIdResolves() throws IOException {
        Path root = configRoot();
        Path packs = root.resolve(ConfigRoot.PACKS);
        Path builtIn = Files.move(packs.resolve(ExamplePacks.BUILT_IN), packs.resolve("Core_Copy"));
        Path org = Files.move(packs.resolve(ExamplePacks.ORG), packs.resolve("Org_Copy"));
        List<String> args = new ArrayList<>();
        for (String name : ExamplePacks.names().stream().sorted().toList()) {
            String directory = switch (name) {
                case ExamplePacks.BUILT_IN -> builtIn + "=" + name;
                case ExamplePacks.ORG -> org + ":org=" + name;
                default -> packs.resolve(name).toString();
            };
            args.add("--pack");
            args.add(directory);
        }

        Run run = run(args.toArray(String[]::new));

        assertThat(run.exit()).as(run.out()).isEqualTo(PackCheckMain.VALID);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE");
        assertThat(run.report().has("skipped")).isFalse();
        assertThat(names(run.report()))
                .containsExactlyElementsOf(
                        ExamplePacks.names().stream().sorted().toList());
    }

    /** A problem names the pack by its given id, in its {@code packs}. */
    @Test
    void aProblemNamesThePackByItsGivenId() throws IOException {
        Path pack = misnamed(ExamplePacks.PROJECT, "Project_Copy");

        Run run = run("--pack", pack + ":project=" + ExamplePacks.PROJECT);

        assertThat(run.exit()).isEqualTo(PackCheckMain.PROBLEMS);
        assertThat(run.codes()).containsExactly("pack_dependency_unknown", "pack_dependency_unknown");
        for (JsonNode problem : run.report().get("problems")) {
            assertThat(problem.get("packs")).singleElement().satisfies(named -> {
                assertThat(named.get("packId").asString()).isEqualTo(ExamplePacks.PROJECT);
                assertThat(named.get("name").asString()).isEqualTo(ExamplePacks.PROJECT);
            });
        }
        assertThat(run.out()).doesNotContain("\"name\" : \"Project_Copy\"");
    }

    /** An example pack copied into {@code temp/packs/<directory>}. */
    private Path misnamed(String name, String directory) throws IOException {
        return Files.move(
                ExamplePacks.copyOf(name, temp.resolve("packs")),
                temp.resolve("packs").resolve(directory));
    }

    @Test
    void aPlantedDuplicateArtifactIdIsOneValidationFailedProblem() throws IOException {
        Path pack = GoldenSubjects.copy(
                subject("planted/duplicate-artifact-id" + GoldenSubjects.WITH_DESCRIPTOR), temp.resolve("plant"));

        Run run = run("--pack", pack.toString());

        assertThat(run.exit()).isEqualTo(PackCheckMain.PROBLEMS);
        assertThat(run.codes()).containsExactly("validation_failed");
        JsonNode problem = run.report().get("problems").get(0);
        assertThat(problem.get("title").asString()).isEqualTo("Validation failed");
        assertThat(problem.get("status").asInt()).isEqualTo(422);
        assertThat(problem.get("instance").asString()).isEqualTo(pack.toUri().toString());
        // Validate mode's findings for this subject (golden/validate.json): the org placement rule, then the plant's.
        assertThat(problem.get("detail").asString())
                .isEqualTo("The pack cannot be registered: 2 fault(s), the first at pack.yaml.");
        List<String> codes = new ArrayList<>();
        problem.get("violations")
                .forEach(violation -> codes.add(violation.get("code").asString()));
        assertThat(codes).containsExactly("org_fragment_missing", "duplicate_artifact_id");
        JsonNode duplicate = problem.get("violations").get(1);
        assertThat(duplicate.get("pointer").asString()).isEqualTo("tactics/b.tactic.yaml");
        assertThat(duplicate.get("detail").asString())
                .isEqualTo("tactics/b.tactic.yaml: tactic id 'same' is already declared in tactics/a.tactic.yaml");
    }

    @Test
    void aPlantedLinkIsRefusedAsALinkedEntry() throws IOException {
        Path pack;
        try {
            pack = GoldenSubjects.copy(subject("planted/pack-entry-linked-file"), temp.resolve("plant"));
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
            throw noLinks;
        }

        Run run = run("--pack", pack + ":org");

        assertThat(run.exit()).isEqualTo(PackCheckMain.PROBLEMS);
        assertThat(run.codes()).containsExactly("validation_failed");
        List<String> linked = new ArrayList<>();
        run.report().get("problems").get(0).get("violations").forEach(violation -> {
            if (violation.get("code").asString().equals("pack_entry_linked")) {
                linked.add(violation.get("pointer").asString());
            }
        });
        assertThat(linked).containsExactly("directives/002-b.directive.yaml");
    }

    @Test
    void aPlantedLinkWithTheRoundTripIsOneProblemForTheLink() throws IOException {
        Path pack;
        try {
            pack = GoldenSubjects.copy(subject("planted/pack-entry-linked-file"), temp.resolve("plant"));
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
            throw noLinks;
        }

        Run run = run(temp.resolve("round-trip-tmp"), "--pack", pack + ":org", "--round-trip");

        assertThat(run.exit()).isEqualTo(PackCheckMain.PROBLEMS);
        assertThat(run.codes())
                .as("one problem: validate mode's, no round-trip problem beside it")
                .containsExactly("validation_failed");
        List<String> atTheLink = new ArrayList<>();
        run.report().get("problems").get(0).get("violations").forEach(violation -> {
            if (violation.get("pointer").asString().equals("directives/002-b.directive.yaml")) {
                atTheLink.add(violation.get("code").asString());
            }
        });
        assertThat(atTheLink).containsExactly("pack_entry_linked");
    }

    @Test
    void theRoundTripOverTheExamplePacksIsValid() {
        Path root = configRoot();
        Path temporary = temp.resolve("round-trip-tmp");

        Run run = run(temporary, "--config-root", root.toString(), "--round-trip");

        assertThat(run.exit()).as(run.out()).isZero();
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE", "ROUND_TRIP");
        assertThat(run.report().get("problems")).isEmpty();
        try (Stream<Path> left = Files.list(temporary)) {
            assertThat(left).as("the run directory is deleted after the run").isEmpty();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void aPackWithNoKnownLevelHasNoLevelInTheReportAndFails() throws IOException {
        Path pack = GoldenSubjects.copy(subject("planted/control-no-pack-level-on-a-bare-path"), temp.resolve("plant"));

        Run run = run("--pack", pack.toString());

        assertThat(run.exit()).isEqualTo(PackCheckMain.PROBLEMS);
        JsonNode entry = run.report().get("packs").get(0);
        assertThat(entry.propertyNames()).containsExactly("name", "path");
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK");
        assertThat(run.report().get("skipped").get(0).get("check").asString()).isEqualTo("CLOSURE");
        assertThat(run.report()
                        .get("problems")
                        .get(0)
                        .get("violations")
                        .get(0)
                        .get("code")
                        .asString())
                .isEqualTo("pack_level_missing");
    }

    @Test
    void aCaseVariantDescriptorIsRefusedAsACaseVariant() throws IOException {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        Files.copy(builtIn.resolve("pack.yaml"), builtIn.resolve("Pack.yaml"));

        Run run = run("--pack", builtIn + ":built-in");

        assertThat(run.exit()).as(run.out()).isEqualTo(PackCheckMain.PROBLEMS);
        assertThat(run.codes()).containsExactly("validation_failed");
        List<String> atVariant = new ArrayList<>();
        run.report().get("problems").get(0).get("violations").forEach(violation -> {
            if (violation.get("pointer").asString().equals("Pack.yaml")) {
                atVariant.add(violation.get("code").asString());
            }
        });
        assertThat(atVariant).as(run.out()).containsExactly("case_variant");
    }

    @Test
    void aFileNotHeldIsAWarningOnStandardErrorNotAProblem() {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));

        Run run = run("--pack", builtIn + ":built-in");

        assertThat(run.exit()).isZero();
        assertThat(run.report().get("problems")).isEmpty();
        assertThat(run.err().lines().toList())
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).startsWith("warning: built-in: file_not_held "));
    }

    @Test
    void aUsageFaultExitsTwoWithNothingOnStandardOutput() {
        Run run = run("--pack", temp.resolve("missing").toString());

        assertThat(run.exit()).isEqualTo(PackCheckMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).contains(temp.resolve("missing").toString()).contains("usage: pack-check");
    }

    /** PR #608 delta review L1: an empty directory part is a usage fault, never the working directory checked. */
    @Test
    void anEmptyDirectoryPartExitsTwoWithNothingOnStandardOutput() {
        for (String value : List.of("=" + ExamplePacks.ORG, ":org")) {
            Run run = run("--pack", value);

            assertThat(run.exit()).as(value).isEqualTo(PackCheckMain.FAILED);
            assertThat(run.out()).as(value).isEmpty();
            assertThat(run.err())
                    .as(value)
                    .contains("--pack " + value + " names no directory: its directory part is empty")
                    .contains("usage: pack-check");
        }
    }

    @Test
    void helpPrintsTheUsageAndExitsZero() {
        Run run = run("--help");

        assertThat(run.exit()).isZero();
        assertThat(run.out()).isEqualTo(Arguments.USAGE);
        assertThat(run.err()).isEmpty();
    }

    @Test
    void aReportThatCannotBeWrittenExitsTwo() {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream broken = new PrintStream(
                new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        throw new IOException("closed");
                    }
                },
                true,
                StandardCharsets.UTF_8);

        int exit = PackCheckMain.run(
                new String[] {"--pack", builtIn + ":built-in"},
                broken,
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temp);

        assertThat(exit).isEqualTo(PackCheckMain.FAILED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("cannot write to standard output");
    }

    @Test
    void aRoundTripWhoseTemporaryDirectoryCannotBeMadeExitsTwo() {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = PackCheckMain.run(
                new String[] {"--pack", builtIn + ":built-in", "--round-trip"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temp.resolve("no-such-directory"));

        assertThat(exit).isEqualTo(PackCheckMain.FAILED);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("temporary directory");
    }

    private static Run run(Path temporary, PackCheckMain.Composition composition, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = PackCheckMain.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temporary,
                composition);
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static List<Path> left(Path temporary) throws IOException {
        try (Stream<Path> left = Files.list(temporary)) {
            return left.toList();
        }
    }

    /** Error handling (PR #608 review L5): a failure while reading the arguments is an internal error, exit 2. */
    @Test
    void anInternalErrorWhileReadingTheArgumentsExitsTwoWithNothingOnStandardOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = PackCheckMain.run(
                new String[] {"--help"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temp,
                new PackCheckMain.Composition(new FileSystemPackInspector(), PackRoundTrip::run),
                args -> {
                    throw new IllegalStateException("the parser broke on " + args[0]);
                });

        assertThat(exit).isEqualTo(PackCheckMain.FAILED);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8))
                .startsWith("pack-check: internal error: java.lang.IllegalStateException: the parser broke on --help")
                .contains("\tat ");
    }

    /** Error handling: a reader that fails as no canonical pack makes it fail, an internal error of the run. */
    @Test
    void anInternalErrorExitsTwoWithNothingOnStandardOutput() {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        PackCheckMain.Composition failing = new PackCheckMain.Composition(
                root -> {
                    throw new IllegalStateException("the reader broke on " + root.name());
                },
                PackRoundTrip::run);

        Run run = run(temp, failing, "--pack", builtIn + ":built-in");

        assertThat(run.exit()).isEqualTo(PackCheckMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err())
                .startsWith("pack-check: internal error: java.lang.IllegalStateException: the reader broke on built-in")
                .contains("\tat ");
    }

    /** Error handling: the round trip fails on the second pack, after the first was written. */
    @Test
    void aRoundTripThatFailsMidwayLeavesNoTemporaryDirectoryAndExitsTwo() throws IOException {
        Path root = configRoot();
        Path temporary = Files.createDirectories(temp.resolve("midway-tmp"));
        AtomicInteger calls = new AtomicInteger();
        PackCheckMain.Composition midway =
                new PackCheckMain.Composition(new FileSystemPackInspector(), (packId, source, target) -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new UncheckedIOException(new IOException("the disk is full"));
                    }
                    return PackRoundTrip.run(packId, source, target);
                });

        Run run = run(temporary, midway, "--config-root", root.toString(), "--round-trip");

        assertThat(calls).hasValue(2);
        assertThat(run.exit()).isEqualTo(PackCheckMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).contains("the disk is full");
        assertThat(left(temporary)).as("the run directory is deleted").isEmpty();
    }

    /** Error handling: as above, with an internal error in place of the I/O failure. */
    @Test
    void aRoundTripThatBreaksMidwayLeavesNoTemporaryDirectoryAndExitsTwo() throws IOException {
        Path root = configRoot();
        Path temporary = Files.createDirectories(temp.resolve("midway-tmp"));
        AtomicInteger calls = new AtomicInteger();
        PackCheckMain.Composition midway =
                new PackCheckMain.Composition(new FileSystemPackInspector(), (packId, source, target) -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new IllegalStateException("the flow broke");
                    }
                    return PackRoundTrip.run(packId, source, target);
                });

        Run run = run(temporary, midway, "--config-root", root.toString(), "--round-trip");

        assertThat(run.exit()).isEqualTo(PackCheckMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).startsWith("pack-check: internal error: java.lang.IllegalStateException: the flow broke");
        assertThat(left(temporary)).isEmpty();
    }

    @Test
    void aConfigRootChecksTheEntriesTheServiceServes() throws IOException {
        Path root = configRoot();
        Path packs = root.resolve(ConfigRoot.PACKS);
        Files.createDirectories(packs.resolve(".staging"));
        ExamplePacks.copyOf(ExamplePacks.BUILT_IN, packs.resolve("aside"));
        Files.move(packs.resolve("aside").resolve(ExamplePacks.BUILT_IN), packs.resolve("built-in.pack-old-123"));
        Files.delete(packs.resolve("aside"));

        Run run = run("--config-root", root.toString());

        assertThat(run.exit()).as(run.out()).isZero();
        assertThat(names(run.report()))
                .containsExactlyElementsOf(
                        ExamplePacks.names().stream().sorted().toList());
    }

    @Test
    void aDanglingConfigRootEntryIsAUsageFault() throws IOException {
        Path root = configRoot();
        Path gone = root.resolve(ConfigRoot.PACKS).resolve("gone");
        try {
            Files.createSymbolicLink(gone, temp.resolve("nowhere"));
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
            throw noLinks;
        }

        Run run = run("--config-root", root.toString());

        assertThat(run.exit()).isEqualTo(PackCheckMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).contains(gone + " is a link to no directory");
    }

    private static GoldenSubjects.Subject subject(String key) {
        return GoldenSubjects.subjects().stream()
                .filter(subject -> subject.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no golden subject " + key));
    }
}
