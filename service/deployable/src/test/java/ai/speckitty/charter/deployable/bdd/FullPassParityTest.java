package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packdoctor.PackDoctorMain;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRegistrationService.Mode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.ManifestStamps;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * cannot pass by both sides being wrong. pack-doctor answers one problem per row, as the registration refuses with one
 * fault, unless the row states pack-doctor's problems: a closure fault the registration halts on before another phase
 * (registration halts at its first failing phase; pack-doctor's {@code CLOSURE} collects every phase, and
 * {@code REFERENCES} runs whenever no rooted closure fails to compose, ruling R-aa, and judges every pack of each
 * root's composed closure, ruling R-ae). Where both name packs and a detail, pack-doctor's are the registration's,
 * which names the closure in front and its roots after the packs.
 *
 * <p>A row may plant its own base pack (a pack the planted pack depends on), registered once and committed as
 * {@code beta} is, so the cross-pack shapes of ruling R-z (a dependency's mention that a dependent holds by case alone)
 * are judged on both sides. The passing rows ({@link #passingRows()}) are shapes both accept.
 *
 * <p>The relation rows (#632 item 3, FR-012, SC-004) plant a {@code specializes_from} cycle, in one pack and across a
 * dependency and its dependent, an override cycle, a self-override and a conflicting relation: {@code --full}'s
 * {@code RELATIONS} judges each over the closure each root composes, with the factory's relation rules, as the
 * registration's relations phase does. The remainder row is the edge-index rule that needs the layer merge, which
 * pack-doctor does not compose: a self-override the merge re-points (#636 slice 7). The registration refuses it
 * {@code self_override}, and {@code --full} does not report it.
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

    private static final String DELTA_ID = "01M4P01D5TWB5FKZGNNQG3943V";

    private static final String EPSILON_ID = "01M4P01D5TWB5FKZGNNQG3943W";

    private static final String ZETA_ID = "01M4P01D5TWB5FKZGNNQG3943Y";

    private static final String ETA_ID = "01M4P01D5TWB5FKZGNNQG3943Z";

    private static final String THETA_ID = "01M4P01D5TWB5FKZGNNQG3943Q";

    private static final String IOTA_ID = "01M4P01D5TWB5FKZGNNQG3943P";

    private static final String FRAGMENT = "nodes: []\nedges: []\n";

    private static final String MANIFEST = "pack-manifest.yaml";

    /** The run's own directory, deleted after it: the config root and the stamped manifest's scratch copy. */
    @TempDir
    static Path shared;

    private static final Set<String> REGISTERED = new HashSet<>();

    private static ContractConformance contract;

    @Autowired
    private PackRegistrationService registration;

    @TempDir
    Path temp;

    @DynamicPropertySource
    static void registrationOn(DynamicPropertyRegistry registry) throws IOException {
        Path configRoot = shared.resolve("config-root");
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
     * A pack the planted pack depends on, registered once and committed, and given to pack-doctor beside it.
     *
     * @param name the name it is registered under, and its directory's
     * @param files its files, by path
     */
    record Base(String name, Map<String, String> files) {}

    /**
     * One planted fault.
     *
     * @param fault what the row plants
     * @param pack the planted pack's files, by path
     * @param base the pack the planted pack depends on; empty when it stands alone
     * @param code the code both answer; for the remainder row, the code the registration alone answers
     * @param violations the violation codes of a {@code validation_failed}, in order; empty for a closure code
     * @param fullReports whether {@code --full} reports it: false only for the remainder #636 slice 7 holds
     * @param doctorCodes the codes of pack-doctor's problems, in order: the registration's code alone, unless
     *     pack-doctor also reports a fault the registration halts before
     */
    record Row(
            String fault,
            Map<String, String> pack,
            Optional<Base> base,
            String code,
            List<String> violations,
            boolean fullReports,
            List<String> doctorCodes) {

        Row(
                String fault,
                Map<String, String> pack,
                Optional<Base> base,
                String code,
                List<String> violations,
                boolean fullReports) {
            this(fault, pack, base, code, violations, fullReports, List.of(code));
        }

        @Override
        public String toString() {
            return fault;
        }
    }

    /**
     * A shape both accept.
     *
     * @param shape what the row plants
     * @param pack the planted pack's files, by path
     * @param base the pack the planted pack depends on; empty when it stands alone
     */
    record Passing(String shape, Map<String, String> pack, Optional<Base> base) {

        @Override
        public String toString() {
            return shape;
        }
    }

    private static final Base BETA = new Base("beta", beta());

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
        return alpha(BETA_ID, added);
    }

    /** alpha, which depends on the base of this pack_id, with one directive, MINE, and the files a row adds. */
    private static Map<String, String> alpha(String baseId, Map<String, String> added) {
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
                """.formatted(baseId));
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

    /**
     * A base pack standing alone at org level, with one directive, D, and the files it adds: what a row's planted alpha
     * depends on when beta will not do.
     */
    private static Map<String, String> base(String id, String name, Map<String, String> added) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.yaml", """
                pack_id: %s
                pack_version: 1.0.0
                parent_pack: null
                name: %s
                pack_level: org
                """.formatted(id, name));
        files.put("drg/fragment.yaml", FRAGMENT);
        files.put("directives/D.directive.yaml", directive("D"));
        files.putAll(added);
        return files;
    }

    /** A pack's files with a policy file that locks the URNs, which its pack.yaml names. */
    private static Map<String, String> locking(Map<String, String> pack, String... urns) {
        Map<String, String> files = new LinkedHashMap<>(pack);
        StringBuilder policy = new StringBuilder("schema_version: 1\nartifacts:\n");
        for (String urn : urns) {
            policy.append("  ").append(urn).append(": locked\n");
        }
        files.put("policy.yaml", policy.toString());
        files.put("pack.yaml", files.get("pack.yaml") + "mutability_policy: policy.yaml\n");
        return files;
    }

    private static Row closure(String fault, Map<String, String> added, String code) {
        return new Row(fault, alpha(added), Optional.of(BETA), code, List.of(), true);
    }

    private static Row refusedAtTheFile(String fault, Map<String, String> added, String violation) {
        return new Row(fault, acme(added), Optional.empty(), "validation_failed", List.of(violation), true);
    }

    private static Map<String, String> with(Map<String, String> first, Map<String, String> second) {
        Map<String, String> files = new LinkedHashMap<>(first);
        files.putAll(second);
        return files;
    }

    static Stream<Row> rows() {
        return Stream.of(
                        referenceRows(),
                        closureRows(),
                        crossPackRows(),
                        relationRows(),
                        policyRows(),
                        fileRows(),
                        antiPatternRows(),
                        remainderRows())
                .flatMap(Stream::of);
    }

    private static Row[] referenceRows() {
        return new Row[] {
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
            closure(
                    "an enhancement target whose pack is missing",
                    stepEnhancementOf("gamma::action:software-dev/plan"),
                    "pack_reference_pack_missing"),
            new Row(
                    "an unresolved substep delegation",
                    alpha(delegationTo("tdd")),
                    Optional.of(BETA),
                    "validation_failed",
                    List.of("substep_delegation_unresolved"),
                    true)
        };
    }

    private static Row[] closureRows() {
        return new Row[] {
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
                    Optional.empty(),
                    "preset_item_not_held",
                    List.of("preset_item_not_held"),
                    true),
            new Row(
                    "a preset item no pack of the own closure declares",
                    acme(preset("""
                                activated_tactics:
                                  - id: ghost
                                """)),
                    Optional.empty(),
                    "preset_item_undeclared",
                    List.of("preset_item_undeclared"),
                    true),
            // The existing case-only answer stands for a preset item, with no violation of its own (US1 scenario 3).
            new Row(
                    "a preset item that names a held artifact only by case",
                    acme(preset("""
                                activated_directives:
                                  - id: a
                                """)),
                    Optional.empty(),
                    "pack_reference_artifact_missing",
                    List.of(),
                    true),
            // CLOSURE's policy-lock phase (WP13, ruling R-y): registration's phase 6 answer, no violation of its own.
            new Row(
                    "a policy lock that names a held artifact only by case",
                    policyLocking("directive:a"),
                    Optional.empty(),
                    "pack_reference_artifact_missing",
                    List.of(),
                    true),
            // Several locks of one pack, each its own fault: the registration lists every one in violations.
            new Row(
                    "several policy locks that name held artifacts only by case",
                    locking(acme(Map.of("directives/B.directive.yaml", directive("B"))), "directive:b", "directive:a"),
                    Optional.empty(),
                    "pack_reference_artifact_missing",
                    List.of("pack_reference_artifact_missing", "pack_reference_artifact_missing"),
                    true,
                    List.of("pack_reference_artifact_missing", "pack_reference_artifact_missing"))
        };
    }

    /** delta, a base whose policy locks {@code directive:mine}, which its dependent alpha holds as MINE (R-z). */
    private static Base delta() {
        return new Base("delta", locking(base(DELTA_ID, "delta", Map.of()), "directive:mine"));
    }

    /** epsilon, a base whose preset activates {@code mine}, which its dependent alpha holds as MINE (R-z). */
    private static Base epsilon() {
        return new Base(
                "epsilon", base(EPSILON_ID, "epsilon", Map.of("presets/team.yaml", """
                                schema_version: '1.0'
                                id: team
                                description: The team's preset.
                                activated_directives:
                                  - id: mine
                                """, "drg/fragment.yaml", """
                                nodes:
                                - {id: mine, kind: directives, title: Mine}
                                edges: []
                                """)));
    }

    /**
     * zeta, a base whose relations name {@code directive:MINE} bare, which only its dependent alpha holds (ruling R-ae,
     * Tier R B2). REFERENCES is judged per root over the composed closure, every pack in it, as the registration's
     * phases 2 to 4 judge it: the bare reference is unqualified when alpha is registered, and in pack-doctor from
     * alpha's root. A qualified reference into a pack outside the referrer's {@code depends_on} but inside a root's
     * closure is answered once per root, each in that root's words, as registering each root answers it; the harness
     * has one base, so {@code ReferencesPhaseTest} pins that shape.
     */
    private static Base zeta() {
        return new Base("zeta", base(ZETA_ID, "zeta", Map.of("relations.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'directive:D', target: 'directive:MINE', relation: requires}
                """)));
    }

    private static Row[] crossPackRows() {
        return new Row[] {
            // Ruling R-ae (Tier R B2): REFERENCES per root over the composed closure, every pack in it (see zeta()).
            new Row(
                    "a dependency's bare reference that only the dependent holds",
                    alpha(ZETA_ID, Map.of()),
                    Optional.of(zeta()),
                    "pack_reference_unqualified",
                    List.of(),
                    true),
            // Ruling R-z: the case-only answer is judged per root over the composed closure, every member's mentions
            // against every member's holdings, as the registration's phase 6 judges it. A dependency's mention that
            // its dependent holds by case alone is refused when the dependent is registered, and by pack-doctor.
            new Row(
                    "a dependency's policy lock that the dependent holds only by case",
                    alpha(DELTA_ID, Map.of()),
                    Optional.of(delta()),
                    "pack_reference_artifact_missing",
                    List.of(),
                    true),
            new Row(
                    "a dependency's preset item that the dependent holds only by case",
                    alpha(EPSILON_ID, Map.of()),
                    Optional.of(epsilon()),
                    "pack_reference_artifact_missing",
                    List.of(),
                    true),
            // Ruling R-aa: REFERENCES is gated only on composition faults. The registration halts at its references
            // phase; pack-doctor's CLOSURE reports the later phase's fault, and REFERENCES the reference fault.
            new Row(
                    "a missing referenced artifact beside a must_have preset item no pack holds",
                    alpha(with(relates("beta::directive:NOPE"), preset("""
                                activated_directives:
                                  - id: NOPE
                                    must_have: true
                                """))),
                    Optional.of(BETA),
                    "pack_reference_artifact_missing",
                    List.of(),
                    true,
                    List.of("preset_item_not_held", "pack_reference_artifact_missing")),
            new Row(
                    "a missing referenced artifact beside a case-only policy lock",
                    locking(alpha(relates("beta::directive:NOPE")), "directive:mine"),
                    Optional.of(BETA),
                    "pack_reference_artifact_missing",
                    List.of(),
                    true,
                    List.of("pack_reference_artifact_missing", "pack_reference_artifact_missing")),
            // A rooted closure that reaches a pack outside the set is a composition fault, and only that: the case-only
            // answer and REFERENCES are not judged over a closure that does not compose.
            new Row(
                    "a dependency no pack declares, beside a case-only policy lock",
                    locking(alpha("01M4P01D5TWB5FKZGNNQG3943X", Map.of()), "directive:mine"),
                    Optional.empty(),
                    "pack_dependency_unknown",
                    List.of(),
                    true)
        };
    }

    static Stream<Passing> passingRows() {
        return Stream.of(new Passing(
                "a must_have preset item held only by a depends_on pack", alpha(preset("""
                            activated_directives:
                              - id: SHARE
                                must_have: true
                            """)), Optional.of(BETA)));
    }

    private static Row[] fileRows() {
        return new Row[] {
            // The manifest gate (WP04): a constituent edited after its content_hash was stamped.
            refusedAtTheFile("a stale manifest content_hash", staleManifest(), "manifest_stale"),
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
            refusedAtTheFile("a file-less anti_pattern node", Map.of("styleguide.graph.yaml", """
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
                                """), "drg_schema_error")
        };
    }

    private static Row[] antiPatternRows() {
        return new Row[] {
            // A9: the node's file is there, refused for its own fault, so that fault is the one cause.
            refusedAtTheFile(
                    "an anti-pattern file whose id is not its stem, beside its node",
                    Map.of("anti_patterns/x.anti-pattern.yaml", """
                                schema_version: '1.0'
                                id: other
                                title: X
                                description: The anti-pattern the graph node declares.
                                severity: smell
                                bad_example: the bad example
                                """, "styleguide.graph.yaml", """
                                schema_version: '1.0'
                                generated_at: STATIC
                                nodes:
                                - urn: anti_pattern:x
                                  kind: anti_pattern
                                  label: X
                                edges: []
                                """),
                    "artifact_schema_error")
        };
    }

    /**
     * eta, a base holding two agent profiles, java-jenny specializing from implementer-ivan: a lineage that ends. Its
     * dependent alpha closes the cycle with the edge back, so only alpha's closure holds the cycle.
     */
    private static Base eta() {
        Map<String, String> added = new LinkedHashMap<>(profiles());
        added.put(
                "lineage.graph.yaml",
                lineage("{source: 'agent_profile:java-jenny', target:"
                        + " 'agent_profile:implementer-ivan', relation: specializes_from}"));
        return new Base("eta", base(ETA_ID, "eta", added));
    }

    private static Row relation(String fault, String code, String... edges) {
        Map<String, String> added = new LinkedHashMap<>();
        added.put("directives/B.directive.yaml", directive("B"));
        added.put("relations.graph.yaml", lineage(edges));
        return new Row(fault, acme(added), Optional.empty(), code, List.of(), true);
    }

    private static Row[] relationRows() {
        return new Row[] {
            // RELATIONS (#632 item 3, FR-012): the factory's relation rules over each root's composed closure.
            new Row(
                    "a specializes_from cycle",
                    acme(specializationCycle()),
                    Optional.empty(),
                    "specialization_cycle",
                    List.of(),
                    true),
            new Row(
                    "a specializes_from cycle a dependent closes across its dependency",
                    alpha(
                            ETA_ID,
                            Map.of(
                                    "lineage.graph.yaml",
                                    lineage("{source: 'eta::agent_profile:implementer-ivan',"
                                            + " target: 'eta::agent_profile:java-jenny',"
                                            + " relation: specializes_from}"))),
                    Optional.of(eta()),
                    "specialization_cycle",
                    List.of(),
                    true),
            relation(
                    "an override cycle",
                    "override_cycle",
                    "{source: 'directive:A', target: 'directive:B', relation: overrides}",
                    "{source: 'directive:B', target: 'directive:A', relation: overrides}"),
            closure(
                    "a self-override of a dependency's artifact",
                    Map.of(
                            "relations.graph.yaml",
                            lineage("{source: 'beta::directive:SHARE', target: 'beta::directive:SHARE',"
                                    + " relation: overrides}")),
                    "self_override"),
            relation(
                    "an artifact that both enhances and overrides one target",
                    "conflicting_relations",
                    "{source: 'directive:A', target: 'directive:B', relation: enhances}",
                    "{source: 'directive:A', target: 'directive:B', relation: overrides}")
        };
    }

    private static Row[] policyRows() {
        return new Row[] {
            // RELATIONS, the rules that read the mutability policy and the mission type's lock: pack-doctor reads them
            // from the files, registration from the stored packs (review F1).
            closureOf(
                    "an override of a dependency's artifact that is not open",
                    THETA_ID,
                    theta(),
                    lineage("{source: 'directive:MINE', target: 'theta::directive:D', relation: overrides}"),
                    "override_not_permitted"),
            new Row(
                    "an enhancement of a step whose mission type its dependency locks",
                    alpha(IOTA_ID, stepEnhancementOf("iota::action:software-dev/plan")),
                    Optional.of(iota()),
                    "override_not_permitted",
                    List.of(),
                    true),
            relation(
                    "an enhances cycle",
                    "enhances_cycle",
                    "{source: 'directive:A', target: 'directive:B', relation: enhances}",
                    "{source: 'directive:B', target: 'directive:A', relation: enhances}"),
            relation(
                    "an enhancer whose chain reaches no artifact that enhances nothing",
                    "enhances_cycle",
                    "{source: 'directive:A', target: 'directive:MISSING_ONE', relation: enhances}")
        };
    }

    /** alpha depending on a base, with the relations file given. */
    private static Row closureOf(String fault, String baseId, Base base, String relations, String code) {
        return new Row(
                fault,
                alpha(baseId, Map.of("relations.graph.yaml", relations)),
                Optional.of(base),
                code,
                List.of(),
                true);
    }

    /** theta, a base whose policy locks its directive D. */
    private static Base theta() {
        return new Base("theta", locking(base(THETA_ID, "theta", Map.of()), "directive:D"));
    }

    /** iota, beta's files under its own id and name, whose policy locks the mission type software-dev. */
    private static Base iota() {
        Map<String, String> files = new LinkedHashMap<>(beta());
        files.put("pack.yaml", files.get("pack.yaml").replace(BETA_ID, IOTA_ID).replace("name: beta", "name: iota"));
        return new Base("iota", locking(files, "mission_type:software-dev"));
    }

    /**
     * alpha at the project level, holding beta's SHARE as its own, with an overrides edge onto beta's SHARE: the layer
     * merge re-points the edge to its own source, which only the merge shows.
     */
    private static Map<String, String> repointedSelfOverride() {
        Map<String, String> files = alpha(Map.of(
                "directives/SHARE.directive.yaml",
                directive("SHARE"),
                "relations.graph.yaml",
                lineage("{source: 'directive:SHARE', target: 'beta::directive:SHARE', relation: overrides}")));
        files.put("pack.yaml", files.get("pack.yaml").replace("pack_level: org", "pack_level: project"));
        return files;
    }

    private static Row[] remainderRows() {
        return new Row[] {
            // The remainder: the edge-index rules judge the layer merge, which pack-doctor does not compose (#636 slice
            // 7). The registration refuses this self-override; --full does not report it. When pack-doctor composes
            // the merge, this row becomes a parity row (fullReports true).
            new Row(
                    "KNOWN GAP #636 slice 7, a pin and no coverage: a self-override the layer merge re-points, refused"
                            + " by registration, not reported by --full",
                    repointedSelfOverride(),
                    Optional.of(BETA),
                    "self_override",
                    List.of(),
                    false)
        };
    }

    /**
     * acme with a manifest stamped from its files, then its directive A edited, so A's {@code content_hash} is stale.
     */
    private static Map<String, String> staleManifest() {
        Map<String, String> files = acme(Map.of());
        try {
            Path stamped = plant(Files.createDirectories(shared.resolve("stamped-manifest")), files);
            ManifestStamps.stamp(stamped, "acme");
            files.put(MANIFEST, Files.readString(stamped.resolve(MANIFEST), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        files.put("directives/A.directive.yaml", directive("A") + "# edited\n");
        return files;
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

    /** acme naming its policy file, which locks one URN. */
    private static Map<String, String> policyLocking(String urn) {
        return locking(acme(Map.of()), urn);
    }

    /** Two agent profiles, implementer-ivan and java-jenny, by path. */
    private static Map<String, String> profiles() {
        return Map.of("agent_profiles/implementer-ivan.agent.yaml", """
                profile-id: implementer-ivan
                name: Implementer Ivan
                role: implementer
                purpose: Implement the work.
                specialization:
                  primary-focus: Code
                """, "agent_profiles/java-jenny.agent.yaml", """
                profile-id: java-jenny
                name: Java Jenny
                role: implementer
                purpose: Implement the work in Java.
                specialization:
                  primary-focus: Java
                """);
    }

    /** A graph file of the edges given, each a flow mapping. */
    private static String lineage(String... edges) {
        StringBuilder graph = new StringBuilder("schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n");
        for (String edge : edges) {
            graph.append("- ").append(edge).append('\n');
        }
        return graph.toString();
    }

    private static Map<String, String> specializationCycle() {
        Map<String, String> files = new LinkedHashMap<>(profiles());
        files.put(
                "lineage.graph.yaml",
                lineage(
                        "{source: 'agent_profile:java-jenny', target: 'agent_profile:implementer-ivan',"
                                + " relation: specializes_from}",
                        "{source: 'agent_profile:implementer-ivan', target: 'agent_profile:java-jenny',"
                                + " relation: specializes_from}"));
        return files;
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

    /** A base, registered once and committed, so a dependent's registration composes the closure it joins. */
    private void register(Optional<Base> base) {
        synchronized (REGISTERED) {
            if (base.isEmpty() || REGISTERED.contains(base.get().name())) {
                return;
            }
            registration.register(
                    base.get().name(),
                    new StagedPack(plant(
                            temp.resolve("staged-" + base.get().name()),
                            base.get().files())),
                    Preconditions.none(),
                    () -> {},
                    Mode.COMMIT);
            REGISTERED.add(base.get().name());
        }
    }

    /** What the registration answers for the planted pack: its refusal, judged in a dry run. */
    private CharterRefusal registrationRefusal(Row row, String name) {
        register(row.base());
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

    private Run fullPass(Optional<Base> base, Map<String, String> pack, String name) {
        List<String> args = new ArrayList<>(List.of("validate", "--full"));
        base.ifPresent(present -> args.addAll(List.of(
                "--pack",
                plant(temp.resolve("doctor").resolve(present.name()), present.files())
                        .toString())));
        args.addAll(List.of(
                "--pack", plant(temp.resolve("doctor").resolve(name), pack).toString()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = PackDoctorMain.run(
                args.toArray(String[]::new),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** One field of each of a pack-doctor problem's violations, in order; none when it has no violations. */
    private static List<String> fieldOfViolations(JsonNode problem, String field) {
        List<String> values = new ArrayList<>();
        Optional.ofNullable(problem.get("violations"))
                .ifPresent(violations ->
                        violations.forEach(v -> values.add(v.get(field).asString())));
        return values;
    }

    private static List<String> violationCodes(List<RefusalViolation> violations) {
        return violations.stream().map(RefusalViolation::code).toList();
    }

    /** The fault's packs, as the contract writes each: its descriptorPackId, packId and name. */
    private static List<List<String>> packsOf(List<RefusalPack> packs) {
        return packs.stream()
                .map(pack ->
                        List.of(pack.descriptorPackId().value(), pack.packId().value(), pack.name()))
                .toList();
    }

    private static List<List<String>> packsOf(JsonNode problem) {
        List<List<String>> packs = new ArrayList<>();
        Optional.ofNullable(problem.get("packs"))
                .ifPresent(named -> named.forEach(pack -> packs.add(List.of(
                        pack.get("descriptorPackId").asString(),
                        pack.get("packId").asString(),
                        pack.get("name").asString()))));
        return packs;
    }

    /**
     * The registration's words for a composing fault pack-doctor words as {@code detail}: the closure it judged in front
     * ({@code In the closure rooted at <root>: }) and then the same text. A preset item's fault is judged in the pack's
     * own closure, not a composed one, so the registration words it as pack-doctor does, with nothing in front.
     */
    private static String registrationDetail(String code, String root, String detail) {
        return code.startsWith("preset_item_") ? detail : "In the closure rooted at " + root + ": " + detail;
    }

    /**
     * The packs the registration names for a fault, as pack-doctor names them: exactly the same, or the same followed by
     * the root pack the registration appends when they do not name it ({@code ClosureImpactCheck.withRoots}).
     */
    private static void assertSamePacks(List<List<String>> registered, JsonNode problem, String root) {
        List<List<String>> named = packsOf(problem);
        assertThat(named).as("pack-doctor names at least one pack").isNotEmpty();
        boolean rootAppended = registered.size() == named.size() + 1
                && registered.getLast().get(1).equals(root)
                && named.stream().noneMatch(pack -> pack.get(1).equals(root));
        assertThat(registered)
                .as("the registration's packs: pack-doctor's, plus the root %s when pack-doctor does not name it", root)
                .isEqualTo(rootAppended ? concat(named, registered.getLast()) : named);
    }

    private static List<List<String>> concat(List<List<String>> packs, List<String> last) {
        List<List<String>> all = new ArrayList<>(packs);
        all.add(last);
        return all;
    }

    /**
     * Every fault of the registration is one violation of its refusal, and one problem of pack-doctor's, in the same
     * order, with the registration's words in front and no pointer on either side.
     */
    private static void assertEveryFaultIsAProblem(
            CharterRefusal refused, List<JsonNode> problems, String name, Row row) {
        assertThat(refused.violations()).hasSameSizeAs(problems);
        for (int fault = 0; fault < problems.size(); fault++) {
            RefusalViolation violation = refused.violations().get(fault);
            JsonNode problem = problems.get(fault);
            assertThat(violation.code()).isEqualTo(problem.get("code").asString());
            assertThat(violation.detail())
                    .as("the registration's detail of fault %d for %s", fault, row)
                    .isEqualTo(registrationDetail(
                            violation.code(), name, problem.get("detail").asString()));
            assertThat(fieldOfViolations(problem, "pointer"))
                    .as("pack-doctor's pointers of fault %d", fault)
                    .isEqualTo(violation.pointer().isEmpty() ? List.of() : List.of(violation.pointer()));
        }
    }

    /**
     * A closure fault: exactly one problem of pack-doctor's is the registration's fault (its code, and its words but
     * for the closure in front, exactly), and it names the registration's packs (A5). pack-doctor also reports the
     * faults the registration halts before, so the others are not paired.
     */
    private static void assertTheClosureFaultIsAProblem(CharterRefusal refused, List<JsonNode> problems, String name) {
        assertThat(problems)
                .as("pack-doctor's problem for the registration's fault (%s)", refused.getMessage())
                .filteredOn(problem ->
                        problem.get("code").asString().equals(refused.code().token())
                                && refused.getMessage()
                                        .equals(registrationDetail(
                                                refused.code().token(),
                                                name,
                                                problem.get("detail").asString())))
                .singleElement()
                .satisfies(problem -> {
                    assertSamePacks(packsOf(refused.packs()), problem, name);
                    assertThat(fieldOfViolations(problem, "pointer")).isEmpty();
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void theRegistrationAndValidateFullGiveTheSameCode(Row row) {
        String name = row.base().isPresent() ? "alpha" : "acme";

        CharterRefusal refused = registrationRefusal(row, name);
        Run run = fullPass(row.base(), row.pack(), name);

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
            // The known gap (#636 slice 7), pinned: --full reports nothing for this row yet. This is no parity
            // coverage; the row becomes a parity row when pack-doctor composes the layer merge, and this branch goes.
            assertThat(problems)
                    .as("--full does not judge %s yet (#636 slice 7); registration refuses it %s", row, row.code())
                    .isEmpty();
            assertThat(run.exit()).isEqualTo(0);
            return;
        }
        assertThat(problems)
                .as("--full's problems for %s", row)
                .extracting(problem -> problem.get("code").asString())
                .isEqualTo(row.doctorCodes());
        boolean severalFaults = refused.getMessage().endsWith("fault(s); every one is in violations)");
        if (row.doctorCodes().size() == 1) {
            JsonNode problem = problems.getFirst();
            assertThat(fieldOfViolations(problem, "code")).isEqualTo(violationCodes(refused.violations()));
            // The pointer is a URI reference into the pack file (main a18fb90f4): the same on both surfaces.
            assertThat(fieldOfViolations(problem, "pointer"))
                    .as("--full's violation pointers for %s", row)
                    .isEqualTo(refused.violations().stream()
                            .map(RefusalViolation::pointer)
                            .toList());
        }
        if (severalFaults) {
            assertEveryFaultIsAProblem(refused, problems, name, row);
        } else if (refused.violations().isEmpty()) {
            assertTheClosureFaultIsAProblem(refused, problems, name);
        }
        assertThat(run.exit()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("passingRows")
    void theRegistrationAndValidateFullBothAccept(Passing row) {
        String name = row.base().isPresent() ? "alpha" : "acme";
        register(row.base());
        StagedPack staged = new StagedPack(plant(temp.resolve("staged-" + name), row.pack()));

        registration.register(name, staged, Preconditions.none(), () -> {}, Mode.DRY_RUN);
        Run run = fullPass(row.base(), row.pack(), name);

        assertThat(contract.schemaViolations(SCHEMA, run.report()))
                .as("the --full report's violations of %s", SCHEMA)
                .isEmpty();
        assertThat(run.report().get("problems"))
                .as("--full's problems for %s", row)
                .isEmpty();
        assertThat(run.exit()).as("stderr: %s", run.err()).isEqualTo(0);
    }
}
