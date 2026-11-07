package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What one {@link Reachability#reach reach} walks (ADR-0059 D2): the mechanism only. Which relations, which stops
 * and whether the lineage passes through are the caller's policy.
 *
 * @param starts the artifacts the walk starts from, a mission type included; each is walked in every closure pack that
 *     holds it
 * @param relations the forward relations the walk crosses; every other relation is left uncrossed. With
 *     {@code requires}, a reached mission type also reaches its steps ({@link Reachability#reach}, A7 Q1)
 * @param stopAt the nodes the walk does not descend into (the caller's deny set): they are reported, never reached
 * @param lineage the lineage pass-through, when the walk follows one
 * @param suggestsBound the number of {@code suggests} hops the walk may cross (IC-21, FR-043); empty for no bound. Only
 *     {@code suggests} is counted: every other relation, the lineage pass-through and a mission type's steps are walked
 *     in full, and a place they produce inherits the hop count of the place that produced it
 */
public record ReachRequest(
        Set<Urn> starts,
        Set<RelationType> relations,
        Predicate<Urn> stopAt,
        Optional<Lineage> lineage,
        OptionalInt suggestsBound) {

    /**
     * The lineage pass-through (ADR-0059 D2.4): up the whole {@code ancestry} chain of every reached node, the
     * {@code passThrough} relations of each ancestor are crossed as if the node wrote them. An ancestor is walked
     * through, never put in force.
     *
     * @param ancestry the relation that names a node's parent
     * @param passThrough the relations of an ancestor that pass through to its descendant
     */
    public record Lineage(RelationType ancestry, Set<RelationType> passThrough) {

        /**
         * Checks and copies the fields.
         *
         * @param ancestry the parent relation
         * @param passThrough the relations that pass through
         */
        public Lineage {
            Objects.requireNonNull(ancestry, "ancestry");
            passThrough = Set.copyOf(Objects.requireNonNull(passThrough, "passThrough"));
        }

        /**
         * The lineage of ADR-0059 D2.4: {@code specializes_from}, passing {@code requires} and {@code scope}.
         *
         * @return that lineage
         */
        public static Lineage specializesFrom() {
            return new Lineage(RelationType.SPECIALIZES_FROM, EnumSet.of(RelationType.REQUIRES, RelationType.SCOPE));
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param starts the starts
     * @param relations the relations crossed
     * @param stopAt the stop predicate
     * @param lineage the lineage, when followed
     * @param suggestsBound the {@code suggests} hops the walk may cross; empty for no bound
     * @throws IllegalArgumentException when the bound is negative
     */
    public ReachRequest {
        starts = Set.copyOf(Objects.requireNonNull(starts, "starts"));
        relations = Set.copyOf(Objects.requireNonNull(relations, "relations"));
        Objects.requireNonNull(stopAt, "stopAt");
        Objects.requireNonNull(lineage, "lineage");
        if (Objects.requireNonNull(suggestsBound, "suggestsBound").isPresent() && suggestsBound.getAsInt() < 0) {
            throw new IllegalArgumentException(
                    "A walk crosses no fewer than 0 suggests hops, not " + suggestsBound.getAsInt());
        }
    }

    /**
     * A request with no bound on the {@code suggests} hops.
     *
     * @param starts the starts
     * @param relations the relations crossed
     * @param stopAt the stop predicate
     * @param lineage the lineage, when followed
     */
    public ReachRequest(
            Set<Urn> starts, Set<RelationType> relations, Predicate<Urn> stopAt, Optional<Lineage> lineage) {
        this(starts, relations, stopAt, lineage, OptionalInt.empty());
    }

    /**
     * The same walk with no stop: what lies behind a stop is what this one reaches and the stopped one does not. The
     * bound is kept, so nothing past it counts as behind a stop.
     *
     * @return this request, stopping nowhere
     */
    ReachRequest withoutStops() {
        return new ReachRequest(starts, relations, urn -> false, lineage, suggestsBound);
    }
}
