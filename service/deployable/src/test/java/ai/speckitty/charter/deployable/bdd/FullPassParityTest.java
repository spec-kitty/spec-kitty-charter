package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packdoctor.PackDoctorMain;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRegistrationService.Mode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SC-001 of mission pack-doctor-coverage-01M7YMQK (FR-006, #632 item 3, ruling R-s; plan D11): for every planted fault
 * of the mission's test set, the registration and {@code pack-doctor validate --full} give the same code. One
 * parameterized table drives both:
 *
 * <ul>
 *   <li>the registration as the service wires it ({@link PackRegistrationService}, a bean of the shipped context), the
 *       planted pack judged by its decisive steps in {@link Mode#DRY_RUN}, beside the base pack the table's
 *       dependent packs need, registered once;
 *   <li>{@link PackDoctorMain#run} in process, {@code validate --full} over the planted pack, with the base pack when it
 *       depends on it; its report validates against {@code PackDoctorReport}.
 * </ul>
 *
 * <p>Each row states the code (and, for a {@code validation_failed}, the violation codes) both must answer, so a row
 * cannot pass by both sides being wrong. pack-doctor answers exactly one problem per row, as the registration refuses
 * with one fault.
 *
 * <p>The {@code specializes_from} cycle row is the remainder ruling R-s leaves (#636 slices 2-4 and 7, recorded in
 * ADR-0083): the registration refuses it {@code specialization_cycle}, and {@code --full} does not report it yet. When
 * #636 lands it in the domain factory, that row flips to a parity row.
 *
 * <p>Every planted pack carries an empty {@code drg/fragment.yaml}, which validate mode asks of an org pack and the
 * registration holds, so the only fault of a row is the one it plants.
 */
@SpringBootTest(classes = CharterServiceApplication.class)
@DisplayName("SC-001: registration and pack-doctor validate --full give the same code for every planted fault")
class FullPassParityTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String SCHEMA = "charter/schemas/PackDoctorReport.yaml";

    private static final String BETA_ID = "01M4P01D5TWB5FKZGNNQG3943S";

    private static final String FRAGMENT = "nodes: []\nedges: []\n";

    private static Path configRoot = Path.of("");

    private static boolean betaRegistered;

    private static ContractConformance contract;

    @Autowired
    private PackRegistrationService registration;

    @TempDir
    Path temp;

    @DynamicPropertySource
    static void registrationOn(DynamicPropertyRegistry registry) throws IOException {
        configRoot = Files.createTempDirectory("charter-full-pass-parity");
        Files.createDirectories(configRoot.resolve(ConfigRoot.PACKS));
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.config-root", () -> configRoot.toString());
        registry.add("charter.registration.enabled", () -> "true");
    }

    @BeforeAll
    static void loadTheContract() {
        contract = new ContractConformance();
    }

    /**
     * One planted fault.
     *
     * @param fault what the row plants
     * @param pack the planted pack's files, by path
     * @param dependsOnBeta whether the planted pack depends on the base pack {@code beta}
     * @param code the code both answer; for the remainder row, the code the registration alone answers
     * @param violations the violation codes of a {@code validation_failed}, in order; empty for a closure code
     * @param fullReports whether {@code --full} reports it: false only for the remainder ruling R-s leaves to #636
     */
    record Row(
            String fault,
            Map<String, String> pack,
            boolean dependsOnBeta,
            String code,
            List<String> violations,
            boolean fullReports) {

        @Override
        public String toString() {
            return fault;
        }
    }

    // ---- the base pack and the two planted packs' common files ----------------------------------------------------

    private static Map<String, String> beta() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.yaml", """
                pack_id: %s
                pack_version: 1.0.0
                parent_pack: null
                name: beta
                pack_level: org
                """.formatted(BETA_ID));
        files.put("drg/fragment.yaml", FRAGMENT);
        files.put("directives/SHARE.directive.yaml", directive("SHARE"));
        files.put("tactics/other.tactic.yaml", """
                schema_version: "1.0"
                id: other
                name: Other
                purpose: Another tactic.
                steps:
                  - title: Do it
                    description: Do it.
                """);
        files.put("missions/software-dev/software-dev.mission-type.yaml", """
                schema_version: "1.0"
                id: software-dev
                title: Software development
                description: Build software
                steps:
                  - id: plan
                    title: Plan
                    role: planner
                    prompt_path: steps/plan.prompt.md
                """);
        files.put("missions/software-dev/steps/plan.prompt.md", """
                # Plan

                <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
                """);
        files.put("mission_type.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes:
                - urn: action:software-dev/plan
                  kind: action
                  label: plan
                edges:
                - source: mission_type:software-dev
                  target: action:software-dev/plan
                  relation: requires
                """);
        return files;
    }

    /** alpha, which depends on beta, with one directive, MINE, and the files a row adds. */
    private static Map<String, String> alpha(Map<String, String> added) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.yaml", """
                pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                pack_version: 1.0.0
                parent_pack: null
                name: alpha
                pack_level: org
                depends_on:
                - pack_id: %s
                  range: '>=1.0'
                """.formatted(BETA_ID));
        files.put("drg/fragment.yaml", FRAGMENT);
        files.put("directives/MINE.directive.yaml", directive("MINE"));
        files.putAll(added);
        return files;
    }

    /** acme, standing alone, with one directive, A, and the files a row adds. */
    private static Map<String, String> acme(Map<String, String> added) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.yaml", """
                pack_id: 01M4P01D5TWB5FKZGNNQG3943T
                pack_version: 1.0.0
                parent_pack: null
                name: acme
                pack_level: org
                """);
        files.put("drg/fragment.yaml", FRAGMENT);
        files.put("directives/A.directive.yaml", directive("A"));
        files.putAll(added);
        return files;
    }

    private static String directive(String id) {
        return """
                schema_version: '1.0'
                id: %s
                title: %s
                intent: The directive %s
                enforcement: required
                """.formatted(id, id, id);
    }

    private static Map<String, String> relates(String target) {
        return Map.of("relations.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'directive:MINE', target: '%s', relation: requires}
                """.formatted(target));
    }

    private static Row closure(String fault, Map<String, String> added, String code) {
        return new Row(fault, alpha(added), true, code, List.of(), true);
    }

    private static Row refusedAtTheFile(String fault, Map<String, String> added, String violation) {
        return new Row(fault, acme(added), false, "validation_failed", List.of(violation), true);
    }

    static Stream<Row> rows() {
        return Stream.of(
                // REFERENCES (#632 item 3, ruling R-s): the three checks --full adds.
                closure(
                        "an unqualified reference (a bare reference only another pack holds)",
                        relates("directive:SHARE"),
                        "pack_reference_unqualified"),
                closure(
                        "a missing referenced artifact",
                        relates("beta::directive:NOPE"),
                        "pack_reference_artifact_missing"),
                closure(
                        "a referenced pack the compose does not hold",
                        relates("gamma::directive:SHARE"),
                        "pack_reference_pack_missing"),
                closure(
                        "a missing target step",
                        stepEnhancementOf("beta::action:software-dev/nosuch"),
                        "pack_reference_artifact_missing"),
                new Row(
                        "an unresolved substep delegation",
                        alpha(delegationTo("tdd")),
                        true,
                        "validation_failed",
                        List.of("substep_delegation_unresolved"),
                        true),
                // CLOSURE's preset-item phase (WP03).
                new Row(
                        "a must_have preset item no pack of the own closure holds",
                        acme(preset("""
                                activated_directives:
                                  - id: A
                                    must_have: true
                                  - id: B
                                    must_have: true
                                """)),
                        false,
                        "preset_item_not_held",
                        List.of("preset_item_not_held"),
                        true),
                new Row(
                        "a preset item no pack of the own closure declares",
                        acme(preset("""
                                activated_tactics:
                                  - id: ghost
                                """)),
                        false,
                        "preset_item_undeclared",
                        List.of("preset_item_undeclared"),
                        true),
                // The anti-pattern refusals (WP10 to WP12), validate mode's at the file.
                refusedAtTheFile(
                        "an anti-pattern file missing severity",
                        Map.of("anti_patterns/clever-over-direct.anti-pattern.yaml", """
                                schema_version: '1.0'
                                id: clever-over-direct
                                title: Clever over direct
                                description: A clever feature where a direct idiom would be easier to review.
                                bad_example: next(...)
                                """),
                        "artifact_schema_error"),
                refusedAtTheFile(
                        "an inline anti_patterns block",
                        Map.of("styleguides/java-style.styleguide.yaml", """
                                schema_version: '1.0'
                                id: java-style
                                title: Java style
                                scope: code
                                principles:
                                - name: Prefer immutability
                                  description: Make a value final unless it must change.
                                anti_patterns:
                                - name: Mutable by default
                                  description: A field that is never reassigned is left mutable.
                                  bad_example: private String name;
                                  good_example: private final String name;
                                """),
                        "artifact_schema_error"),
                refusedAtTheFile(
                        "a file-less anti_pattern node", Map.of("styleguide.graph.yaml", """
                                schema_version: '1.0'
                                generated_at: STATIC
                                nodes:
                                - urn: anti_pattern:x
                                  kind: anti_pattern
                                  label: X
                                edges:
                                - source: directive:A
                                  target: anti_pattern:x
                                  relation: rejects
                                """), "drg_schema_error"),
                // The remainder ruling R-s leaves: the specializes_from cycle lands in the domain factory with #636
                // (slices 2-4 and 7, ADR-0083). The registration refuses it; --full does not report it yet. When #636
                // lands it, this row becomes a parity row (fullReports true).
                new Row(
                        "a specializes_from cycle (#636: registration only, for now)",
                        acme(specializationCycle()),
                        false,
                        "specialization_cycle",
                        List.of(),
                        false));
    }

    private static Map<String, String> stepEnhancementOf(String target) {
        return Map.of(
                "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml",
                """
                schema_version: '1.0'
                id: spdd-plan
                prompt_fragments:
                  - slot: spdd:reasons-block
                    path: spdd-plan.prompt-fragment.md
                """,
                "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md",
                "Fill or update the REASONS canvas while composing the plan.\n",
                "enhancements.graph.yaml",
                """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: step_enhancement:spdd-plan
                  target: %s
                  relation: enhances
                """.formatted(target));
    }

    private static Map<String, String> delegationTo(String tactic) {
        return Map.of(
                "missions/rx/rx.mission-type.yaml", """
                schema_version: '1.0'
                id: rx
                title: Rx
                description: A mission type whose step delegates to its base.
                steps:
                - id: a
                  title: A
                  substeps:
                  - id: run
                    description: Run the step test first
                    delegates_to:
                      kind: tactic
                      candidates:
                      - beta::%s
                """.formatted(tactic), "mission_type.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: mission_type:rx
                  target: action:rx/a
                  relation: requires
                - source: action:rx/a
                  target: beta::tactic:%s
                  relation: scope
                """.formatted(
                                tactic));
    }

    private static Map<String, String> preset(String items) {
        return Map.of("presets/team.yaml", """
                schema_version: '1.0'
                id: team
                description: The team's preset.
                """ + items);
    }

    private static Map<String, String> specializationCycle() {
        return Map.of(
                "agent_profiles/implementer-ivan.agent.yaml",
                """
                profile-id: implementer-ivan
                name: Implementer Ivan
                role: implementer
                purpose: Implement the work.
                specialization:
                  primary-focus: Code
                """,
                "agent_profiles/java-jenny.agent.yaml",
                """
                profile-id: java-jenny
                name: Java Jenny
                role: implementer
                purpose: Implement the work in Java.
                specialization:
                  primary-focus: Java
                """,
                "lineage.graph.yaml",
                """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'agent_profile:java-jenny', target: 'agent_profile:implementer-ivan', relation: specializes_from}
                - {source: 'agent_profile:implementer-ivan', target: 'agent_profile:java-jenny', relation: specializes_from}
                """);
    }

    // ---- the two sides ------------------------------------------------------------------------------------------

    private static Path plant(Path directory, Map<String, String> files) {
        try {
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path target = directory.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return directory;
    }

    /** beta, registered once and committed, so a dependent's registration composes the closure it joins. */
    private synchronized void registerBeta() {
        if (betaRegistered) {
            return;
        }
        registration.register(
                "beta",
                new StagedPack(plant(temp.resolve("staged-beta"), beta())),
                Preconditions.none(),
                () -> {},
                Mode.COMMIT);
        betaRegistered = true;
    }

    /** What the registration answers for the planted pack: its refusal, judged in a dry run. */
    private CharterRefusal registrationRefusal(Row row, String name) {
        if (row.dependsOnBeta()) {
            registerBeta();
        }
        StagedPack staged = new StagedPack(plant(temp.resolve("staged-" + name), row.pack()));
        List<CharterRefusal> refused = new ArrayList<>();
        assertThatThrownBy(() -> registration.register(name, staged, Preconditions.none(), () -> {}, Mode.DRY_RUN))
                .as("the registration refuses %s", row)
                .isInstanceOfSatisfying(CharterRefusal.class, refused::add);
        return refused.getFirst();
    }

    /** What one pack-doctor run answered. */
    private record Run(int exit, String out, String err) {
        JsonNode report() {
            return JSON.readTree(out);
        }
    }

    private Run fullPass(Row row, String name) {
        List<String> args = new ArrayList<>(List.of("validate", "--full"));
        if (row.dependsOnBeta()) {
            args.addAll(List.of(
                    "--pack",
                    plant(temp.resolve("doctor").resolve("beta"), beta()).toString()));
        }
        args.addAll(List.of(
                "--pack",
                plant(temp.resolve("doctor").resolve(name), row.pack()).toString()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = PackDoctorMain.run(
                args.toArray(String[]::new),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static List<String> violationCodes(List<RefusalViolation> violations) {
        return violations.stream().map(RefusalViolation::code).toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void theRegistrationAndValidateFullGiveTheSameCode(Row row) {
        String name = row.dependsOnBeta() ? "alpha" : "acme";

        CharterRefusal refused = registrationRefusal(row, name);
        Run run = fullPass(row, name);

        assertThat(refused.code().token())
                .as("the registration's code for %s", row)
                .isEqualTo(row.code());
        assertThat(violationCodes(refused.violations()))
                .as("the registration's violation codes for %s", row)
                .isEqualTo(row.violations());
        assertThat(run.out())
                .as("a report on standard output (stderr: %s)", run.err())
                .isNotEmpty();
        assertThat(contract.schemaViolations(SCHEMA, run.report()))
                .as("the --full report's violations of %s", SCHEMA)
                .isEmpty();
        List<JsonNode> problems = new ArrayList<>();
        run.report().get("problems").forEach(problems::add);
        if (!row.fullReports()) {
            assertThat(problems)
                    .as("--full does not judge %s yet (#636); registration refuses it %s", row, row.code())
                    .isEmpty();
            assertThat(run.exit()).isEqualTo(0);
            return;
        }
        assertThat(problems).as("--full's problems for %s", row).singleElement().satisfies(problem -> {
            assertThat(problem.get("code").asString()).isEqualTo(refused.code().token());
            List<String> codes = new ArrayList<>();
            Optional.ofNullable(problem.get("violations"))
                    .ifPresent(violations ->
                            violations.forEach(v -> codes.add(v.get("code").asString())));
            assertThat(codes).isEqualTo(violationCodes(refused.violations()));
        });
        assertThat(run.exit()).isEqualTo(1);
    }
}
