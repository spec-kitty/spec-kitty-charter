package ai.speckitty.charter.activations.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Follows the ways of one {@link Reach} back, so a report names an artifact an {@code ArtifactReference} can name and
 * the contract means (ArtifactReference.yaml, ADR-0059 D2.4), not whatever node wrote the last relation: a template,
 * an asset, a lineage ancestor or an unselected intermediate leads back to what put it in the walk. A lineage
 * pass-through's {@code from} is the ancestor, whose {@link Reach#ancestry()} leads back to its descendant.
 *
 * <p>The walk back is breadth first, one predecessor level at a time, so the nearest accepted artifact is named; among
 * the nearest, the first in {@link ArtifactRef}'s order (kind, then id in code-point order), so the choice never
 * depends on the order the walk recorded its ways in.
 */
final class Predecessors {

    private final Reach reach;

    /**
     * The ways of a walk.
     *
     * @param reach the walk's answer
     */
    Predecessors(Reach reach) {
        this.reach = reach;
    }

    /**
     * The nearest artifact back from some predecessors that a test accepts.
     *
     * @param from the predecessors the walk back starts at, themselves the first level
     * @param accepted the artifacts that may be named
     * @return the first accepted artifact of the nearest level that holds one; empty when no way leads to one
     */
    Optional<ArtifactRef> nearest(Collection<ArtifactRef> from, Predicate<ArtifactRef> accepted) {
        Set<ArtifactRef> seen = new HashSet<>();
        SortedSet<ArtifactRef> level = new TreeSet<>(from);
        while (!level.isEmpty()) {
            Optional<ArtifactRef> found = level.stream().filter(accepted).findFirst();
            if (found.isPresent()) {
                return found;
            }
            seen.addAll(level);
            level = previous(level, seen);
        }
        return Optional.empty();
    }

    /**
     * Whether an artifact was only passed through as a lineage ancestor: never in force, so never named.
     *
     * @param ref the artifact
     * @return {@code true} for an ancestor the walk did not also reach
     */
    boolean ancestorOnly(ArtifactRef ref) {
        return reach.ancestorsPassed().contains(ref) && !reach.reached().containsKey(ref);
    }

    /** The predecessors of a level not seen yet. */
    private SortedSet<ArtifactRef> previous(Set<ArtifactRef> level, Set<ArtifactRef> seen) {
        SortedSet<ArtifactRef> previous = new TreeSet<>();
        level.forEach(ref -> ways(ref).forEach(via -> previous.add(via.from())));
        previous.removeAll(seen);
        return previous;
    }

    /** The ways an artifact was reached by, and, for an ancestor, the children it was passed from. */
    List<Reach.Via> ways(ArtifactRef ref) {
        return Stream.concat(
                        reach.reached().getOrDefault(ref, List.of()).stream(),
                        reach.ancestry().getOrDefault(ref, List.of()).stream())
                .toList();
    }
}
