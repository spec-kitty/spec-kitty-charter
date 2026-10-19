package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One typed edge of the Doctrine Reference Graph.
 *
 * @param source the node the edge starts from
 * @param target the node the edge points to
 * @param relation what the edge means
 * @param when the edge's condition, as authored; empty when it has none
 * @param reason why the edge exists, as authored; empty when it has none
 */
public record DrgEdge(Urn source, Urn target, Relation relation, Optional<String> when, Optional<String> reason) {
    /**
     * Checks that every field is present; the optional ones may be empty but never {@code null}.
     *
     * @param source the node the edge starts from
     * @param target the node the edge points to
     * @param relation what the edge means
     * @param when the edge's condition
     * @param reason why the edge exists
     */
    public DrgEdge {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
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
        return new DrgEdge(source, target, relation, Optional.empty(), Optional.empty());
    }
}
