package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One typed edge of the Doctrine Reference Graph.
 *
 * @param source the node the edge starts from
 * @param target the node the edge points to
 * @param relation what the edge means
 * @param impacts the signed strength of an {@link Relation#IMPACTS} edge, from -1 (sabotages) to +1 (reinforces);
 *     empty for every other relation
 * @param when the edge's condition, as authored; empty when it has none
 * @param reason why the edge exists, as authored; empty when it has none
 */
public record DrgEdge(
        Urn source,
        Urn target,
        Relation relation,
        Optional<Double> impacts,
        Optional<String> when,
        Optional<String> reason) {
    /**
     * Checks that every field is present, the optional ones possibly empty but never {@code null}, and that an
     * edge carries a strength exactly when its relation is {@link Relation#IMPACTS}.
     *
     * @param source the node the edge starts from
     * @param target the node the edge points to
     * @param relation what the edge means
     * @param impacts the strength of an {@code impacts} edge
     * @param when the edge's condition
     * @param reason why the edge exists
     * @throws IllegalArgumentException when an {@code impacts} edge has no strength or one outside [-1, 1], or
     *     another relation has one
     */
    public DrgEdge {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(impacts, "impacts");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
        if (relation == Relation.IMPACTS) {
            double strength =
                    impacts.orElseThrow(() -> new IllegalArgumentException("an impacts edge needs a strength"));
            if (!isStrength(strength)) {
                throw new IllegalArgumentException("impacts strength " + strength + " is outside [-1, 1]");
            }
        } else if (impacts.isPresent()) {
            throw new IllegalArgumentException("a " + relation.token() + " edge has no impacts strength");
        }
    }

    /**
     * Whether {@code value} is a valid {@code impacts} strength.
     *
     * @param value the strength
     * @return {@code true} when it lies in [-1, 1]; {@code false} for anything else, NaN included
     */
    public static boolean isStrength(double value) {
        return value >= -1.0 && value <= 1.0;
    }

    /**
     * An edge without a condition or a reason.
     *
     * @param source the node the edge starts from
     * @param target the node the edge points to
     * @param relation what the edge means
     * @return the edge
     */
    public static DrgEdge of(Urn source, Urn target, Relation relation) {
        return new DrgEdge(source, target, relation, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * An {@code impacts} edge without a condition or a reason.
     *
     * @param source the node whose effect it records
     * @param target the node it affects
     * @param strength the signed strength, from -1 to +1
     * @return the edge
     * @throws IllegalArgumentException when {@code strength} is outside [-1, 1]
     */
    public static DrgEdge impacts(Urn source, Urn target, double strength) {
        return new DrgEdge(source, target, Relation.IMPACTS, Optional.of(strength), Optional.empty(), Optional.empty());
    }
}
