package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.rules.GraphBinding;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The endpoint table applied to a pack's graph documents (ADR-0064 decision 13, #350): validate mode and the load
 * refuse an edge whose relation does not allow its kinds. Every refused edge has a twin that passes and differs only in
 * one endpoint.
 */
class RelationshipEndpointRuleTest {

    private static final String FILE = "edges.graph.yaml";

    private static RelationshipEndpointRule.Edge edge(String source, String relation, String target) {
        return new RelationshipEndpointRule.Edge(FILE, source, target, relation);
    }

    /** The reason the rule refuses an edge for, which must then be the table's code, or empty when it passes. */
    private static Optional<String> tableRefusal(RelationshipEndpointRule.Edge edge) {
        Optional<RelationshipEndpointRule.Refusal> refusal = RelationshipEndpointRule.refusal(edge, MAIN);
        refusal.ifPresent(refused -> assertThat(refused.code()).isEqualTo(RelationshipEndpointRule.CODE));
        return refusal.map(RelationshipEndpointRule.Refusal::reason);
    }

    @ParameterizedTest(name = "{0} {1} {2} is refused; {3} {1} {4} passes")
    @CsvSource({
        "procedure:p, requires, action:software-dev/implement, procedure:p, directive:D",
        "directive:d, suggests, mission_type:software-dev, directive:d, agent_profile:a",
        "tactic:t, scope, directive:D, action:software-dev/plan, directive:D",
        "directive:d, instantiates, template:t.md, action:software-dev/plan, template:t.md",
        "skill:s, refines, directive:D, tactic:t, directive:D",
        "directive:d, specializes_from, directive:E, agent_profile:a, agent_profile:b",
        "tactic:t, enhances, action:software-dev/plan, tactic:t, tactic:u",
        "directive:d, rejects, directive:E, directive:d, anti_pattern:x",
        "tactic:t, vocabulary, directive:D, tactic:t, glossary_context:g",
        "action:software-dev/plan, applies, styleguide:s, action:software-dev/plan, tactic:t",
        "directive:d, overrides, tactic:t, directive:d, directive:E",
        "directive:d, in_tension_with, skill:s, directive:d, tactic:t"
    })
    @DisplayName("validate mode refuses one edge of each relation family with one finding, beside a twin that passes")
    void eachRelationFamilyRefusesKindsItsRowsDoNotHold(
            String source, String relation, String target, String twinSource, String twinTarget) {
        List<PackFinding> findings = PackPolicy.judge(Readouts.of(validated(source, relation, target)))
                .findings();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(finding.file()).isEqualTo(FILE);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail()).startsWith(FILE + ": ").contains(relation);
        });
        assertThat(PackPolicy.judge(Readouts.of(validated(twinSource, relation, twinTarget)))
                        .findings())
                .isEmpty();
    }

    /** The pack "main", which holds the step enhancement e. */
    private static final RelationshipEndpointRule.Scope MAIN = new RelationshipEndpointRule.Scope("main", Set.of("e"));

    @Test
    @DisplayName("a fault another rule owns is not reported again, nor is an edge the graph rules refuse")
    void theFaultsOfOtherRulesAreLeftToThem() {
        assertThat(RelationshipEndpointRule.refusal(edge("template:t.md", "overrides", "directive:D"), MAIN))
                .as("the bridge's source rule comes before the table: slice C's overrides_source_not_activatable")
                .hasValueSatisfying(
                        refusal -> assertThat(refusal.code()).isEqualTo(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE));
        assertThat(tableRefusal(edge("step_enhancement:e", "overrides", "mission_type:m")))
                .as("the step-enhancement rules judge the held enhancement (prompt_fragment_invalid)")
                .isEmpty();
        assertThat(tableRefusal(edge("main::step_enhancement:e", "enhances", "directive:D")))
                .as("the held enhancement written with the pack's own name")
                .isEmpty();
        assertThat(tableRefusal(edge("action:m/s", "instantiates", "action:m/s")))
                .as("a bare self-loop is a graph fault, even under a relation that refuses its kinds")
                .isEmpty();
        assertThat(tableRefusal(edge("directive:D", "extends", "action:a/b")))
                .as("an unknown relation is a graph fault")
                .isEmpty();
        assertThat(tableRefusal(edge("not a urn", "requires", "action:a/b")))
                .as("an endpoint that is no URN is a graph fault")
                .isEmpty();
    }

    @Test
    @DisplayName("an edge from a step enhancement the step-enhancement rules do not judge is the table's to refuse")
    void anEnhancementEdgeNoOtherRuleJudgesIsRefusedByTheTable() {
        assertThat(tableRefusal(edge("step_enhancement:ghost", "enhances", "directive:D")))
                .as("an enhancement the pack does not hold")
                .contains("an enhances edge may not connect step_enhancement to directive");
        assertThat(tableRefusal(edge("other::step_enhancement:e", "overrides", "mission_type:m")))
                .as("another pack's enhancement")
                .contains("an overrides edge may not connect step_enhancement to mission_type");
        assertThat(tableRefusal(edge("step_enhancement:e", "requires", "directive:D")))
                .as("a relation the step-enhancement rules do not judge")
                .contains("a requires edge may not connect step_enhancement to directive");
    }

    @Test
    @DisplayName("validate mode reports a bare self-loop once, as the graph fault it is")
    void validateModeReportsABareSelfLoopOnce() {
        List<PackFinding> findings = PackPolicy.judge(
                        Readouts.of(validated("action:m/s", "instantiates", "action:m/s")))
                .findings();

        assertThat(findings)
                .singleElement()
                .satisfies(finding -> assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR));
    }

    @Test
    @DisplayName("validate mode refuses an edge from an enhancement the pack does not hold")
    void validateModeRefusesAnUnheldEnhancementEdge() {
        assertThat(PackPolicy.judge(Readouts.of(validated("step_enhancement:ghost", "enhances", "directive:D")))
                        .findings())
                .singleElement()
                .satisfies(
                        finding -> assertThat(finding.code()).isEqualTo(PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("the registered shard's read reports a refused edge, never drops it")
    void theRegisteredShardReportsARefusedEdge() {
        RawGraph shard = new RawGraph(
                "registered.graph.yaml",
                RawGraph.Shape.SHARD,
                List.of(),
                List.of(new RawEdge(
                        "procedure:p",
                        "action:software-dev/implement",
                        "requires",
                        Optional.empty(),
                        Optional.empty())));

        GraphBinding.GraphBuild build = GraphBinding.registeredGraph("main", shard);

        assertThat(build.graph().relationships()).isEmpty();
        assertThat(build.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.place()).containsExactly("edges", "0");
        });
    }

    @Test
    @DisplayName("the load lists one violation per refused edge, at its graph document, in edge order")
    void theLoadListsOneViolationPerEdge() {
        List<PackElementViolation> violations = RelationshipEndpointRule.violations(
                List.of(
                        edge("procedure:p", "requires", "action:software-dev/implement"),
                        edge("procedure:p", "requires", "directive:D"),
                        edge("directive:d", "overrides", "tactic:t")),
                MAIN);

        assertThat(violations)
                .extracting(PackElementViolation::file, v -> v.kind().value(), PackElementViolation::reason)
                .containsExactly(
                        tuple(
                                FILE,
                                RelationshipEndpointRule.CODE,
                                "a requires edge may not connect procedure to action"),
                        tuple(
                                FILE,
                                RelationshipEndpointRule.CODE,
                                "an overrides edge may not connect directive to tactic"));
    }

    @Test
    @DisplayName("validate mode reports the edge as one blocking finding at its place, and the twin passes")
    void validateModeReportsTheEdgeAtItsPlace() {
        List<PackFinding> findings = PackPolicy.judge(
                        Readouts.of(validated("procedure:p", "requires", "action:software-dev/implement")))
                .findings();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(finding.file()).isEqualTo(FILE);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail()).isEqualTo(FILE + ": a requires edge may not connect procedure to action");
        });
        assertThat(PackPolicy.judge(Readouts.of(validated("procedure:p", "requires", "directive:D")))
                        .findings())
                .isEmpty();
    }

    @Test
    void theFindingCodeAndTheViolationCodeAreOneToken() {
        assertThat(PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED.value()).isEqualTo(RelationshipEndpointRule.CODE);
    }

    private static PackContents validated(String source, String relation, String target) {
        RawGraph shard = new RawGraph(
                FILE,
                RawGraph.Shape.SHARD,
                List.of(),
                List.of(new RawEdge(source, target, relation, Optional.empty(), Optional.empty())));
        return new PackContents(
                new PackRoot("main", Optional.of(PackLevel.ORG), "loc"),
                new FileRead.Read<>(
                        "pack.yaml",
                        new PackDescriptor(
                                "01ARWG13C00000000000001ABC",
                                "1.0.0",
                                Optional.empty(),
                                "main",
                                Optional.of(PackLevel.ORG),
                                Optional.empty())),
                List.of(new FileRead.Read<>(FILE, shard)),
                new FileRead.Read<>(
                        "drg/fragment.yaml",
                        new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of())),
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of());
    }

    // ---- the source rule over edges as written (moved from OverrideSourceRuleTest when the rule was folded into the
    // bridge, operator ruling DM-01M7B1Z03VF9A36FBV65431HR3) ----------------------------------------------------------

    private static final String OVERRIDES_FILE = "overrides.graph.yaml";
    private static final String OVERRIDES_TARGET = "base::directive:D";

    private static RelationshipEndpointRule.Edge overrides(String source) {
        return new RelationshipEndpointRule.Edge(OVERRIDES_FILE, source, OVERRIDES_TARGET, "overrides");
    }

    private static Optional<String> sourceRefusal(RelationshipEndpointRule.Edge edge) {
        return RelationshipEndpointRule.refusal(edge, MAIN)
                .filter(refusal -> EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE.equals(refusal.code()))
                .map(RelationshipEndpointRule.Refusal::reason);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = ArtifactKind.class,
            names = {"TEMPLATE", "ASSET", "GUARD", "LIFECYCLE"})
    void anOverridesEdgeFromEachKindThatIsNotActivatableIsOneViolationAtItsFile(ArtifactKind kind) {
        String source = NodeKind.of(kind).token() + ":t";

        assertThat(RelationshipEndpointRule.violations(List.of(overrides(source)), MAIN))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.file()).isEqualTo(OVERRIDES_FILE);
                    assertThat(violation.kind().value()).isEqualTo("overrides_source_not_activatable");
                    assertThat(violation.reason())
                            .contains(source)
                            .contains(OVERRIDES_TARGET)
                            .contains("not an activatable kind");
                });
    }

    @Test
    void aSourceQualifiedWithAPackNameIsJudgedLikeABareOne() {
        assertThat(sourceRefusal(overrides("main::template:t.md")))
                .hasValueSatisfying(reason -> assertThat(reason).contains("main::template:t.md"));
        assertThat(sourceRefusal(overrides("main::directive:E"))).isEmpty();
    }

    @Test
    void theSourceRuleJudgesTheSourceOnlyWhateverTheTarget() {
        assertThat(sourceRefusal(
                        new RelationshipEndpointRule.Edge(OVERRIDES_FILE, "template:t.md", "not a urn", "overrides")))
                .as("a target that is no reference")
                .isPresent();
        assertThat(sourceRefusal(
                        new RelationshipEndpointRule.Edge(OVERRIDES_FILE, "directive:E", "template:t.md", "overrides")))
                .as("an overrides edge to a template")
                .isEmpty();
    }

    @Test
    void aSourceThatIsNoUrnIsLeftToTheGraphRules() {
        assertThat(RelationshipEndpointRule.refusal(overrides("not a urn"), MAIN))
                .isEmpty();
    }

    @Test
    void theLoadListsTheSourceFaultsFirstThenTheTablesEachInEdgeOrder() {
        List<PackElementViolation> violations = RelationshipEndpointRule.violations(
                List.of(
                        edge("procedure:p", "requires", "action:software-dev/implement"),
                        overrides("template:t.md"),
                        overrides("directive:E"),
                        new RelationshipEndpointRule.Edge(
                                "other.graph.yaml", "asset:logo", OVERRIDES_TARGET, "overrides")),
                MAIN);

        assertThat(violations)
                .extracting(PackElementViolation::file, v -> v.kind().value())
                .containsExactly(
                        tuple(OVERRIDES_FILE, "overrides_source_not_activatable"),
                        tuple("other.graph.yaml", "overrides_source_not_activatable"),
                        tuple(FILE, RelationshipEndpointRule.CODE));
        assertThat(violations.get(0).reason()).contains("template:t.md");
        assertThat(violations.get(1).reason()).contains("asset:logo");
    }

    @Test
    void validateModeReportsAnInactivatableOverriderAsOneBlockingFindingAndTheTwinPasses() {
        List<PackFinding> findings = PackPolicy.judge(
                        Readouts.of(validated("template:t.md", "overrides", OVERRIDES_TARGET)))
                .findings();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE);
            assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(finding.file()).isEqualTo(FILE);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail()).startsWith(FILE + ": ").contains("template:t.md");
        });
        assertThat(PackPolicy.judge(Readouts.of(validated("directive:E", "overrides", OVERRIDES_TARGET)))
                        .findings())
                .isEmpty();
    }

    @Test
    void theSourceRulesFindingCodeAndViolationCodeAreOneToken() {
        assertThat(PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE.value())
                .isEqualTo(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE);
    }
}
