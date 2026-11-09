package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.LayerOverride;
import ai.speckitty.charter.packs.domain.MergedClosure;
import java.util.List;
import java.util.function.Predicate;

/** Stub: the red test of T134 fails on its assertions. */
public record GraphProjection(List<Urn> nodes, List<Edge> edges, List<LayerOverride> layerOverrides) {

    /** An edge. */
    public record Edge(Urn source, RelationType relation, Urn target) {}

    static GraphProjection of(EdgeIndex merged, MergedClosure closure, Predicate<Urn> effective) {
        return new GraphProjection(List.of(), List.of(), List.of());
    }
}
