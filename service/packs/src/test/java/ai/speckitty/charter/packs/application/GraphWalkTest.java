package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NFR-001 / FR-004: the walk is lazy and bounded. It resolves each neighbour on demand through the
 * {@link ComponentResolver} port, visits every reachable component once, guards against cycles, continues past an
 * unwired kind (D6), and honours a visitor's {@link Descent}. A counting resolver and a recording visitor give every
 * scenario a same-fixture positive control, so no assertion passes vacuously.
 */
@DisplayName("Graph walk")
class GraphWalkTest {

    @Test
    @DisplayName("a chain A->B->C visits every component once and resolves each distinct neighbour exactly once")
    void chainVisitsEachComponentOnceWithOneResolvePerNeighbour() {
        Urn a = urn("a");
        Urn b = urn("b");
        Urn c = urn("c");
        Node nodeA = new Node(a, List.of(Relationship.of(a, b, RelationType.REQUIRES)));
        Node nodeB = new Node(b, List.of(Relationship.of(b, c, RelationType.REQUIRES)));
        Node nodeC = new Node(c, List.of());
        // An unreachable component sits in the resolver's map but must never be fetched (bounded reads).
        Urn unreachable = urn("z");
        CountingResolver resolver = resolverOf(nodeB, nodeC, new Node(unreachable, List.of()));
        Recorder recorder = new Recorder();

        new GraphWalk(resolver).walk(nodeA, recorder);

        assertThat(recorder.visited).containsExactly(a, b, c);
        // exactly one resolve per distinct reachable neighbour (B and C); A was given, not resolved
        assertThat(resolver.requested).containsExactly(b, c);
        assertThat(resolver.requested).doesNotContain(unreachable);
        assertThat(resolver.requested).hasSizeLessThan(resolver.mapSize());
    }

    @Test
    @DisplayName("a cycle A->B->A terminates and resolves each neighbour at most once")
    void cycleTerminates() {
        Urn a = urn("a");
        Urn b = urn("b");
        Node nodeA = new Node(a, List.of(Relationship.of(a, b, RelationType.REQUIRES)));
        Node nodeB = new Node(b, List.of(Relationship.of(b, a, RelationType.REQUIRES)));
        CountingResolver resolver = resolverOf(nodeA, nodeB);
        Recorder recorder = new Recorder();

        new GraphWalk(resolver).walk(nodeA, recorder);

        assertThat(recorder.visited).containsExactly(a, b);
        // B is resolved once; A is never resolved (it is the start and already visited when the cycle closes)
        assertThat(resolver.requested).containsExactly(b);
    }

    @Test
    @DisplayName("a bidirectional edge authored from the far side is crossed to that far side")
    void bidirectionalEdgeAuthoredFromTheFarSideIsCrossedBackwards() {
        Urn a = urn("a");
        Urn b = urn("b");
        // B authored "B impacts A"; impacts is bidirectional, so A is an effective origin of it and the walk from A
        // must reach B, not turn back to A.
        Relationship impacts = Relationship.of(b, a, RelationType.IMPACTS);
        Node nodeA = new Node(a, List.of(impacts));
        Node nodeB = new Node(b, List.of(impacts));
        CountingResolver resolver = resolverOf(nodeA, nodeB);
        Recorder recorder = new Recorder();

        new GraphWalk(resolver).walk(nodeA, recorder);

        assertThat(recorder.visited).containsExactly(a, b);
        assertThat(resolver.requested).containsExactly(b);
    }

    @Test
    @DisplayName("a relationship to an unwired kind resolves empty and the walk continues to its other neighbours")
    void unwiredNeighbourIsSkippedAndTheWalkContinues() {
        Urn a = urn("a");
        Urn wired = urn("wired");
        Urn unwired = new Urn(NodeKind.PROCEDURE, "p-1");
        Node nodeA = new Node(
                a,
                List.of(
                        Relationship.of(a, unwired, RelationType.REQUIRES),
                        Relationship.of(a, wired, RelationType.SUGGESTS)));
        Node nodeWired = new Node(wired, List.of());
        // the resolver has no entry for the unwired kind, so it returns empty for it (D6)
        CountingResolver resolver = resolverOf(nodeWired);
        Recorder recorder = new Recorder();

        new GraphWalk(resolver).walk(nodeA, recorder);

        assertThat(resolver.requested).containsExactly(unwired, wired);
        assertThat(recorder.visited).containsExactly(a, wired);
    }

