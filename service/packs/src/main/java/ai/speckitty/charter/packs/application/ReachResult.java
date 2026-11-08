package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What one {@link Reachability#reach reach} found (ADR-0059 D2). Every map keeps every predecessor, not only the
 * first: a later artifact that also reaches a node is never dropped (ADR-0059 D2.2 reporting).
 *
 * @param reached each node the walk put in force, with every {@link Via} it was reached by; a start has none of its own
 * @param stopped each node the stop predicate matched, with every {@link Via} that led to it
 * @param behindStopped the nodes the walk would have reached through a stopped node and reached no other way. It
 *     lists only what the unstopped walk would put in force: a lineage pass-through target behind a stop is listed, but
 *     an ancestor that would only be passed through (never in force) is not, even when it lies behind a stop
 * @param ancestorsPassed the lineage ancestors walked through; an ancestor is never put in force by the lineage
 * @param ancestry each ancestor passed, with every {@link Via} it was passed by: the child that writes
 *     {@code specializes_from} onto it. A pass-through {@link Via} names the ancestor as its predecessor; this leads
 *     from the ancestor back to the descendant the walk reached
 * @param hops each reached node's minimum number of {@code suggests} hops from a start (IC-21): 0 for a start and for
 *     what the hard relations reach from it, and a place the lineage or a mission type's steps produced has the count of
 *     the place it came from. A node reached only through {@code suggests} has more than 0
 * @param beyondBound the nodes one {@code suggests} hop past the request's bound that the walk reached no other way,
 *     neither in force nor stopped; empty for an unbounded walk. The bound is checked before a node is entered, so
 *     these have no {@link Via}
 */
public record ReachResult(
        Map<Urn, List<Via>> reached,
        Map<Urn, List<Via>> stopped,
        Set<Urn> behindStopped,
        Set<Urn> ancestorsPassed,
        Map<Urn, List<Via>> ancestry,
        Map<Urn, Integer> hops,
        Set<Urn> beyondBound) {

    /**
     * How a node was reached: the node before it and the relation crossed.
     *
     * @param predecessor the node that writes the relation (for a lineage pass-through, the ancestor)
     * @param relation the relation crossed
     */
    public record Via(Urn predecessor, RelationType relation) {

        /**
         * Checks the fields.
         *
         * @param predecessor the predecessor
         * @param relation the relation
         */
        public Via {
            Objects.requireNonNull(predecessor, "predecessor");
            Objects.requireNonNull(relation, "relation");
        }
    }

    /**
     * Checks and copies the fields, keeping their order.
     *
     * @param reached the reached nodes
     * @param stopped the stopped nodes
     * @param behindStopped the nodes behind a stop
     * @param ancestorsPassed the ancestors passed through
     * @param ancestry the ways each ancestor was passed by
     * @param hops the minimum {@code suggests} hops of each reached node
     * @param beyondBound the nodes past the {@code suggests} bound
     */
    public ReachResult {
        reached = copyOf(Objects.requireNonNull(reached, "reached"));
        stopped = copyOf(Objects.requireNonNull(stopped, "stopped"));
        behindStopped = Set.copyOf(Objects.requireNonNull(behindStopped, "behindStopped"));
        ancestorsPassed = Set.copyOf(Objects.requireNonNull(ancestorsPassed, "ancestorsPassed"));
        ancestry = copyOf(Objects.requireNonNull(ancestry, "ancestry"));
        hops = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(hops, "hops")));
        beyondBound = Set.copyOf(Objects.requireNonNull(beyondBound, "beyondBound"));
    }

    /**
     * A result that records no hop counts and no bound.
     *
     * @param reached the reached nodes
     * @param stopped the stopped nodes
     * @param behindStopped the nodes behind a stop
     * @param ancestorsPassed the ancestors passed through
     * @param ancestry the ways each ancestor was passed by
     */
    public ReachResult(
            Map<Urn, List<Via>> reached,
            Map<Urn, List<Via>> stopped,
            Set<Urn> behindStopped,
            Set<Urn> ancestorsPassed,
            Map<Urn, List<Via>> ancestry) {
        this(reached, stopped, behindStopped, ancestorsPassed, ancestry, Map.of(), Set.of());
    }

    private static Map<Urn, List<Via>> copyOf(Map<Urn, List<Via>> vias) {
        Map<Urn, List<Via>> copy = new LinkedHashMap<>();
        vias.forEach((urn, list) -> copy.put(urn, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }
}
