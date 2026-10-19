package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.PackLevel;
import java.util.List;
import java.util.Optional;

/**
 * One loaded pack and the size of its graph.
 *
 * @param name the name the pack is configured under
 * @param level the level the pack is configured at
 * @param packId the descriptor's {@code pack_id}; empty when the pack has no {@code pack.yaml}
 * @param packVersion the descriptor's {@code pack_version}; empty when the pack has no {@code pack.yaml}
 * @param parentPack the descriptor's {@code parent_pack}; empty for a root pack or a pack without {@code pack.yaml}
 * @param nodeCount the number of nodes in the pack's own graph
 * @param edgeCount the number of edges in the pack's own graph
 * @param diagnostics the load findings of the pack; empty when there are none
 */
public record PackSummary(
        String name,
        PackLevel level,
        Optional<String> packId,
        Optional<String> packVersion,
        Optional<String> parentPack,
        int nodeCount,
        int edgeCount,
        List<Diagnostic> diagnostics) {}
