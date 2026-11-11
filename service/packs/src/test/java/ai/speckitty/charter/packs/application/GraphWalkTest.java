package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.pack;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NFR-001 / FR-004: the walk over an {@link EdgeIndex} visits every reachable component once, guards against cycles,
 * passes over a neighbour the closure does not hold, keeps a bare reference in the pack that writes it, and honours a
 * visitor's {@link Descent}. A recording visitor gives every scenario a same-fixture positive control.
 */
@DisplayName("Graph walk")
class GraphWalkTest {

    @Test
    @DisplayName("a chain A->B->C visits every reachable component once and never an unreachable one")
    void chainVisitsEachReachableComponentOnce() {
        EdgeIndex index = new Closure()
                .pack(
                        "p",
                        List.of("directive:a", "directive:b", "directive:c", "directive:z"),
                        edge("directive:a", RelationType.REQUIRES, "directive:b"),
                        edge("directive:b", RelationType.REQUIRES, "directive:c"))
                .index();
        Recorder recorder = new Recorder();

        walkFrom(index, "p", "directive:a", recorder);

        assertThat(recorder.visited).containsExactly(urn("directive:a"), urn("directive:b"), urn("directive:c"));
    }

    @Test
    @DisplayName("a cycle A->B->A terminates")
    void cycleTerminates() {
        EdgeIndex index = new Closure()
                .pack(
                        "p",
                        List.of("directive:a", "directive:b"),
                        edge("directive:a", RelationType.REQUIRES, "directive:b"),
                        edge("directive:b", RelationType.REQUIRES, "directive:a"))
                .index();
        Recorder recorder = new Recorder();

        walkFrom(index, "p", "directive:a", recorder);

        assertThat(recorder.visited).containsExactly(urn("directive:a"), urn("directive:b"));
    }

    @Test
    @DisplayName("a neighbour the closure does not hold is passed over and the walk continues to the other neighbours")
    void aNeighbourTheClosureDoesNotHoldIsSkippedAndTheWalkContinues() {
        EdgeIndex index = new Closure()
                .pack(
                        "p",
                        List.of("directive:a", "directive:wired"),
                        edge("directive:a", RelationType.REQUIRES, "directive:unheld"),
                        edge("directive:a", RelationType.SUGGESTS, "directive:wired"))
                .index();
        Recorder recorder = new Recorder();

        walkFrom(index, "p", "directive:a", recorder);

        assertThat(recorder.visited).containsExactly(urn("directive:a"), urn("directive:wired"));
        assertThat(recorder.crossed)
                .as("the edge to the unheld neighbour is still announced")
                .hasSize(2);
    }

    @Test
    @DisplayName("Descent.STOP from a node halts the whole walk before any neighbour is visited")
    void stopHaltsTheWalk() {
        EdgeIndex index = new Closure()
                .pack(
                        "p",
                        List.of("directive:a", "directive:b"),
                        edge("directive:a", RelationType.REQUIRES, "directive:b"))
                .index();
        Recorder recorder = new Recorder();
        recorder.nodeDescent = Descent.STOP;

        walkFrom(index, "p", "directive:a", recorder);

        assertThat(recorder.visited).containsExactly(urn("directive:a"));
    }

    @Test
    @DisplayName("Descent.SKIP on a node does not descend into its relationships but does not stop the walk")
    void skipDoesNotDescend() {
        EdgeIndex index = new Closure()
                .pack(
                        "p",
                        List.of("directive:a", "directive:b"),
                        edge("directive:a", RelationType.REQUIRES, "directive:b"))
                .index();
        Recorder recorder = new Recorder();
        recorder.nodeDescent = Descent.SKIP;

        walkFrom(index, "p", "directive:a", recorder);

        assertThat(recorder.visited).containsExactly(urn("directive:a"));
        assertThat(recorder.crossed).isEmpty();
    }

    @Test
    @DisplayName("Descent.SKIP on a relationship skips that neighbour but keeps walking the siblings")
    void skipOnRelationshipKeepsSiblings() {
        EdgeIndex index = new Closure()
                .pack(
                        "p",
                        List.of("directive:a", "directive:skipped", "directive:kept"),
                        edge("directive:a", RelationType.REQUIRES, "directive:skipped"),
                        edge("directive:a", RelationType.SUGGESTS, "directive:kept"))
                .index();
        Recorder recorder = new Recorder();
        recorder.skipRelationshipTo = urn("directive:skipped");

        walkFrom(index, "p", "directive:a", recorder);

        assertThat(recorder.visited).containsExactly(urn("directive:a"), urn("directive:kept"));
    }

    @Test
    @DisplayName("a walk across packs keys its visited set by (holding pack, bare urn) and keeps a bare reference in"
            + " the pack of the artifact that writes it")
    void crossPackWalkTerminatesAndResolvesBareReferencesInTheHoldingPack() {
        // X (a) -> b::Y; Y (b) -> a::X (back where it started) and bare Z; Z (b) -> a::X again and a::Y, the Y that
        // pack a holds beside pack b's own Y.
        EdgeIndex index = new Closure()
                .pack(
                        "a",
                        List.of("b"),
                        List.of("directive:X", "directive:Y"),
                        edge("directive:X", RelationType.REQUIRES, "b::directive:Y"))
                .pack(
                        "b",
                        List.of("a"),
                        List.of("directive:Y", "directive:Z"),
                        edge("directive:Y", RelationType.REQUIRES, "a::directive:X"),
                        edge("directive:Y", RelationType.REQUIRES, "directive:Z"),
                        edge("directive:Z", RelationType.REQUIRES, "a::directive:X"),
                        edge("directive:Z", RelationType.REQUIRES, "a::directive:Y"))
                .index();
        Recorder recorder = new Recorder();

        walkFrom(index, "a", "directive:X", recorder);

        // pack b's Y and pack a's Y are two components of one bare urn: both are visited, neither shadows the other.
        // Z exists only in pack b, so a bare reference that stayed in pack a would never find it.
        assertThat(recorder.visited)
                .containsExactly(urn("directive:X"), urn("directive:Y"), urn("directive:Z"), urn("directive:Y"));
    }

    private static void walkFrom(EdgeIndex index, String pack, String start, GraphWalker visitor) {
        new GraphWalk(index).walk(index.nodeAt(new EdgeIndex.Place(pack(pack), urn(start))), visitor);
    }

    /** Records the order components are visited and relationships crossed, and drives the configured descent. */
    private static final class Recorder implements GraphWalker {
        private final List<Urn> visited = new ArrayList<>();
        private final List<Relationship> crossed = new ArrayList<>();
        private Descent nodeDescent = Descent.CONTINUE;
        private Urn skipRelationshipTo;

        @Override
        public Descent onNode(DrgNode node) {
            visited.add(node.urn());
            return nodeDescent;
        }

        @Override
        public Descent onRelationship(Relationship relationship) {
            crossed.add(relationship);
            if (skipRelationshipTo != null && skipRelationshipTo.equals(relationship.to())) {
                return Descent.SKIP;
            }
            return Descent.CONTINUE;
        }
    }
}
