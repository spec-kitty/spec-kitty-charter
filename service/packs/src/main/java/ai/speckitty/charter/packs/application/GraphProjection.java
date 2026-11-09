package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.LayerOverride;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.StepHolding;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The effective graph over the merged index (FR-018, rule E15): the nodes in force, the merged edges whose endpoints are
 * both nodes, and the layer overrides of those nodes. An edge is dropped with either endpoint.
 *
 * <p>The nodes are the artifacts the predicate holds in force, plus each step the closure holds ({@link StepHolding},
 * closure form) whose mission type the predicate holds in force: a step is the one node with a defined state that is no
 * artifact of an activatable kind (decision {@code 01M7037ZNKJ6JTRKGD7G695ZV1}). The layer overrides follow the same
 * nodes. Every list has an order of its own, so no answer depends on the order of the closure, the iteration or the
 * writing of an edge: nodes by URN; edges by (source, relation, target); overrides by (artifact, losing level, losing
 * pack).
 *
 * @param nodes the nodes, in URN order
 * @param edges the edges among the nodes, each once, in (source, relation, target) order
 * @param layerOverrides one record per losing version of a node, in (artifact, losing level, losing pack) order
 */
public record GraphProjection(List<Urn> nodes, List<Edge> edges, List<LayerOverride> layerOverrides) {

    private static final Comparator<Edge> EDGE_ORDER = Comparator.comparing(Edge::source)
            .thenComparing(edge -> edge.relation().token())
            .thenComparing(Edge::target);

    private static final Comparator<LayerOverride> OVERRIDE_ORDER = Comparator.comparing(LayerOverride::urn)
            .thenComparing(replaced -> replaced.loser().level())
            .thenComparing(replaced -> replaced.loser().packId().value());

    /**
     * An edge of the graph.
     *
     * @param source the edge's source node
     * @param relation the edge's relation
     * @param target the edge's target node
     */
    public record Edge(Urn source, RelationType relation, Urn target) {

        /**
         * Checks the fields.
         *
         * @param source the source node
         * @param relation the relation
         * @param target the target node
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
    public GraphProjection {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        layerOverrides = List.copyOf(Objects.requireNonNull(layerOverrides, "layerOverrides"));
    }

    /**
     * Projects the merged edges onto the nodes in force.
     *
     * @param merged the closure's merged index
     * @param closure the layer merge the index was built from
     * @param effective whether an artifact is in force: the effective set, which holds no step
     * @return the graph
     */
    static GraphProjection of(EdgeIndex merged, MergedClosure closure, Predicate<Urn> effective) {
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(effective, "effective");
        Set<Urn> nodes = new TreeSet<>();
        closure.winners().keySet().stream().filter(effective).forEach(nodes::add);
        merged.heldSteps().stream()
                .filter(closure.winners()::containsKey)
                .filter(step ->
                        StepHolding.missionTypeOf(step).filter(effective).isPresent())
                .forEach(nodes::add);
        List<Edge> edges = merged.links().stream()
                .filter(link -> nodes.contains(link.source()) && nodes.contains(link.target()))
                .map(link -> new Edge(link.source(), link.relation(), link.target()))
                .distinct()
                .sorted(EDGE_ORDER)
                .toList();
        List<LayerOverride> overrides = closure.layerOverrides().stream()
                .filter(replaced -> nodes.contains(replaced.urn()))
                .sorted(OVERRIDE_ORDER)
                .toList();
        return new GraphProjection(List.copyOf(nodes), edges, overrides);
    }
}
