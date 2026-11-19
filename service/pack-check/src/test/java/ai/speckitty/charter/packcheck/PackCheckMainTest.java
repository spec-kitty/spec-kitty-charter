package ai.speckitty.charter.packcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /** The five example packs under {@code root/packs/}, a config root. */
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
        assertThat(problem.get("instance").asString()).isEqualTo(pack.toString());
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

    private static GoldenSubjects.Subject subject(String key) {
        return GoldenSubjects.subjects().stream()
                .filter(subject -> subject.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no golden subject " + key));
    }
}
