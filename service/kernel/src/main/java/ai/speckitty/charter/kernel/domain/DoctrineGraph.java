package ai.speckitty.charter.kernel.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An ordered DRG: nodes keyed by URN in first-declared order, {@link Relationship}s in declared order.
 * A node declared twice keeps its first declaration whole, tags and provenance included (ADR-0045 decision 7).
 *
 * <p>The graph holds first-class {@link Relationship}s (ADR-0025 Decision 5): {@code DrgEdge} is a storage DSO in
 * {@code packs.infra.storage} and never reaches the kernel. A relationship's identity is its unordered
 * {@code {from, to}} pair and its {@link Relationship.PairClass} (ADR-0027 Decision 1 as amended by ADR-0045 decision 1),
 * so a pair may hold one structural relationship and, beside it, one {@code tension}. This list is ordered and not a
 * multigraph index; a transitive walk is the {@code packs.application} walker's job, not an edge index here.
 */
public final class DoctrineGraph {

    private final Map<Urn, DrgNode> nodes;
    private final List<Relationship> relationships;

    /**
     * A graph of {@code nodes} and {@code relationships}; a node whose URN was already declared is dropped.
     *
     * @param nodes the nodes, in declaration order
     * @param relationships the relationships, in declaration order; endpoints need not be nodes of this graph
     */
    public DoctrineGraph(Collection<DrgNode> nodes, Collection<Relationship> relationships) {
        Map<Urn, DrgNode> byUrn = new LinkedHashMap<>();
        nodes.forEach(n -> byUrn.putIfAbsent(n.urn(), n));
        this.nodes = Collections.unmodifiableMap(byUrn);
        this.relationships = List.copyOf(relationships);
    }

    /**
     * The graph with no nodes or relationships, the identity of {@link #merge(DoctrineGraph)}.
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
     * The relationships, in declared order, tensions among them.
     *
     * @return an unmodifiable list of the relationships
     */
    public List<Relationship> relationships() {
        return relationships;
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
     * This graph followed by {@code other}: a node of {@code other} whose URN this graph already declares is dropped,
     * and a relationship of {@code other} whose slot (pair and pair class, {@link Relationship#sameSlot}) this graph
     * already holds is dropped, so the first declaration wins (ADR-0045 decision 7).
     *
     * @param other the graph of the next layer
     * @return the merged graph; neither input changes
     */
    public DoctrineGraph merge(DoctrineGraph other) {
        List<DrgNode> mergedNodes = new ArrayList<>(nodes.values());
        mergedNodes.addAll(other.nodes());
        List<Relationship> mergedRelationships = new ArrayList<>(relationships);
        for (Relationship incoming : other.relationships()) {
            if (mergedRelationships.stream().noneMatch(incoming::sameSlot)) {
                mergedRelationships.add(incoming);
            }
        }
        return new DoctrineGraph(mergedNodes, mergedRelationships);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof DoctrineGraph g
                && List.copyOf(nodes.values()).equals(List.copyOf(g.nodes.values()))
                && relationships.equals(g.relationships);
    }

    @Override
    public int hashCode() {
        return Objects.hash(List.copyOf(nodes.values()), relationships);
    }
}
