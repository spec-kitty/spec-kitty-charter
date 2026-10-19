package ai.speckitty.charter.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An ordered DRG: nodes keyed by URN in first-declared order, edges in declared order.
 * Merging appends; a node declared twice keeps its first declaration (layer semantics beyond
 * this union arrive with the conformance corpus, roadmap step 5).
 */
public final class DoctrineGraph {

    private final Map<Urn, DrgNode> nodes;
    private final List<DrgEdge> edges;

    /**
     * A graph of {@code nodes} and {@code edges}; a node whose URN was already declared is dropped.
     *
     * @param nodes the nodes, in declaration order
     * @param edges the edges, in declaration order; endpoints need not be nodes of this graph
     */
    public DoctrineGraph(Collection<DrgNode> nodes, Collection<DrgEdge> edges) {
        Map<Urn, DrgNode> byUrn = new LinkedHashMap<>();
        nodes.forEach(n -> byUrn.putIfAbsent(n.urn(), n));
        this.nodes = java.util.Collections.unmodifiableMap(byUrn);
        this.edges = List.copyOf(edges);
    }

    /**
     * The graph with no nodes and no edges, the identity of {@link #merge(DoctrineGraph)}.
     *
     * @return an empty graph
     */
    public static DoctrineGraph empty() {
        return new DoctrineGraph(List.of(), List.of());
    }

    /**
     * The nodes, in first-declared order.
     *
     * @return an unmodifiable view of the nodes
     */
    public Collection<DrgNode> nodes() {
        return nodes.values();
    }

    /**
     * The edges, in declared order.
     *
     * @return an unmodifiable list of the edges
     */
    public List<DrgEdge> edges() {
        return edges;
    }

    /**
     * The node of a URN.
     *
     * @param urn the node's identity
     * @return the node; empty when the graph has no node with that URN
     */
    public Optional<DrgNode> node(Urn urn) {
        return Optional.ofNullable(nodes.get(urn));
    }

    /**
     * Whether the graph has a node with this URN.
     *
     * @param urn the node's identity
     * @return {@code true} when a node with that URN was declared
     */
    public boolean contains(Urn urn) {
        return nodes.containsKey(urn);
    }

    /**
     * The edges that start at {@code source} with {@code relation}.
     *
     * @param source the node the edges start from
     * @param relation the relation of the edges
     * @return the matching edges, in declared order; empty when there are none
     */
    public List<DrgEdge> outgoing(Urn source, Relation relation) {
        return edges.stream()
                .filter(e -> e.source().equals(source) && e.relation() == relation)
                .toList();
    }

    /**
     * This graph followed by {@code other}: its nodes and edges are appended, and a node of {@code other} whose URN
     * this graph already declares is dropped.
     *
     * @param other the graph of the next layer
     * @return the merged graph; neither input changes
     */
    public DoctrineGraph merge(DoctrineGraph other) {
        List<DrgNode> mergedNodes = new ArrayList<>(nodes.values());
        mergedNodes.addAll(other.nodes());
        List<DrgEdge> mergedEdges = new ArrayList<>(edges);
        mergedEdges.addAll(other.edges());
        return new DoctrineGraph(mergedNodes, mergedEdges);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof DoctrineGraph g
                && List.copyOf(nodes.values()).equals(List.copyOf(g.nodes.values()))
                && edges.equals(g.edges);
    }

    @Override
    public int hashCode() {
        return 31 * List.copyOf(nodes.values()).hashCode() + edges.hashCode();
    }
}
