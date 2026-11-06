package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;

/**
 * The port through which a pack's relationships are read, whatever the storage: the edges of its graph documents, the
 * only place a relationship is authored (ADR-0064).
 */
@FunctionalInterface
public interface PackEdgeRepository {

    /**
     * The edges of one pack's graph documents: every root {@code *.graph.yaml} shard in file-name order (the registered
     * shard among them), then {@code drg/fragment.yaml}, each in document order.
     *
     * @param pack the pack's identity
     * @return the edges; empty for a pack with no graph document
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when a graph document of the
     *     pack cannot be read, or an edge names a relation the service does not know
     */
    List<PackEdge> edges(PackIdentifier pack);
}
