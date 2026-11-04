package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.ComposeResolver;
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

    /** A component's place: the pack that holds it and its bare URN. The visited set is keyed by it. */
    private record Held(PackIdentifier pack, Urn urn) {}

    private final ComposeResolver resolvers;

    /**
     * Creates a walk driver over one pack's resolver. A pack-qualified neighbour names a pack this resolver cannot
     * reach, so the walk passes over it, as it does an unwired kind.
     *
     * @param resolver the port that resolves a neighbour URN to its component on demand
     */
    public GraphWalk(ComponentResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        this.resolvers = new ComposeResolver() {
            @Override
            public Optional<PackIdentifier> holdingPack(
                    PackQualifiedReference reference, PackIdentifier referringPack) {
                return Optional.empty();
            }

            @Override
            public ComponentResolver inPack(PackIdentifier pack) {
                return resolver;
            }
        };
    }

    /**
     * Creates a walk driver across the packs of one compose. A bare reference resolves in the pack that holds the
     * artifact writing it, also after a qualified hop into another pack; a qualified reference resolves in the pack the
     * compose's one closure rule gives it ({@link ComposeResolver#holdingPack}), is passed over when the compose holds
     * none, and is refused as the compose refuses it when it names a pack outside the writer's {@code depends_on}
     * closure.
     *
     * <p>Reserved for the effective-graph reads: no read of the service constructs a walk across packs yet.
     *
     * @param resolvers the compose, resolved once for the read the walk serves
     */
    public GraphWalk(ComposeResolver resolvers) {
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
    }

    /**
     * Walks the DRG from {@code start}, visiting each reachable component once.
     *
     * <p>At each component the driver calls {@link GraphWalker#onNode} and obeys the returned {@link Descent}:
     * {@link Descent#STOP} halts the whole walk, {@link Descent#SKIP} leaves the component's relationships uncrossed
     * (so no neighbour of it is resolved), {@link Descent#CONTINUE} descends. For each relationship it then calls
     * {@link GraphWalker#onRelationship} and again obeys the {@link Descent}: {@code STOP} halts, {@code SKIP} leaves
     * that one relationship uncrossed, {@code CONTINUE} resolves the relationship's other endpoint (its {@code to}, or its {@code from} for a
     * bidirectional edge that names this component as {@code to}) and, when the resolver
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
    private boolean visit(CharterArtifact component, GraphWalker visitor, Set<Held> visited) {
        visited.add(new Held(component.packId(), component.urn()));
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
            // An effective-origin edge is either authored from this component or a bidirectional edge that names it
            // as `to`; the neighbour is always the other endpoint. Its reference resolves to (holding pack, bare URN)
            // before anything is compared: a qualified one names its pack, a bare one is in the pack of the component
            // that writes it.
            boolean forward = relationship.from().equals(component.urn());
            Urn neighbour = forward ? relationship.to() : relationship.from();
            Optional<String> qualifier = forward ? relationship.toPack() : relationship.fromPack();
            Optional<PackIdentifier> holder = qualifier.isPresent()
                    ? resolvers.holdingPack(
                            new PackQualifiedReference(qualifier, neighbour.toString()), component.packId())
                    : Optional.of(component.packId());
            if (holder.isEmpty() || visited.contains(new Held(holder.get(), neighbour))) {
                continue;
            }
            Optional<CharterArtifact> resolved = resolvers.inPack(holder.get()).resolve(neighbour);
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
