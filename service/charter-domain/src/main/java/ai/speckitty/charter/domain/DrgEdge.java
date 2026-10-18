package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/** One typed edge of the Doctrine Reference Graph. */
public record DrgEdge(Urn source, Urn target, Relation relation, Optional<String> when, Optional<String> reason) {
    public DrgEdge {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
    }

    public static DrgEdge of(Urn source, Urn target, Relation relation) {
        return new DrgEdge(source, target, relation, Optional.empty(), Optional.empty());
    }
}
