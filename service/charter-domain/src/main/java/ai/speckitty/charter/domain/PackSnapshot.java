package ai.speckitty.charter.domain;

import java.util.List;
import java.util.Objects;

/**
 * What a partial-tolerant fetch of a pack found: its descriptor, its readable tactics, and the files it could not
 * read. Unlike {@link CharterPack} it holds no doctrine graph.
 *
 * @param descriptor the pack's descriptor
 * @param tactics the readable tactics, in a stable order; empty for a descriptor-only pack
 * @param violations the files that failed to parse or validate
 */
public record PackSnapshot(PackDescriptor descriptor, List<Tactic> tactics, List<PackElementViolation> violations) {
    /**
     * Checks that the descriptor is present and copies the lists.
     *
     * @param descriptor the pack's descriptor
     * @param tactics the readable tactics
     * @param violations the reported violations
     */
    public PackSnapshot {
        Objects.requireNonNull(descriptor, "descriptor");
        tactics = List.copyOf(tactics);
        violations = List.copyOf(violations);
    }
}
