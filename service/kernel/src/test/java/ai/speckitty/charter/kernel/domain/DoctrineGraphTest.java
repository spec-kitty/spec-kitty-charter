package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DoctrineGraphTest {

    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.TACTIC, "b");
    private static final Urn C = new Urn(NodeKind.TACTIC, "c");

    @Test
    void outgoingAnswersTheEdgesOfOneSourceAndRelationInDeclaredOrder() {
        DrgEdge first = DrgEdge.of(A, C, Relation.SUGGESTS);
        DrgEdge other = DrgEdge.of(A, B, Relation.REQUIRES);
        DrgEdge second = DrgEdge.of(A, B, Relation.SUGGESTS);
        DrgEdge elsewhere = DrgEdge.of(B, C, Relation.SUGGESTS);
        DoctrineGraph graph = new DoctrineGraph(List.of(), List.of(first, other, elsewhere, second));
        assertThat(graph.outgoing(A, Relation.SUGGESTS)).containsExactly(first, second);
        assertThat(graph.outgoing(A, Relation.REQUIRES)).containsExactly(other);
        assertThat(graph.outgoing(B, Relation.SUGGESTS)).containsExactly(elsewhere);
    }

    @Test
    void outgoingIsEmptyForASourceOrRelationWithNoEdge() {
        DoctrineGraph graph = new DoctrineGraph(List.of(), List.of(DrgEdge.of(A, B, Relation.SCOPE)));
        assertThat(graph.outgoing(C, Relation.SCOPE)).isEmpty();
        assertThat(graph.outgoing(A, Relation.VOCABULARY)).isEmpty();
        assertThat(DoctrineGraph.empty().outgoing(A, Relation.SCOPE)).isEmpty();
    }

    @Test
    void aMergedGraphIndexesTheEdgesOfBothLayersInOrder() {
        DrgEdge fromFirst = DrgEdge.of(A, B, Relation.REQUIRES);
        DrgEdge fromSecond = DrgEdge.of(A, C, Relation.REQUIRES);
        DoctrineGraph merged = new DoctrineGraph(List.of(new DrgNode(A, Optional.empty())), List.of(fromFirst))
                .merge(new DoctrineGraph(List.of(), List.of(fromSecond)));
        assertThat(merged.outgoing(A, Relation.REQUIRES)).containsExactly(fromFirst, fromSecond);
    }
}
