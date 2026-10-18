package ai.speckitty.charter.domain;

import java.util.Comparator;
import java.util.List;

/** The packs a project draws on, in layer order, and the graph they merge into. */
public record CharterOffering(List<CharterPack> packs) {

    public CharterOffering {
        // Stable sort: configuration order is kept within a level.
        packs = packs.stream().sorted(Comparator.comparing(CharterPack::level)).toList();
    }

    public DoctrineGraph mergedGraph() {
        return packs.stream().map(CharterPack::graph).reduce(DoctrineGraph.empty(), DoctrineGraph::merge);
    }
}
