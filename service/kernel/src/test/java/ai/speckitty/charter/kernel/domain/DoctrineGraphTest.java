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

    @Test
    void tensionsAreHeldBesideTheRelationshipsInDeclaredOrder() {
        Relationship requires = Relationship.of(B, A, RelationType.REQUIRES);
        Tension first = new Tension(A, B, Optional.empty(), Optional.of("r"));
        Tension second = new Tension(A, C, Optional.of("w"), Optional.empty());
        DoctrineGraph graph = new DoctrineGraph(List.of(), List.of(requires), List.of(first, second));

        assertThat(graph.relationships())
                .singleElement()
                .satisfies(r -> assertThat(r.type()).isEqualTo(RelationType.REQUIRES));
        assertThat(graph.tensions()).containsExactly(first, second);
        assertThat(new DoctrineGraph(List.of(), List.of(requires)).tensions()).isEmpty();
        assertThat(DoctrineGraph.empty().tensions()).isEmpty();
    }

    @Test
    void aMergedGraphAppendsTheTensionsOfBothLayersInOrder() {
        Tension fromFirst = new Tension(A, B, Optional.empty(), Optional.empty());
        Tension fromSecond = new Tension(A, C, Optional.empty(), Optional.empty());
        DoctrineGraph merged = new DoctrineGraph(List.of(), List.of(), List.of(fromFirst))
                .merge(new DoctrineGraph(List.of(), List.of(), List.of(fromSecond)));

        assertThat(merged.tensions()).containsExactly(fromFirst, fromSecond);
    }

    @Test
    void graphsThatDifferOnlyByATensionAreNotEqual() {
        Tension tension = new Tension(A, B, Optional.empty(), Optional.empty());
        DoctrineGraph with = new DoctrineGraph(List.of(), List.of(), List.of(tension));
        DoctrineGraph without = new DoctrineGraph(List.of(), List.of());

        assertThat(with).isNotEqualTo(without);
        assertThat(with.hashCode()).isNotEqualTo(without.hashCode());
        assertThat(with).isEqualTo(new DoctrineGraph(List.of(), List.of(), List.of(tension)));
    }

    @Test
    void graphsThatDifferOnlyInANodesTagOrderAreNotEqual() {
        DoctrineGraph ba = new DoctrineGraph(
                List.of(new DrgNode(A, Optional.empty(), Optional.empty(), List.of("b", "a"), Optional.empty())),
                List.of());
        DoctrineGraph ab = new DoctrineGraph(
                List.of(new DrgNode(A, Optional.empty(), Optional.empty(), List.of("a", "b"), Optional.empty())),
                List.of());

        assertThat(ba).isNotEqualTo(ab);
    }

    @Test
    void aNodeDeclaredTwiceKeepsItsFirstDeclarationWholeTagsIncluded() {
        DrgNode first = new DrgNode(A, Optional.empty(), Optional.empty(), List.of("t"), Optional.empty());
        DrgNode second = new DrgNode(A, Optional.empty(), Optional.of("p"), List.of("u"), Optional.empty());

        assertThat(new DoctrineGraph(List.of(first, second), List.of()).node(A)).contains(first);
    }
}
