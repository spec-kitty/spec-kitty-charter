package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.PackPolicy.GraphBuild;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PackPolicyGraphTest {

    private static final String SHARD = "tactic.graph.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";
    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.DIRECTIVE, "b");

    private static RawGraph shard(List<RawNode> nodes, List<RawEdge> edges) {
        return new RawGraph(SHARD, RawGraph.Shape.SHARD, nodes, edges);
    }

    private static RawGraph fragment(List<RawNode> nodes, List<RawEdge> edges) {
        return new RawGraph(FRAGMENT, RawGraph.Shape.FRAGMENT, nodes, edges);
    }

    private static RawEdge edge(String source, String target, String relation, Double impacts) {
        return new RawEdge(source, target, relation, Optional.ofNullable(impacts), Optional.empty(), Optional.empty());
    }

    private static List<String> details(GraphBuild build) {
        return build.findings().stream().map(PackFinding::detail).toList();
    }

    private static GraphBuild build(List<RawEdge> edges) {
        return PackPolicy.graph("qa", shard(List.of(), edges));
    }

    // ---- nodes ---------------------------------------------------------------------------------

    @Test
    void shardNodesBindToTheirUrn() {
        GraphBuild build =
                PackPolicy.graph("qa", shard(List.of(new RawNode("tactic:a", "tactic", Optional.of("A"))), List.of()));
        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().nodes()).containsExactly(new DrgNode(A, Optional.of("A")));
    }

    @Test
    void aShardNodeUrnThatDoesNotParseIsAFindingAndTheNodeIsDropped() {
        GraphBuild build =
                PackPolicy.graph("qa", shard(List.of(new RawNode("nonsense", "tactic", Optional.empty())), List.of()));
        assertThat(build.graph().nodes()).isEmpty();
        assertThat(build.findings())
                .containsExactly(new PackFinding(
                        PackFindingCode.DRG_SCHEMA_ERROR,
                        SHARD,
                        List.of("nodes", "0"),
                        "qa/tactic.graph.yaml: node urn 'nonsense' is not <kind>:<id>"));
    }

    @Test
    void fragmentNodesBindThePluralKindAndTheIdToAUrn() {
        GraphBuild build = PackPolicy.graph(
                "qa",
                fragment(
                        List.of(
                                new RawNode("a", "tactics", Optional.of("A")),
                                new RawNode("q", "mission_types", Optional.empty())),
                        List.of()));
        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().nodes())
                .containsExactly(new DrgNode(A, Optional.of("A")), new DrgNode(Urn.missionType("q"), Optional.empty()));
    }

    @Test
    void aFragmentNodeOfAnUnknownKindIsAFinding() {
        GraphBuild build = PackPolicy.graph(
                "qa", fragment(List.of(new RawNode("t1", "mission_steps", Optional.empty())), List.of()));
        assertThat(build.graph().nodes()).isEmpty();
        assertThat(details(build)).containsExactly("qa/drg/fragment.yaml: node t1 has unknown kind 'mission_steps'");
    }

    @Test
    void aFragmentNodeWithAnEmptyIdIsAFindingNotAnException() {
        GraphBuild build =
                PackPolicy.graph("qa", fragment(List.of(new RawNode("", "directives", Optional.empty())), List.of()));
        assertThat(details(build)).containsExactly("qa/drg/fragment.yaml: a node of kind 'directives' has an empty id");
    }

    // ---- edges ---------------------------------------------------------------------------------

    @Test
    void edgesBindBothEndpointsWithTheirOptionalTexts() {
        RawEdge raw = new RawEdge(
                "tactic:a", "directive:b", "requires", Optional.empty(), Optional.of("w"), Optional.of("r"));
        GraphBuild build = build(List.of(raw));
        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().relationships()).singleElement().satisfies(relationship -> {
            assertThat(relationship.from()).isEqualTo(A);
            assertThat(relationship.to()).isEqualTo(B);
            assertThat(relationship.type()).isEqualTo(RelationType.REQUIRES);
            assertThat(relationship.when()).contains("w");
            assertThat(relationship.rationale()).contains("r");
        });
    }

    @Test
    void aNonUrnEndpointIsAFindingAndNotAnException() {
        GraphBuild build = build(List.of(edge("tactic:a", "QA_404", "requires", null)));
        assertThat(build.graph().relationships()).isEmpty();
        assertThat(build.findings())
                .containsExactly(new PackFinding(
                        PackFindingCode.DRG_SCHEMA_ERROR,
                        SHARD,
                        List.of("edges", "0"),
                        "qa/tactic.graph.yaml: edge endpoint 'QA_404' is not a URN"));
        assertThat(details(build(List.of(edge("nope", "tactic:a", "requires", null)))))
                .containsExactly("qa/tactic.graph.yaml: edge endpoint 'nope' is not a URN");
    }

    @Test
    void aSelfLoopEdgeIsAFindingAndNotAnException() {
        GraphBuild build = build(List.of(edge("tactic:a", "tactic:a", "requires", null)));
        assertThat(build.graph().relationships()).isEmpty();
        assertThat(details(build))
                .containsExactly("qa/tactic.graph.yaml: a relationship cannot join 'tactic:a' to itself");
    }

    @Test
    void anUnknownRelationIsAFinding() {
        GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "befriends", null)));
        assertThat(build.graph().relationships()).isEmpty();
        assertThat(details(build)).containsExactly("qa/tactic.graph.yaml: unknown relation 'befriends'");
    }

    @Test
    void aLegacyInTensionWithEdgeBecomesTheImpactsRelation() {
        GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "in_tension_with", null)));
        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().relationships()).singleElement().satisfies(relationship -> {
            assertThat(relationship.from()).isEqualTo(A);
            assertThat(relationship.to()).isEqualTo(B);
            assertThat(relationship.type()).isEqualTo(RelationType.IMPACTS);
        });
    }

    @Test
    void aLegacyInTensionWithEdgeThatAlsoWritesImpactsIsAFinding() {
        GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "in_tension_with", -0.5)));
        assertThat(build.graph().relationships()).isEmpty();
        assertThat(details(build)).containsExactly("qa/tactic.graph.yaml: an in_tension_with edge has no 'impacts'");
    }

    @Test
    void anImpactsEdgeNeedsItsStrength() {
        GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "impacts", null)));
        assertThat(build.graph().relationships()).isEmpty();
        assertThat(details(build)).containsExactly("qa/tactic.graph.yaml: an impacts edge is missing 'impacts'");
    }

    @Test
    void anotherRelationMustNotCarryImpacts() {
        GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "scope", 0.5)));
        assertThat(build.graph().relationships()).isEmpty();
        assertThat(details(build)).containsExactly("qa/tactic.graph.yaml: a scope edge has no 'impacts'");
    }

    @Test
    void aStrengthInsideTheRangeBindsButIsNotCarriedOnTheRelationship() {
        GraphBuild build = build(List.of(
                edge("tactic:a", "directive:b", "impacts", 1.0), edge("tactic:a", "directive:b", "impacts", -1.0)));
        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().relationships())
                .hasSize(2)
                .allSatisfy(relationship -> assertThat(relationship.type()).isEqualTo(RelationType.IMPACTS));
    }

    @Test
    void aStrengthOutsideTheRangeNamesTheValueAsDoubleToStringFormatsIt() {
        record Case(double value, String text) {}
        for (Case c : List.of(
                new Case(1.5, "1.5"),
                new Case(-2.0, "-2.0"),
                new Case(Double.NaN, "NaN"),
                new Case(Double.POSITIVE_INFINITY, "Infinity"),
                new Case(Double.NEGATIVE_INFINITY, "-Infinity"))) {
            GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "impacts", c.value())));
            assertThat(build.graph().relationships()).as(c.text()).isEmpty();
            assertThat(details(build))
                    .as(c.text())
                    .containsExactly("qa/tactic.graph.yaml: 'impacts' " + c.text() + " is outside [-1, 1]");
        }
    }

    @Test
    void anEdgeWithAFindingIsNotAddedAndItsNeighboursAre() {
        GraphBuild build = build(List.of(
                edge("tactic:a", "directive:b", "requires", null),
                edge("tactic:a", "QA_404", "requires", null),
                edge("tactic:a", "directive:b", "suggests", null)));
        assertThat(build.graph().relationships())
                .extracting(r -> r.type())
                .containsExactly(RelationType.REQUIRES, RelationType.SUGGESTS);
        assertThat(build.findings()).extracting(PackFinding::place).containsExactly(List.of("edges", "1"));
    }

    @Test
    void anEdgeReportsItsFirstFaultInTodaysOrderRelationBeforeStrengthBeforeEndpoint() {
        GraphBuild build = build(List.of(edge("nope", "directive:b", "befriends", 9.0)));
        assertThat(details(build)).containsExactly("qa/tactic.graph.yaml: unknown relation 'befriends'");
        GraphBuild second = build(List.of(edge("nope", "directive:b", "impacts", 9.0)));
        assertThat(details(second)).containsExactly("qa/tactic.graph.yaml: 'impacts' 9.0 is outside [-1, 1]");
    }
}
