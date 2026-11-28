package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** {@link PackDoctorMain#run}, in process, over the example packs and planted packs (#478, T017). */
class PackDoctorMainTest {

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
        int exit = PackDoctorMain.run(
                validate(args),
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
    void theExamplePacksWithFullRunReferencesAndAreValid() {
        Path root = configRoot();

        Run run = run("--config-root", root.toString(), "--full");

        assertThat(run.exit()).as(run.out()).isEqualTo(PackDoctorMain.VALID);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE", "REFERENCES", "RELATIONS");
        assertThat(run.report().get("problems")).isEmpty();
    }

    /** beta-pack, holding SHARE, and alpha-pack, which depends on it and relates its MINE to three targets. */
    private static void plantAlphaAndBeta(Path beta, Path alpha) throws IOException {
        String fragment = "nodes: []\nedges: []\n";
        Files.createDirectories(beta.resolve("directives"));
        Files.createDirectories(beta.resolve("drg"));
        Files.writeString(beta.resolve("pack.yaml"), """
                pack_id: 01M4P01D5TWB5FKZGNNQG3943S
                pack_version: 1.0.0
                parent_pack: null
                name: beta-pack
                pack_level: org
                """);
        Files.writeString(beta.resolve("drg/fragment.yaml"), fragment);
        Files.writeString(beta.resolve("directives/SHARE.directive.yaml"), """
                schema_version: '1.0'
                id: SHARE
                title: Share
                intent: Share
                enforcement: required
                """);
        Files.createDirectories(alpha.resolve("directives"));
        Files.createDirectories(alpha.resolve("drg"));
        Files.writeString(alpha.resolve("pack.yaml"), """
                pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                pack_version: 1.0.0
                parent_pack: null
                name: alpha-pack
                pack_level: org
                depends_on:
                - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
                  range: '>=1.0'
                """);
        Files.writeString(alpha.resolve("drg/fragment.yaml"), fragment);
        Files.writeString(alpha.resolve("directives/MINE.directive.yaml"), """
                schema_version: '1.0'
                id: MINE
                title: Mine
                intent: Mine
                enforcement: required
                """);
        Files.writeString(alpha.resolve("relations.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'directive:MINE', target: 'directive:SHARE', relation: requires}
                - {source: 'directive:MINE', target: 'gamma-pack::directive:SHARE', relation: requires}
                - {source: 'directive:MINE', target: 'beta-pack::directive:NOPE', relation: requires}
                """);
    }

    /**
     * {@code --full} answers each reference code registration answers, with its status (#632 item 3): beta-pack holds
     * SHARE, alpha-pack depends on it and relates its MINE to each target.
     */
    @Test
    void fullAnswersEveryReferenceCodeWithTheRegistrationsStatus() throws IOException {
        Path beta = temp.resolve("references").resolve("beta");
        Path alpha = temp.resolve("references").resolve("alpha");
        plantAlphaAndBeta(beta, alpha);

        Run run = run("--pack", beta.toString(), "--pack", alpha.toString(), "--full");

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE", "REFERENCES", "RELATIONS");
        assertThat(run.codes())
                .containsExactly(
                        "pack_reference_unqualified", "pack_reference_pack_missing", "pack_reference_artifact_missing");
        run.report()
                .get("problems")
                .forEach(problem -> assertThat(problem.get("status").asInt()).isEqualTo(422));
        Run plain = run("--pack", beta.toString(), "--pack", alpha.toString());
        assertThat(plain.exit())
                .as("validate without --full judges no reference")
                .isEqualTo(PackDoctorMain.VALID);
        assertThat(texts(plain.report().get("checks"))).containsExactly("PACK", "CLOSURE");
    }

    /**
     * base-pack holds the directive B_001 and, copied from the built-in example pack, the tactic problem-decomposition,
     * the toolguide execution-lanes, the styleguide boring-code-review and the procedure git-flow; org-pack depends on
     * it and holds the profile {@code p}, which writes {@code body} as its selection data (#673). other-pack, which some
     * references name, is in no compose.
     */
    private static Path[] plantProfilePacks(Path root, String body) throws IOException {
        Path base = root.resolve("base");
        Path org = root.resolve("org");
        for (String dir : List.of("directives", "drg", "tactics", "toolguides", "styleguides", "procedures")) {
            Files.createDirectories(base.resolve(dir));
        }
        Files.writeString(base.resolve("pack.yaml"), """
                pack_id: 01M4P01D5TWB5FKZGNNQG3943S
                pack_version: 1.0.0
                parent_pack: null
                name: base-pack
                pack_level: org
                """);
        Files.writeString(base.resolve("drg/fragment.yaml"), "nodes: []\nedges: []\n");
        Files.writeString(base.resolve("directives/B_001.directive.yaml"), """
                schema_version: '1.0'
                id: B_001
                title: Base one
                intent: Base one
                enforcement: required
                """);
        Path builtIn = ExamplePacks.pack(ExamplePacks.BUILT_IN);
        for (String file : List.of(
                "tactics/problem-decomposition.tactic.yaml",
                "toolguides/execution-lanes.toolguide.yaml",
                "toolguides/execution-lanes.guide.md",
                "styleguides/boring-code-review.styleguide.yaml",
                "procedures/git-flow.procedure.yaml")) {
            Files.copy(builtIn.resolve(file), base.resolve(file));
        }
        Files.createDirectories(org.resolve("agent_profiles"));
        Files.createDirectories(org.resolve("drg"));
        Files.writeString(org.resolve("pack.yaml"), """
                pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                pack_version: 1.0.0
                parent_pack: null
                name: org-pack
                pack_level: org
                depends_on:
                - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
                  range: '>=1.0'
                """);
        Files.writeString(org.resolve("drg/fragment.yaml"), "nodes: []\nedges: []\n");
        Files.writeString(org.resolve("agent_profiles/p.agent.yaml"), """
                profile-id: p
                name: P
                role: implementer
                purpose: x
                specialization:
                  primary-focus: f
                """ + body);
        return new Path[] {base, org};
    }

    private Run fullOverProfilePacks(String name, String body) throws IOException {
        Path[] packs = plantProfilePacks(temp.resolve(name), body);
        return run("--pack", packs[0].toString(), "--pack", packs[1].toString(), "--full");
    }

    private static String directive(String code) {
        return "directive-references:\n- code: " + code + "\n  name: n\n  rationale: r\n";
    }

    private static String cites(String field, String id) {
        return field + ":\n- id: " + id + "\n  rationale: r\n";
    }

    private static String procedure(String id) {
        return "collaboration:\n  operating-procedures:\n  - " + id + "\n";
    }

    /** The four cases of #673, and a fault in every other reference list of a profile, with the reference as judged. */
    static Stream<org.junit.jupiter.params.provider.Arguments> profileReferenceFaults() {
        return Stream.concat(directiveReferenceFaults(), otherListFaults());
    }

    /** The four cases of #673, in a profile's directive references. */
    private static Stream<org.junit.jupiter.params.provider.Arguments> directiveReferenceFaults() {
        return Stream.of(
                arguments(
                        "bare, held only by another pack",
                        directive("B_001"),
                        "pack_reference_unqualified",
                        "directive:B_001"),
                arguments(
                        "pack outside the closure",
                        directive("other-pack::O_001"),
                        "pack_reference_pack_missing",
                        "other-pack::directive:O_001"),
                arguments(
                        "absent from the named pack",
                        directive("base-pack::B_404"),
                        "pack_reference_artifact_missing",
                        "base-pack::directive:B_404"),
                arguments(
                        "kind written out, absent",
                        directive("base-pack::directive:B_404"),
                        "pack_reference_artifact_missing",
                        "base-pack::directive:B_404"));
    }

    /** One fault in each of the profile's other reference lists, and one in an exclusion of a reference list. */
    private static Stream<org.junit.jupiter.params.provider.Arguments> otherListFaults() {
        return Stream.of(
                arguments(
                        "a tactic, bare",
                        cites("tactic-references", "problem-decomposition"),
                        "pack_reference_unqualified",
                        "tactic:problem-decomposition"),
                arguments(
                        "a toolguide absent from the named pack",
                        cites("toolguide-references", "base-pack::gradle"),
                        "pack_reference_artifact_missing",
                        "base-pack::toolguide:gradle"),
                arguments(
                        "a styleguide outside the closure",
                        cites("styleguide-references", "other-pack::styleguide:boring"),
                        "pack_reference_pack_missing",
                        "other-pack::styleguide:boring"),
                arguments(
                        "an exclusion outside the closure",
                        "excluding:\n  directive-references:\n  - other-pack::O_001\n",
                        "pack_reference_pack_missing",
                        "other-pack::directive:O_001"),
                arguments(
                        "an operating procedure, bare",
                        procedure("git-flow"),
                        "pack_reference_unqualified",
                        "procedure:git-flow"));
    }

    /**
     * {@code --full} judges an agent profile's selection references as registration does (#673, ruling of 2026-11-26):
     * the code, the status and the detail naming the reference as judged, the same as
     * {@code registration/profile-references.feature} answers.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("profileReferenceFaults")
    void fullJudgesAnAgentProfilesReferences(String label, String body, String code, String written)
            throws IOException {
        Run run = fullOverProfilePacks(label.replace(' ', '-').replace(",", ""), body);

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE", "REFERENCES", "RELATIONS");
        assertThat(run.codes()).containsExactly(code);
        JsonNode problem = run.report().get("problems").get(0);
        assertThat(problem.get("status").asInt()).isEqualTo(422);
        assertThat(problem.get("detail").asString()).contains("org-pack").contains("`" + written + "`");
    }

    /**
     * The control: each reference form of every list that resolves is no problem, and a bare one no pack holds is none
     * either, as for a graph edge's endpoint.
     */
    @Test
    void fullPassesAnAgentProfileWhoseReferencesResolve() throws IOException {
        Run run = fullOverProfilePacks(
                "resolving",
                directive("base-pack::B_001") + "- code: base-pack::directive:B_001\n  name: n\n  rationale: r\n"
                        + "- code: HELD_BY_NONE\n  name: n\n  rationale: r\n"
                        + cites("tactic-references", "base-pack::problem-decomposition")
                        + cites("toolguide-references", "base-pack::toolguide:execution-lanes")
                        + cites("styleguide-references", "base-pack::boring-code-review")
                        + procedure("base-pack::git-flow"));

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.VALID);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE", "REFERENCES", "RELATIONS");
    }

    /**
     * An entry that is no reference of its list is refused when the pack is read (#673): by the schema (another kind
     * written out), and by the profile's rule (a pack name past the pack-id cap, which the schema's pattern lets through).
     */
    static Stream<org.junit.jupiter.params.provider.Arguments> entriesThatAreNoReference() {
        return Stream.of(
                arguments(
                        "another kind written out",
                        cites("tactic-references", "directive:B_001"),
                        "#/tactic-references/0/id",
                        "does not match its pattern"),
                arguments(
                        "a pack name past the cap",
                        directive("a".repeat(64) + "::B_001"),
                        "",
                        "by a pack id of at most 63 characters"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entriesThatAreNoReference")
    void anEntryThatIsNoReferenceIsRefusedAtTheProfile(String label, String body, String pointer, String detail)
            throws IOException {
        Run run = fullOverProfilePacks(label.replace(' ', '-'), body);

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.codes()).containsExactly("validation_failed");
        assertThat(run.out())
                .contains("artifact_schema_error")
                .contains("agent_profiles/p.agent.yaml" + pointer)
                .contains(detail);
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
        assertThat(report.propertyNames()).containsExactly("tool", "inputs", "checks", "packs", "problems", "summary");
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

        // The same report, but for its inputs, which name what was given: the --pack values, not the config root.
        ObjectNode expected = (ObjectNode) configured.report().deepCopy();
        ObjectNode inputs = JSON.createObjectNode();
        args.stream().filter(arg -> !"--pack".equals(arg)).forEach(inputs.putArray("packs")::add);
        inputs.putObject("options").put("full", false).put("round_trip", false);
        expected.set("inputs", inputs);
        assertThat(given.exit()).isZero();
        assertThat(JSON.writeValueAsString(given.report())).isEqualTo(JSON.writeValueAsString(expected));
        assertThat(given.err()).isEqualTo(configured.err());
    }

    @Test
    void aDependentPackAloneFailsOnItsUnknownDependencies() {
        Path project = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp.resolve("packs"));

        Run run = run("--pack", project + ":project");

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
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

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
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

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.VALID);
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

        assertThat(run.exit()).as(run.out()).isEqualTo(PackDoctorMain.VALID);
        assertThat(texts(run.report().get("checks"))).containsExactly("PACK", "CLOSURE", "ROUND_TRIP");
        assertThat(run.report().get("problems")).isEmpty();
        assertThat(names(run.report())).containsExactly(ExamplePacks.BUILT_IN);
    }

    /** The composition root hands the round trip the given id, never one taken from the directory. */
    @Test
    void theRoundTripIsHandedTheGivenId() throws IOException {
        Path pack = misnamed(ExamplePacks.BUILT_IN, "Bad_Name");
        List<String> handed = new ArrayList<>();
        PackDoctorMain.Composition recording = new PackDoctorMain.Composition(
                new FileSystemPackInspector(),
                (packId, source, target) -> {
                    handed.add(packId.value() + " " + source);
                    return PackRoundTrip.run(packId, source, target);
                },
                PackDoctorMain::remediated);

        Path temporary = Files.createDirectories(temp.resolve("tmp"));

        Run run = run(temporary, recording, "--pack", pack + "=" + ExamplePacks.BUILT_IN, "--round-trip");

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.VALID);
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

        assertThat(run.exit()).as(run.out()).isEqualTo(PackDoctorMain.VALID);
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

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
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
    void aTacticOfManySchemaViolationsIsOneProblemThatListsTheFirstHundredAndCountsTheRest() throws IOException {
        // #648: what the command adds to the collection, which PackRegistrationScaleTest holds to its order and
        // PackViolationCollectionBudgetTest to a time: the cap of listed violations, the first hundred in order, and
        // the detail that counts the others.
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, temp.resolve("packs"));
        String tactic = "tactics/reasons-canvas-fill.tactic.yaml";
        StringBuilder text =
                new StringBuilder("schema_version: \"1.0\"\nid: reasons-canvas-fill\nname: Fill\nsteps:\n");
        for (int i = 0; i < 300; i++) {
            text.append("  - {title: t, description: 1}\n");
        }
        Files.writeString(pack.resolve(tactic), text);

        Run run = run("--pack", pack + ":org");

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        JsonNode problem = run.report().get("problems").get(0);
        assertThat(problem.get("code").asString()).isEqualTo("validation_failed");
        assertThat(problem.get("detail").asString())
                .isEqualTo("The pack cannot be registered: 300 fault(s), the first at " + tactic
                        + "#/steps/0/description. 200 more violations are not listed.");
        assertThat(problem.get("violations")).hasSize(Problems.MAX_VIOLATIONS);
        assertThat(problem.get("violations").get(0).get("pointer").asString())
                .isEqualTo(tactic + "#/steps/0/description");
        assertThat(problem.get("violations")
                        .get(Problems.MAX_VIOLATIONS - 1)
                        .get("pointer")
                        .asString())
                .isEqualTo(tactic + "#/steps/99/description");
    }

    @Test
    void aPlantedDuplicateArtifactIdIsOneValidationFailedProblem() throws IOException {
        Path pack = GoldenSubjects.copy(
                subject("planted/duplicate-artifact-id" + GoldenSubjects.WITH_DESCRIPTOR), temp.resolve("plant"));

        Run run = run("--pack", pack.toString());

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
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

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
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

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
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

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
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

        assertThat(run.exit()).as(run.out()).isEqualTo(PackDoctorMain.PROBLEMS);
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

    /**
     * Operator ruling R34 (#631): an extra file in a pack (one no pack file kind models, beside one the {@code ignored}
     * fallback takes) is never processed and never fails. Reading the pack warns {@code file_not_held} at each, and the
     * round trip leaves both out: neither gets an outcome or a written copy, and the run is valid.
     */
    @Test
    void extraFilesInPacksAreNotProcessedAndAWarningIsLoggedOnIngest() throws IOException {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        Files.createDirectories(builtIn.resolve("qa"));
        Files.writeString(builtIn.resolve("qa/notes.txt"), "a stray log line\n");
        Files.writeString(builtIn.resolve("qa/notes.md"), "# A stray note\n");
        List<PackRoundTripResult> results = new ArrayList<>();
        PackDoctorMain.Composition recording = new PackDoctorMain.Composition(
                new FileSystemPackInspector(),
                (packId, source, target) -> {
                    PackRoundTripResult result = PackRoundTrip.run(packId, source, target);
                    results.add(result);
                    return result;
                },
                PackDoctorMain::remediated);

        for (boolean roundTrip : new boolean[] {false, true}) {
            Path temporary = Files.createDirectories(temp.resolve("tmp-" + roundTrip));
            Run run = roundTrip
                    ? run(temporary, recording, "--pack", builtIn + ":built-in", "--round-trip")
                    : run(temporary, recording, "--pack", builtIn + ":built-in");

            assertThat(run.exit()).as("round trip %s: %s", roundTrip, run.out()).isEqualTo(PackDoctorMain.VALID);
            assertThat(run.report().get("problems"))
                    .as("round trip %s", roundTrip)
                    .isEmpty();
            assertThat(run.err().lines())
                    .as("round trip %s: one file_not_held warning names each extra file", roundTrip)
                    .anySatisfy(line -> assertThat(line)
                            .startsWith("warning: built-in: file_not_held ")
                            .contains("qa/notes.txt"))
                    .anySatisfy(line -> assertThat(line)
                            .startsWith("warning: built-in: file_not_held ")
                            .contains("qa/notes.md"));
        }
        assertThat(results)
                .as("only the --round-trip run round-trips")
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.outcomes()).doesNotContainKeys("qa/notes.txt", "qa/notes.md");
                    assertThat(result.written()).doesNotContain("qa/notes.txt", "qa/notes.md");
                });
    }

    @Test
    void aUsageFaultExitsTwoWithNothingOnStandardOutput() {
        Run run = run("--pack", temp.resolve("missing").toString());

        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).contains(temp.resolve("missing").toString()).contains("usage: pack-doctor");
    }

    /** PR #608 delta review L1: an empty directory part is a usage fault, never the working directory checked. */
    @Test
    void anEmptyDirectoryPartExitsTwoWithNothingOnStandardOutput() {
        for (String value : List.of("=" + ExamplePacks.ORG, ":org")) {
            Run run = run("--pack", value);

            assertThat(run.exit()).as(value).isEqualTo(PackDoctorMain.FAILED);
            assertThat(run.out()).as(value).isEmpty();
            assertThat(run.err())
                    .as(value)
                    .contains("--pack " + value + " names no directory: its directory part is empty")
                    .contains("usage: pack-doctor");
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

        int exit = PackDoctorMain.run(
                new String[] {"validate", "--pack", builtIn + ":built-in"},
                broken,
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temp);

        assertThat(exit).isEqualTo(PackDoctorMain.FAILED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("cannot write to standard output");
    }

    @Test
    void aRoundTripWhoseTemporaryDirectoryCannotBeMadeExitsTwo() {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = PackDoctorMain.run(
                new String[] {"validate", "--pack", builtIn + ":built-in", "--round-trip"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temp.resolve("no-such-directory"));

        assertThat(exit).isEqualTo(PackDoctorMain.FAILED);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("temporary directory");
    }

    private static Run run(Path temporary, PackDoctorMain.Composition composition, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = PackDoctorMain.run(
                validate(args),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temporary,
                composition);
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** The {@code validate} command with {@code options}. */
    private static String[] validate(String... options) {
        return Stream.concat(Stream.of("validate"), Stream.of(options)).toArray(String[]::new);
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

        int exit = PackDoctorMain.run(
                new String[] {"--help"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temp,
                new PackDoctorMain.Composition(
                        new FileSystemPackInspector(), PackRoundTrip::run, PackDoctorMain::remediated),
                args -> {
                    throw new IllegalStateException("the parser broke on " + args[0]);
                });

        assertThat(exit).isEqualTo(PackDoctorMain.FAILED);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8))
                .startsWith("pack-doctor: internal error: java.lang.IllegalStateException: the parser broke on --help")
                .contains("\tat ");
    }

    /** Error handling: a reader that fails as no canonical pack makes it fail, an internal error of the run. */
    @Test
    void anInternalErrorExitsTwoWithNothingOnStandardOutput() {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        PackDoctorMain.Composition failing = new PackDoctorMain.Composition(
                root -> {
                    throw new IllegalStateException("the reader broke on " + root.name());
                },
                PackRoundTrip::run,
                PackDoctorMain::remediated);

        Run run = run(temp, failing, "--pack", builtIn + ":built-in");

        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err())
                .startsWith(
                        "pack-doctor: internal error: java.lang.IllegalStateException: the reader broke on built-in")
                .contains("\tat ");
    }

    /** Error handling: the round trip fails on the second pack, after the first was written. */
    @Test
    void aRoundTripThatFailsMidwayLeavesNoTemporaryDirectoryAndExitsTwo() throws IOException {
        Path root = configRoot();
        Path temporary = Files.createDirectories(temp.resolve("midway-tmp"));
        AtomicInteger calls = new AtomicInteger();
        PackDoctorMain.Composition midway = new PackDoctorMain.Composition(
                new FileSystemPackInspector(),
                (packId, source, target) -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new UncheckedIOException(new IOException("the disk is full"));
                    }
                    return PackRoundTrip.run(packId, source, target);
                },
                PackDoctorMain::remediated);

        Run run = run(temporary, midway, "--config-root", root.toString(), "--round-trip");

        assertThat(calls).hasValue(2);
        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
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
        PackDoctorMain.Composition midway = new PackDoctorMain.Composition(
                new FileSystemPackInspector(),
                (packId, source, target) -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new IllegalStateException("the flow broke");
                    }
                    return PackRoundTrip.run(packId, source, target);
                },
                PackDoctorMain::remediated);

        Run run = run(temporary, midway, "--config-root", root.toString(), "--round-trip");

        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err())
                .startsWith("pack-doctor: internal error: java.lang.IllegalStateException: the flow broke");
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

        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
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
