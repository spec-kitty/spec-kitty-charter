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
    void relationshipsAnswerTheRelationshipsInDeclaredOrder() {
        Relationship first = Relationship.of(A, C, RelationType.SUGGESTS);
        Relationship other = Relationship.of(A, B, RelationType.REQUIRES);
        Relationship elsewhere = Relationship.of(B, C, RelationType.SUGGESTS);
        DoctrineGraph graph = new DoctrineGraph(List.of(), List.of(first, other, elsewhere));
        assertThat(graph.relationships()).containsExactly(first, other, elsewhere);
    }

    @Test
    void nodesAreKeyedByUrnAndFirstDeclarationWins() {
        DrgNode a = new DrgNode(A, Optional.of("first"));
        DrgNode aAgain = new DrgNode(A, Optional.of("second"));
        DrgNode b = new DrgNode(B, Optional.empty());
        DoctrineGraph graph = new DoctrineGraph(List.of(a, aAgain, b), List.of());
        assertThat(graph.nodes()).containsExactly(a, b);
        assertThat(graph.node(A)).contains(a);
        assertThat(graph.contains(B)).isTrue();
        assertThat(graph.contains(C)).isFalse();
        assertThat(DoctrineGraph.empty().relationships()).isEmpty();
    }

    @Test
    void aMergedGraphAppendsTheRelationshipsOfBothLayersInOrder() {
        Relationship fromFirst = Relationship.of(A, B, RelationType.REQUIRES);
        Relationship fromSecond = Relationship.of(A, C, RelationType.REQUIRES);
        DoctrineGraph merged = new DoctrineGraph(List.of(new DrgNode(A, Optional.empty())), List.of(fromFirst))
                .merge(new DoctrineGraph(List.of(), List.of(fromSecond)));
        assertThat(merged.relationships()).containsExactly(fromFirst, fromSecond);
        assertThat(merged.nodes()).containsExactly(new DrgNode(A, Optional.empty()));
    }

    @Test
    void valueEqualityComparesNodesAndRelationships() {
        Relationship r = Relationship.of(A, B, RelationType.REQUIRES);
        DoctrineGraph one = new DoctrineGraph(List.of(new DrgNode(A, Optional.empty())), List.of(r));
        DoctrineGraph same = new DoctrineGraph(List.of(new DrgNode(A, Optional.empty())), List.of(r));
        DoctrineGraph different = new DoctrineGraph(List.of(), List.of(r));
        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(one).isNotEqualTo(different);
    }
}
