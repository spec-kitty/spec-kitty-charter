package ai.speckitty.charter.domain;

import java.util.Comparator;
import java.util.List;

/**
 * The packs a project draws on, in layer order, and the graph they merge into.
 *
 * @param packs the loaded packs, sorted by level (built-in, org, project) and in configuration order within a level
 */
public record CharterOffering(List<CharterPack> packs) {

    /**
     * Sorts {@code packs} into layer order.
     *
     * @param packs the loaded packs, in configuration order
     */
    public CharterOffering {
        // Stable sort: configuration order is kept within a level.
        packs = packs.stream().sorted(Comparator.comparing(CharterPack::level)).toList();
    }

    /**
     * The packs' graphs merged in layer order.
     *
     * @return the union of every pack's graph; empty when there are no packs
     */
    public DoctrineGraph mergedGraph() {
        return packs.stream().map(CharterPack::graph).reduce(DoctrineGraph.empty(), DoctrineGraph::merge);
    }
}
