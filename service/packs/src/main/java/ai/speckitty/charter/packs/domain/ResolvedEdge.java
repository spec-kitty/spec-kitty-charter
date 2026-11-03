package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * One dependency edge the compose resolved (FR-020): the depending pack, the pack it needs, the range its descriptor
 * declared and the version that satisfied it. Derived from the closure the resolver already ordered — never authored,
 * so no write path carries it (data-model.md: {@code ResolvedEdge}).
 *
 * @param from the depending pack's {@code pack_id}
 * @param to the depended-on pack's {@code pack_id}
 * @param range the PEP 440 range the descriptor declared
 * @param version the held {@code pack_version} that satisfied the range
 */
public record ResolvedEdge(String from, String to, String range, String version) {

    /**
     * A resolved edge.
     *
     * @param from the depending pack id
     * @param to the depended-on pack id
     * @param range the declared range
     * @param version the satisfying version
     */
    public ResolvedEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(version, "version");
    }
}
