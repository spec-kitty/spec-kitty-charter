package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the application asks the packs slice to walk for one cascade: the policy half of a reach (ADR-0059 D2). A plan
 * with no relations asks for no walk: the application skips the call and passes {@link Reach#empty()}.
 *
 * @param starts the artifacts the walk starts from
 * @param relations the forward relations the walk crosses
 * @param stopAt the artifacts the walk stops at and reports
 * @param lineage whether the {@code specializes_from} lineage passes {@code requires} and {@code scope} through
 * @param suggestsBound the number of {@code suggests} hops the walk may cross (IC-21, FR-043); empty for an unlimited
 *     walk. Every other relation is walked in full
 */
public record ReachPlan(
        Set<ArtifactRef> starts,
        Set<RelationType> relations,
        Set<ArtifactRef> stopAt,
        boolean lineage,
        OptionalInt suggestsBound) {

    /**
     * Checks and copies the fields.
     *
     * @param starts the starts
     * @param relations the relations
     * @param stopAt the stops
     * @param lineage whether the lineage passes through
     * @param suggestsBound the {@code suggests} hops the walk may cross; empty for no bound
     * @throws IllegalArgumentException when the bound is negative
     */
    public ReachPlan {
        starts = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(starts, "starts")));
        relations = Collections.unmodifiableSet(
                Objects.requireNonNull(relations, "relations").isEmpty()
                        ? EnumSet.noneOf(RelationType.class)
                        : EnumSet.copyOf(relations));
        stopAt = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(stopAt, "stopAt")));
        if (Objects.requireNonNull(suggestsBound, "suggestsBound").isPresent() && suggestsBound.getAsInt() < 0) {
            throw new IllegalArgumentException(
                    "A walk crosses no fewer than 0 suggests hops, not " + suggestsBound.getAsInt());
        }
    }

    /**
     * A plan with no bound on the {@code suggests} hops.
     *
     * @param starts the starts
     * @param relations the relations crossed
     * @param stopAt the stops
     * @param lineage whether the lineage passes through
     */
    public ReachPlan(Set<ArtifactRef> starts, Set<RelationType> relations, Set<ArtifactRef> stopAt, boolean lineage) {
        this(starts, relations, stopAt, lineage, OptionalInt.empty());
    }

    /**
     * The plan of a write without a cascade: no walk.
     *
     * @param target the artifact the write names
     * @return the plan, with no relations
     */
    public static ReachPlan none(ArtifactRef target) {
        return new ReachPlan(Set.of(target), Set.of(), Set.of(), false);
    }

    /**
     * Whether the plan asks for a walk at all.
     *
     * @return {@code false} when the plan has no relations
     */
    public boolean walks() {
        return !relations.isEmpty();
    }
}
