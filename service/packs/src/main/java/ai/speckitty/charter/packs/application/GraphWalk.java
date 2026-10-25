package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The lazy, repository-backed DRG walk driver (ADR-0025 Decision 4). It visits a component's effective-origin
 * {@link CharterArtifact#relationships() relationships}, resolving each neighbour on demand through the
 * {@link ComponentResolver} port, with a visited-URN cycle guard. It never materialises the whole graph: it issues at
 * most one resolver call per distinct reachable component (NFR-001).
 *
 * <p>It lives in the application ring, where the ports exist (ADR-0006). It is plain orchestration over the resolver
 * and a {@link GraphWalker} visitor — no IO, no framework; the composition root constructs it.
 */
public final class GraphWalk {

    private final ComponentResolver resolver;

    /**
     * Creates a walk driver.
     *
     * @param resolver the port that resolves a neighbour URN to its component on demand
     */
    public GraphWalk(ComponentResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Walks the DRG from {@code start}, visiting each reachable component once.
     *
     * <p>At each component the driver calls {@link GraphWalker#onNode} and obeys the returned {@link Descent}:
     * {@link Descent#STOP} halts the whole walk, {@link Descent#SKIP} leaves the component's relationships uncrossed
     * (so no neighbour of it is resolved), {@link Descent#CONTINUE} descends. For each relationship it then calls
     * {@link GraphWalker#onRelationship} and again obeys the {@link Descent}: {@code STOP} halts, {@code SKIP} leaves
     * that one relationship uncrossed, {@code CONTINUE} resolves the relationship's {@code to} and, when the resolver
     * yields a not-yet-visited component, recurses into it. A relationship whose neighbour is already visited (the
     * cycle guard) or resolves empty (an unwired kind, research D6) is passed over and the walk continues.
     *
     * @param start the component to start from; it is visited but never resolved
     * @param visitor the visitor notified of every node and relationship
     */
    public void walk(CharterArtifact start, GraphWalker visitor) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(visitor, "visitor");
        visit(start, visitor, new HashSet<>());
    }

    /**
     * Visits one component and, when told to descend, its neighbours.
     *
     * @return {@code true} to keep the overall walk going, {@code false} once a {@link Descent#STOP} has halted it
     */
    private boolean visit(CharterArtifact component, GraphWalker visitor, Set<Urn> visited) {
        visited.add(component.urn());
        Descent nodeDescent = visitor.onNode(new DrgNode(component.urn(), Optional.of(component.label())));
        if (nodeDescent == Descent.STOP) {
            return false;
        }
        if (nodeDescent == Descent.SKIP) {
            return true;
        }
        for (Relationship relationship : component.relationships()) {
            Descent relationshipDescent = visitor.onRelationship(relationship);
            if (relationshipDescent == Descent.STOP) {
                return false;
            }
            if (relationshipDescent == Descent.SKIP) {
                continue;
            }
            Urn neighbour = relationship.to();
            if (visited.contains(neighbour)) {
                continue;
            }
            Optional<CharterArtifact> resolved = resolver.resolve(neighbour);
            if (resolved.isEmpty()) {
                continue;
            }
            if (!visit(resolved.get(), visitor, visited)) {
                return false;
            }
        }
        return true;
    }
}
