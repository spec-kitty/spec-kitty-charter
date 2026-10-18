package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/** One node of the Doctrine Reference Graph. */
public record DrgNode(Urn urn, Optional<String> label) {
    public DrgNode {
        Objects.requireNonNull(urn, "urn");
        Objects.requireNonNull(label, "label");
    }
}
