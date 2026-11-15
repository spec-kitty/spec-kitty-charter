package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The substep delegation rules of WP07 (FR-005, operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9), each proven by a planted
 * pack that holds exactly one fault, refused alike by validate mode, the store's load and the registration's staged
 * load, beside a control that differs only in that fault:
 *
 * <ul>
 *   <li>{@code planted/r35-substep-delegation-unresolved}: the substep {@code run} of the step {@code a} delegates to
 *       the tactic {@code tdd}, which the pack does not hold;
 *   <li>{@code planted/r36-substep-delegation-unscoped}: the pack holds the tactic, and no graph document holds
 *       {@code action:rx/a scope tactic:tdd}.
 * </ul>
 */
@DisplayName("the substep delegation rules: every candidate resolves and is scoped from its step's action (FR-005)")
class SubstepDelegationPlantsTest {

    private static final String TYPE = "missions/rx/rx.mission-type.yaml";
    private static final String GRAPH = "mission_type.graph.yaml";
    private static final String TACTIC = "tactics/tdd.tactic.yaml";
    private static final String UNRESOLVED = "r35-substep-delegation-unresolved";
    private static final String UNSCOPED = "r36-substep-delegation-unscoped";
    private static final String SCOPE_EDGE = "- source: action:rx/a\n  target: tactic:tdd\n  relation: scope\n";
    private static final List<String> PLACE = List.of("steps", "0", "substeps", "0", "delegates_to", "candidates", "0");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "r35-substep-delegation-unresolved, SUBSTEP_DELEGATION_UNRESOLVED, which the pack does not hold as a tactic",
        "r36-substep-delegation-unscoped, SUBSTEP_DELEGATION_UNSCOPED, no graph document holds action:rx/a scope tactic:tdd"
    })
    @DisplayName("validate mode: one blocking finding at the candidate's place in the mission type file")
    void validateModeRefusesTheCandidate(String plant, PackFindingCode code, String reason, @TempDir Path host) {
        List<PackFinding> findings = validate(planted(host, plant));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(code);
            assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
            assertThat(finding.file()).isEqualTo(TYPE);
            assertThat(finding.place()).isEqualTo(PLACE);
            assertThat(finding.detail())
                    .contains("the substep run of the step a of mission_type:rx delegates to tactic tdd")
                    .contains(reason);
        });
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "r35-substep-delegation-unresolved, substep_delegation_unresolved",
        "r36-substep-delegation-unscoped, substep_delegation_unscoped"
    })
    @DisplayName("the store's load and registration: one violation of the code at the mission type file")
    void theLoadAndRegistrationRefuseTheCandidate(String plant, String code, @TempDir Path host) {
        Path pack = planted(host, plant);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(store.load(pack).pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(TYPE);
            assertThat(violation.kind()).isEqualTo(code);
            assertThat(violation.reason()).contains("tactic tdd");
        });
        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEqualTo(TYPE);
                        assertThat(violation.code()).isEqualTo(code);
                    });
                });
    }

    @Test
    @DisplayName("the codes are the rule's: one spelling at load, at registration and in validate mode")
    void theCodesAreTheRules() {
        assertThat(SubstepDelegationRule.UNRESOLVED).isEqualTo("substep_delegation_unresolved");
        assertThat(SubstepDelegationRule.UNSCOPED).isEqualTo("substep_delegation_unscoped");
    }

    @Test
    @DisplayName("control: with the tactic and its scope edge the unresolved pack validates, loads and registers")
    void theResolvedScopedCandidateIsFine(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNRESOLVED);
        Files.createDirectories(pack.resolve("tactics"));
        Files.copy(planted(host.resolve("other"), UNSCOPED).resolve(TACTIC), pack.resolve(TACTIC));
        appendToGraph(pack, SCOPE_EDGE);

        assertClean(pack);
    }

    @Test
    @DisplayName("control: with its scope edge the unscoped pack validates, loads and registers")
    void theScopedCandidateIsFine(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNSCOPED);
        appendToGraph(pack, SCOPE_EDGE);

        assertClean(pack);
    }

    @Test
    @DisplayName("a candidate and an edge written with the pack's own name are the pack's own")
    void thePacksOwnNameIsTheOwnPack(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNSCOPED);
        rewriteType(pack, "      - tdd\n", "      - qa::tdd\n");
        appendToGraph(pack, "- source: qa::action:rx/a\n  target: qa::tactic:tdd\n  relation: scope\n");

        assertClean(pack);
    }

    @Test
    @DisplayName(
            "a candidate of another pack is resolved at compose; its scope edge is the qualified edge, judged here")
    void aCandidateOfAnotherPackNeedsItsQualifiedEdge(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNSCOPED);
        rewriteType(pack, "      - tdd\n", "      - base::tdd\n");
        appendToGraph(pack, SCOPE_EDGE);

        assertThat(validate(pack)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.SUBSTEP_DELEGATION_UNSCOPED);
            assertThat(finding.detail()).contains("action:rx/a scope base::tactic:tdd");
        });
        assertThat(new WholePackStore(new YamlSerializationStrategy())
                        .load(pack)
                        .pack()
                        .violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.kind()).isEqualTo(SubstepDelegationRule.UNSCOPED));
    }

    @Test
    @DisplayName(
            "a substep id repeated within a step: duplicate_step_id at the type's file, in validate mode and at load")
    void aRepeatedSubstepIdIsTheTypesOneFault(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNRESOLVED);
        rewriteType(pack, "      - tdd\n", "      - tdd\n  - id: run\n    description: Run it again\n");

        assertThat(validate(pack)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.DUPLICATE_STEP_ID);
            assertThat(finding.file()).isEqualTo(TYPE);
            assertThat(finding.place()).containsExactly("steps", "0", "substeps", "1", "id");
            assertThat(finding.detail()).contains("substep id 'run' is declared more than once in the step 'a'");
        });
        assertThat(new WholePackStore(new YamlSerializationStrategy())
                        .load(pack)
                        .pack()
                        .violations())
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.kind()).isEqualTo("duplicate_step_id");
                    assertThat(violation.file()).isEqualTo(TYPE);
                    assertThat(violation.reason())
                            .contains("substep id 'run' is declared more than once in the step 'a'");
                });
    }

    @Test
    @DisplayName("a kind that is no scope target of an action is refused by the schema, by name")
    void aKindThatIsNoScopeTargetIsRefusedByTheSchema(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNRESOLVED);
        rewriteType(pack, "      kind: tactic\n", "      kind: template\n");

        assertThat(validate(pack)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.file()).isEqualTo(TYPE);
            assertThat(finding.place()).containsExactly("steps", "0", "substeps", "0", "delegates_to", "kind");
        });
    }

    @Test
    @DisplayName("a substep that authors the step contract's retired gate is refused as an unknown field, by its name")
    void aSubstepFieldTheSchemaDoesNotDeclareIsRefusedAsUnknown(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNRESOLVED);
        rewriteType(pack, "    delegates_to:\n", "    gate: review\n    delegates_to:\n");
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(validate(pack)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.file()).isEqualTo(TYPE);
            assertThat(finding.detail()).contains("unknown key 'gate'");
        });
        assertThat(store.load(pack).pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(TYPE);
            assertThat(violation.reason())
                    .contains("additionalProperties violated at")
                    .contains("undeclared key 'gate'");
        });
    }

    @Test
    @DisplayName(
            "a substep never delegates to an agent profile: refused in validate mode and at load (ADR-0032 decision 1)")
    void anAgentProfileDelegationIsRefused(@TempDir Path host) throws IOException {
        // Operator ruling DM-01M7DTN8JWR4G1P35NXGCMS65M: a mission type names roles, never profiles, though the scope
        // row lets an action reach an agent_profile.
        Path pack = planted(host, UNRESOLVED);
        rewriteType(pack, "      kind: tactic\n", "      kind: agent_profile\n");
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(validate(pack)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.file()).isEqualTo(TYPE);
            assertThat(finding.detail()).contains("agent_profile");
        });
        assertThat(store.load(pack).pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(TYPE);
            assertThat(violation.reason()).contains("enum violated at /steps/0/substeps/0/delegates_to/kind");
        });
        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(
                                    violation -> assertThat(violation.pointer()).isEqualTo(TYPE));
                });
    }

    private static void rewriteType(Path pack, String from, String to) throws IOException {
        Path type = pack.resolve(TYPE);
        String text = Files.readString(type, StandardCharsets.UTF_8);
        assertThat(text).contains(from);
        Files.writeString(type, text.replace(from, to), StandardCharsets.UTF_8);
    }

    private static void appendToGraph(Path pack, String edge) throws IOException {
        Path graph = pack.resolve(GRAPH);
        Files.writeString(graph, Files.readString(graph, StandardCharsets.UTF_8) + edge, StandardCharsets.UTF_8);
    }

    private static void assertClean(Path pack) {
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(validate(pack)).isEmpty();
        assertThat(store.load(pack).pack().violations()).isEmpty();
        assertThat(store.loadStaged(new StagedPack(pack)).pack().violations()).isEmpty();
    }

    private static List<PackFinding> validate(Path pack) {
        return new PackValidation(new FileSystemPackInspector())
                .validate(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()))
                .findings();
    }

    /** A copy of the planted pack under {@code host}, so a test may change it. */
    private static Path planted(Path host, String plant) {
        String resource = "/planted/" + plant + "/files";
        try {
            Path source =
                    Path.of(Objects.requireNonNull(SubstepDelegationPlantsTest.class.getResource(resource), resource)
                            .toURI());
            Path target = host.resolve("qa");
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.sorted().toList()) {
                    Path copy = target.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(file, copy);
                    }
                }
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
