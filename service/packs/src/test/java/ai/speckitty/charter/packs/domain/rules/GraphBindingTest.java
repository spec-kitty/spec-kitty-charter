package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Tension;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.rules.GraphBinding.GraphBuild;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphBindingTest {

    private static final String SHARD = "tactic.graph.yaml";
    private static final String REGISTERED = "registered.graph.yaml";
    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.DIRECTIVE, "b");

    private static RawEdge edge(String source, String target, String relation) {
        return new RawEdge(source, target, relation, Optional.empty(), Optional.empty());
    }

    private static RawGraph shard(String file, List<RawNode> nodes, List<RawEdge> edges) {
        return new RawGraph(file, RawGraph.Shape.SHARD, nodes, edges);
    }

    private static PackQualifiedReference ref(String text) {
        return PackQualifiedReference.parse(text);
    }

    // ---- the graph of a pack's graph document -------------------------------------------------------

    @Test
    void aShardBindsItsNodesItsRelationshipsAndItsTensions() {
        GraphBuild build = GraphBinding.graph(
                "qa",
                shard(
                        SHARD,
                        List.of(new RawNode("tactic:a", "tactic", Optional.of("A"))),
                        List.of(
                                edge("tactic:a", "directive:b", "requires"),
                                edge("tactic:a", "directive:b", "in_tension_with"))));

        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().nodes()).containsExactly(new DrgNode(A, Optional.of("A")));
        assertThat(build.graph().relationships()).singleElement().satisfies(relationship -> {
            assertThat(relationship.type()).isEqualTo(RelationType.REQUIRES);
            assertThat(relationship.from()).isEqualTo(A);
            assertThat(relationship.to()).isEqualTo(B);
        });
        assertThat(build.graph().tensions()).containsExactly(new Tension(A, B, Optional.empty(), Optional.empty()));
    }

    @Test
    void anEdgeThatDoesNotBindIsADrgSchemaFindingAtItsPlace() {
        GraphBuild build = GraphBinding.graph(
                "qa",
                shard(
                        SHARD,
                        List.of(),
                        List.of(
                                edge("tactic:a", "directive:b", "requires"),
                                edge("tactic:a", "directive:b", "befriends"))));

        assertThat(build.graph().relationships()).hasSize(1);
        assertThat(build.findings())
                .containsExactly(new PackFinding(
                        PackCodes.DRG_SCHEMA_ERROR,
                        SHARD,
                        List.of("edges", "1"),
                        "qa/tactic.graph.yaml: unknown relation 'befriends'"));
    }

    @Test
    void anEdgeTheTableRefusesIsLeftOutOfAPacksGraphWithoutAFinding() {
        // A pack's graph documents report the table's refusal under its own code, in a pack-level pass.
        GraphBuild build = GraphBinding.graph(
                "main",
                shard(SHARD, List.of(), List.of(edge("procedure:p", "action:software-dev/implement", "requires"))));

        assertThat(build.graph().relationships()).isEmpty();
        assertThat(build.findings()).isEmpty();
    }

    // ---- the registered-relationship shard ----------------------------------------------------------

    @Test
    void theRegisteredShardHoldsNoTension() {
        GraphBuild build = GraphBinding.registeredGraph(
                "qa", shard(REGISTERED, List.of(), List.of(edge("tactic:a", "directive:c", GraphBinding.TENSION))));

        assertThat(build.graph().tensions()).isEmpty();
        assertThat(build.findings())
                .containsExactly(new PackFinding(
                        PackCodes.DRG_SCHEMA_ERROR,
                        REGISTERED,
                        List.of("edges", "0"),
                        "qa/registered.graph.yaml: a registered relationship is never a tension; in_tension_with is"
                                + " authored in a pack's graph shards"));
    }

    @Test
    void theRegisteredShardReportsAnEdgeTheTableRefusesUnderTheEndpointCode() {
        GraphBuild build = GraphBinding.registeredGraph(
                "main",
                shard(
                        REGISTERED,
                        List.of(),
                        List.of(edge("procedure:p", "action:software-dev/implement", "requires"))));

        assertThat(build.graph().relationships()).isEmpty();
        assertThat(build.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.file()).isEqualTo(REGISTERED);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail()).startsWith("main/registered.graph.yaml: ");
        });
    }

    // ---- the vocabulary of an endpoint --------------------------------------------------------------

    @Test
    void theEndpointTextsKeepTheirWording() {
        assertThat(GraphBinding.TENSION).isEqualTo("in_tension_with");
        assertThat(GraphBinding.NOT_AUTHORED)
                .isEqualTo("a guard or a lifecycle is not an authored edge endpoint until its contract set lands: ");
        assertThat(GraphBinding.TERM_ENDPOINT)
                .isEqualTo("a term is an edge endpoint only as the target of a vocabulary edge: ");
    }

    // ---- a self-loop --------------------------------------------------------------------------------

    @Test
    void twoReferencesToTheSameArtifactOfTheWritingPackJoinItself() {
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("tactic:a")))
                .isTrue();
        assertThat(GraphBinding.joinsItself("qa", ref("qa::tactic:a"), ref("tactic:a")))
                .isTrue();
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("qa::tactic:a")))
                .isTrue();
    }

    @Test
    void aReferenceQualifiedWithAnotherPackOrAnotherIdDoesNotJoinItself() {
        assertThat(GraphBinding.joinsItself("qa", ref("rival::tactic:a"), ref("tactic:a")))
                .isFalse();
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("rival::tactic:a")))
                .isFalse();
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("tactic:b")))
                .isFalse();
    }
}
