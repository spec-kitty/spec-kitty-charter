package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import java.util.List;
import java.util.Locale;
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

    private static OverrideSourceRule.Edge edge(String source, String relation, String target) {
        return new OverrideSourceRule.Edge(FILE, source, target, relation);
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
        List<PackFinding> findings =
                PackPolicy.judge(validated(source, relation, target)).findings();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
            assertThat(finding.file()).isEqualTo(FILE);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail()).startsWith("main/" + FILE + ": ").contains(relation);
        });
        assertThat(PackPolicy.judge(validated(twinSource, relation, twinTarget)).findings())
                .isEmpty();
    }

    /** The pack "main", which holds the step enhancement e. */
    private static final RelationshipEndpointRule.Scope MAIN = new RelationshipEndpointRule.Scope("main", Set.of("e"));

    @Test
    @DisplayName("a fault another rule owns is not reported again, nor is an edge the graph rules refuse")
    void theFaultsOfOtherRulesAreLeftToThem() {
        assertThat(RelationshipEndpointRule.refusal(edge("template:t.md", "overrides", "directive:D"), MAIN))
                .as("slice C's overrides_source_not_activatable")
                .isEmpty();
        assertThat(RelationshipEndpointRule.refusal(edge("step_enhancement:e", "overrides", "mission_type:m"), MAIN))
                .as("the step-enhancement rules judge the held enhancement (prompt_fragment_invalid)")
                .isEmpty();
        assertThat(RelationshipEndpointRule.refusal(edge("main::step_enhancement:e", "enhances", "directive:D"), MAIN))
                .as("the held enhancement written with the pack's own name")
                .isEmpty();
        assertThat(RelationshipEndpointRule.refusal(edge("action:m/s", "instantiates", "action:m/s"), MAIN))
                .as("a bare self-loop is a graph fault, even under a relation that refuses its kinds")
                .isEmpty();
        assertThat(RelationshipEndpointRule.refusal(edge("directive:D", "extends", "action:a/b"), MAIN))
                .as("an unknown relation is a graph fault")
                .isEmpty();
        assertThat(RelationshipEndpointRule.refusal(edge("not a urn", "requires", "action:a/b"), MAIN))
                .as("an endpoint that is no URN is a graph fault")
                .isEmpty();
    }

    @Test
    @DisplayName("an edge from a step enhancement the step-enhancement rules do not judge is the table's to refuse")
    void anEnhancementEdgeNoOtherRuleJudgesIsRefusedByTheTable() {
        assertThat(RelationshipEndpointRule.refusal(edge("step_enhancement:ghost", "enhances", "directive:D"), MAIN))
                .as("an enhancement the pack does not hold")
                .contains("an enhances edge may not connect step_enhancement to directive");
        assertThat(RelationshipEndpointRule.refusal(
                        edge("other::step_enhancement:e", "overrides", "mission_type:m"), MAIN))
                .as("another pack's enhancement")
                .contains("an overrides edge may not connect step_enhancement to mission_type");
        assertThat(RelationshipEndpointRule.refusal(edge("step_enhancement:e", "requires", "directive:D"), MAIN))
                .as("a relation the step-enhancement rules do not judge")
                .contains("a requires edge may not connect step_enhancement to directive");
    }

    @Test
    @DisplayName("validate mode reports a bare self-loop once, as the graph fault it is")
    void validateModeReportsABareSelfLoopOnce() {
        List<PackFinding> findings = PackPolicy.judge(validated("action:m/s", "instantiates", "action:m/s"))
                .findings();

        assertThat(findings)
                .singleElement()
                .satisfies(finding -> assertThat(finding.code()).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR));
    }

    @Test
    @DisplayName("validate mode refuses an edge from an enhancement the pack does not hold")
    void validateModeRefusesAnUnheldEnhancementEdge() {
        assertThat(PackPolicy.judge(validated("step_enhancement:ghost", "enhances", "directive:D"))
                        .findings())
                .singleElement()
                .satisfies(finding ->
                        assertThat(finding.code()).isEqualTo(PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED));
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

        PackPolicy.GraphBuild build = PackPolicy.registeredGraph("main", shard);

        assertThat(build.graph().relationships()).isEmpty();
        assertThat(build.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
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
                .extracting(PackElementViolation::file, PackElementViolation::kind, PackElementViolation::reason)
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
                        validated("procedure:p", "requires", "action:software-dev/implement"))
                .findings();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
            assertThat(finding.file()).isEqualTo(FILE);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail())
                    .isEqualTo("main/" + FILE + ": a requires edge may not connect procedure to action");
        });
        assertThat(PackPolicy.judge(validated("procedure:p", "requires", "directive:D"))
                        .findings())
                .isEmpty();
    }

    @Test
    void theFindingCodeAndTheViolationCodeAreOneToken() {
        assertThat(PackPolicy.violationKind(PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED))
                .isEqualTo(RelationshipEndpointRule.CODE);
        assertThat(PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED.name().toLowerCase(Locale.ROOT))
                .isEqualTo(RelationshipEndpointRule.CODE);
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
                List.of(),
                List.of(),
                List.of());
    }
}
