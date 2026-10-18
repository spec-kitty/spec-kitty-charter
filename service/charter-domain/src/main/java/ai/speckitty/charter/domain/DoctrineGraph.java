package ai.speckitty.charter.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An ordered DRG: nodes keyed by URN in first-declared order, edges in declared order.
 * Merging appends; a node declared twice keeps its first declaration (layer semantics beyond
 * this union arrive with the conformance corpus, roadmap step 5).
 */
public final class DoctrineGraph {

    private final Map<Urn, DrgNode> nodes;
    private final List<DrgEdge> edges;

    public DoctrineGraph(Collection<DrgNode> nodes, Collection<DrgEdge> edges) {
        Map<Urn, DrgNode> byUrn = new LinkedHashMap<>();
        nodes.forEach(n -> byUrn.putIfAbsent(n.urn(), n));
        this.nodes = java.util.Collections.unmodifiableMap(byUrn);
        this.edges = List.copyOf(edges);
    }

    public static DoctrineGraph empty() { return new DoctrineGraph(List.of(), List.of()); }

    public Collection<DrgNode> nodes() { return nodes.values(); }

    public List<DrgEdge> edges() { return edges; }

    public Optional<DrgNode> node(Urn urn) { return Optional.ofNullable(nodes.get(urn)); }

    public boolean contains(Urn urn) { return nodes.containsKey(urn); }

    public List<DrgEdge> outgoing(Urn source, Relation relation) {
        return edges.stream().filter(e -> e.source().equals(source) && e.relation() == relation).toList();
    }

    public DoctrineGraph merge(DoctrineGraph other) {
        List<DrgNode> mergedNodes = new ArrayList<>(nodes.values());
        mergedNodes.addAll(other.nodes());
        List<DrgEdge> mergedEdges = new ArrayList<>(edges);
        mergedEdges.addAll(other.edges());
        return new DoctrineGraph(mergedNodes, mergedEdges);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DoctrineGraph g && List.copyOf(nodes.values()).equals(List.copyOf(g.nodes.values()))
                && edges.equals(g.edges);
    }

    @Override
    public int hashCode() { return 31 * List.copyOf(nodes.values()).hashCode() + edges.hashCode(); }
}
