package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What one walk of the closure found, as a value the cascade rules read (ADR-0059 D2). The packs slice walks; its
 * adapter copies the answer here, so the activation domain names no other slice and holds no traversal.
 *
 * @param reached each artifact the walk put in force, with every {@link Via} it was reached by; a start has none
 * @param stopped each artifact the walk stopped at (a deny entry), with every {@link Via} that led to it
 * @param behindStopped the artifacts the walk would have put in force through a stopped one, and reached no other way
 * @param ancestorsPassed the lineage ancestors walked through, never in force and never reported
 * @param ancestry each ancestor passed, with every {@link Via} it was passed by: the child that specializes from it,
 *     over {@code specializes_from}. It lets a pass-through {@link Via}, whose {@code from} is the ancestor, be followed
 *     back to the descendant that put the ancestor's relation in force
 */
public record Reach(
        Map<ArtifactRef, List<Via>> reached,
        Map<ArtifactRef, List<Via>> stopped,
        Set<ArtifactRef> behindStopped,
        Set<ArtifactRef> ancestorsPassed,
        Map<ArtifactRef, List<Via>> ancestry) {

    /**
     * How an artifact was reached: the artifact before it and the relation crossed.
     *
     * @param from the artifact that writes the relation (for a lineage pass-through, the ancestor; for an ancestor in
     *     {@link Reach#ancestry()}, the child that specializes from it)
     * @param relation the relation crossed
     */
    public record Via(ArtifactRef from, RelationType relation) {

        /**
         * Checks the fields.
         *
         * @param from the predecessor
         * @param relation the relation
         */
        public Via {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(relation, "relation");
        }
    }

    /**
     * Checks and copies the fields, sorted.
     *
     * @param reached the reached artifacts
     * @param stopped the stopped artifacts
     * @param behindStopped the artifacts behind a stop
     * @param ancestorsPassed the ancestors passed through
     * @param ancestry the ways each ancestor was passed by
     */
    public Reach {
        reached = copyOf(Objects.requireNonNull(reached, "reached"));
        stopped = copyOf(Objects.requireNonNull(stopped, "stopped"));
        behindStopped = Collections.unmodifiableSortedSet(
                new TreeSet<>(Objects.requireNonNull(behindStopped, "behindStopped")));
        ancestorsPassed = Collections.unmodifiableSortedSet(
                new TreeSet<>(Objects.requireNonNull(ancestorsPassed, "ancestorsPassed")));
        ancestry = copyOf(Objects.requireNonNull(ancestry, "ancestry"));
    }

    /**
     * The answer of a walk that was not made: nothing reached.
     *
     * @return the empty reach
     */
    public static Reach empty() {
        return new Reach(Map.of(), Map.of(), Set.of(), Set.of(), Map.of());
    }

    private static Map<ArtifactRef, List<Via>> copyOf(Map<ArtifactRef, List<Via>> vias) {
        Map<ArtifactRef, List<Via>> copy = new TreeMap<>();
        vias.forEach((ref, list) -> copy.put(ref, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }
}
