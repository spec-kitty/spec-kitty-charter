package ai.speckitty.charter.kernel.domain;

import java.util.Objects;

/**
 * One allowed combination of a relation's source and target kinds (ADR-0064 decision 13).
 *
 * @param source the source node kind
 * @param target the target node kind
 */
public record EndpointPair(NodeKind source, NodeKind target) {

    /** Checks both kinds are present. */
    public EndpointPair {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }
}
