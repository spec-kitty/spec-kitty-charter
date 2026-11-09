package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.List;
import java.util.Objects;

/**
 * The effective graph, as the activations slice reads it (IC-11, FR-018, rule E15): the nodes in force, the merged edges
 * among them and the layer overrides of those nodes. The packs slice projected it over the merged closure; this is the
 * adapter's copy, as values only, and nothing here walks. A node is an artifact in force or a held step of a mission type
 * in force, so it is a {@link NodeRef}, not an {@link ArtifactRef}.
 *
 * <p>Every list keeps the order the projection gave it, which is the contract's: nodes by URN, edges by source, relation
 * and target, layer overrides by artifact, then the loser's level and pack.
 *
 * @param nodes the nodes in force
 * @param edges the edges among the nodes
 * @param layerOverrides one record per losing version of a node
 */
public record GraphSnapshot(List<NodeRef> nodes, List<Edge> edges, List<MergedSnapshot.LayerOverride> layerOverrides) {

    private static final GraphSnapshot EMPTY = new GraphSnapshot(List.of(), List.of(), List.of());

    /**
     * An edge of the graph.
     *
     * @param source the node the edge starts at
     * @param relation what the edge means
     * @param target the node it ends at
     */
    public record Edge(NodeRef source, RelationType relation, NodeRef target) {

        /**
         * Checks the fields.
         *
         * @param source the start
         * @param relation the relation
         * @param target the end
         */
        public Edge {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param nodes the nodes
     * @param edges the edges
     * @param layerOverrides the layer overrides
     */
    public GraphSnapshot {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        layerOverrides = List.copyOf(Objects.requireNonNull(layerOverrides, "layerOverrides"));
    }

    /**
     * The graph of a closure that holds nothing.
     *
     * @return the empty graph
     */
    public static GraphSnapshot empty() {
        return EMPTY;
    }
}