    @Test
    @DisplayName("Descent.STOP from a node halts the whole walk before any neighbour is resolved")
    void stopHaltsTheWalk() {
        Urn a = urn("a");
        Urn b = urn("b");
        Node nodeA = new Node(a, List.of(Relationship.of(a, b, RelationType.REQUIRES)));
        CountingResolver resolver = resolverOf(new Node(b, List.of()));
        Recorder recorder = new Recorder();
        recorder.nodeDescent = Descent.STOP;

        new GraphWalk(resolver).walk(nodeA, recorder);

        assertThat(recorder.visited).containsExactly(a);
        assertThat(resolver.requested).isEmpty();
    }

    @Test
    @DisplayName("Descent.SKIP on a node does not descend into its relationships but does not stop the walk")
    void skipDoesNotDescend() {
        Urn a = urn("a");
        Urn b = urn("b");
        Node nodeA = new Node(a, List.of(Relationship.of(a, b, RelationType.REQUIRES)));
        CountingResolver resolver = resolverOf(new Node(b, List.of()));
        Recorder recorder = new Recorder();
        recorder.nodeDescent = Descent.SKIP;

        new GraphWalk(resolver).walk(nodeA, recorder);

        assertThat(recorder.visited).containsExactly(a);
        // SKIP means we never cross any of A's relationships, so no neighbour is resolved
        assertThat(resolver.requested).isEmpty();
        assertThat(recorder.crossed).isEmpty();
    }

    @Test
    @DisplayName("Descent.SKIP on a relationship skips that neighbour but keeps walking the siblings")
    void skipOnRelationshipKeepsSiblings() {
        Urn a = urn("a");
        Urn skipped = urn("skipped");
        Urn kept = urn("kept");
        Node nodeA = new Node(
                a,
                List.of(
                        Relationship.of(a, skipped, RelationType.REQUIRES),
                        Relationship.of(a, kept, RelationType.SUGGESTS)));
        CountingResolver resolver = resolverOf(new Node(skipped, List.of()), new Node(kept, List.of()));
        Recorder recorder = new Recorder();
        recorder.skipRelationshipTo = skipped;

        new GraphWalk(resolver).walk(nodeA, recorder);

        assertThat(recorder.visited).containsExactly(a, kept);
        assertThat(resolver.requested).containsExactly(kept);
    }

    private static Urn urn(String id) {
        return new Urn(NodeKind.DIRECTIVE, id);
    }

    private static CountingResolver resolverOf(Node... nodes) {
        Map<Urn, CharterArtifact> map = new HashMap<>();
        for (Node node : nodes) {
            map.put(node.urn(), node);
        }
        return new CountingResolver(map);
    }

    /** A resolver that records every URN it is asked for and returns empty for anything it does not hold (D6). */
    private static final class CountingResolver implements ComponentResolver {
        private final Map<Urn, CharterArtifact> store;
        private final List<Urn> requested = new ArrayList<>();

        CountingResolver(Map<Urn, CharterArtifact> store) {
            this.store = store;
        }

        @Override
        public Optional<CharterArtifact> resolve(Urn urn) {
            requested.add(urn);
            return Optional.ofNullable(store.get(urn));
        }

        int mapSize() {
            return store.size();
        }
    }

    /** Records the order components are visited and relationships crossed, and drives the configured descent. */
    private static final class Recorder implements ai.speckitty.charter.kernel.domain.GraphWalker {
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

    /** A controllable {@link CharterArtifact} whose {@code urn()} and {@code relationships()} are given outright. */
    private static final class Node implements CharterArtifact {
        private final Urn urn;
        private final List<Relationship> relationships;

        Node(Urn urn, List<Relationship> relationships) {
            this.urn = urn;
            this.relationships = List.copyOf(relationships);
        }

        @Override
        public ArtifactKind kind() {
            return urn.kind().artifactKind().orElseThrow();
        }

        @Override
        public PackIdentifier packId() {
            return new PackIdentifier("01TESTPACK");
        }

        @Override
        public String localId() {
            return urn.id();
        }

        @Override
        public String schemaVersion() {
            return "1.0";
        }

        @Override
        public String label() {
            return urn.id();
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }

        @Override
        public List<Relationship> relationships() {
            return relationships;
        }

        @Override
        public Urn urn() {
            return urn;
        }
    }
}
