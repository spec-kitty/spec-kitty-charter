package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One node of the Doctrine Reference Graph.
 *
 * @param urn the node's identity
 * @param label the node's human-readable label; empty when it has none
 */
public record DrgNode(Urn urn, Optional<String> label) {
    /**
     * Checks that both fields are present.
     *
     * @param urn the node's identity
     * @param label the node's label, possibly empty
     */
    public DrgNode {
        Objects.requireNonNull(urn, "urn");
        Objects.requireNonNull(label, "label");
    }
}
