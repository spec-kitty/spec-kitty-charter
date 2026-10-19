package ai.speckitty.charter.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

    /**
     * A digest of the governance this offering answers with: the packs in merge order (pack id, version and level,
     * or the configured name for a pack without a descriptor) and the merged graph, its nodes sorted by URN and its
     * edges sorted, {@code impacts} strengths included. It does not depend on host paths, on how the files are
     * formatted or on the order nodes and edges are declared in.
     *
     * @return {@code sha256:} and 64 lower-case hex digits
     */
    public String governanceFingerprint() {
        Fingerprint fingerprint = Fingerprint.sha256();
        for (CharterPack pack : packs) {
            fingerprint
                    .add("pack")
                    .add(pack.level().token())
                    .add(pack.descriptor().map(PackDescriptor::packId).orElse(pack.name()))
                    .add(optional(pack.descriptor().map(PackDescriptor::packVersion)));
        }
        DoctrineGraph graph = mergedGraph();
        graph.nodes().stream()
                .sorted(Comparator.comparing(DrgNode::urn))
                .forEach(node ->
                        fingerprint.add("node").add(node.urn().toString()).add(optional(node.label())));
        graph.edges().stream()
                .map(CharterOffering::canonical)
                .sorted()
                .forEach(edge -> fingerprint.add("edge").add(edge));
        return fingerprint.value();
    }

    /** One edge as one string, each field length-prefixed so the sort order is the only thing it decides. */
    private static String canonical(DrgEdge edge) {
        StringBuilder out = new StringBuilder();
        for (String field : List.of(
                edge.source().toString(),
                edge.relation().token(),
                edge.target().toString(),
                // 0.0 and -0.0 are the same strength.
                optional(edge.impacts().map(s -> Double.toString(s == 0 ? 0.0 : s))),
                optional(edge.when()),
                optional(edge.reason()))) {
            out.append(field.length()).append(':').append(field);
        }
        return out.toString();
    }

    /** An optional field, so that an absent value and an empty one differ. */
    private static String optional(Optional<String> value) {
        return value.map(v -> "+" + v).orElse("-");
    }
}
