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
 * Merging appends; a node declared twice keeps its first declaration (layer semantics beyond this union
 * arrive with the conformance corpus, roadmap step 5).
 *
 * <p>The graph holds first-class {@link Relationship}s (ADR-0025 Decision 5): {@code DrgEdge} is now a
 * storage DSO in {@code packs.infra.storage} and never reaches the kernel. Relationship identity is the
 * unordered {@code {from, to}} pair (ADR-0027 Decision 1), so this list is pair-sensitive in order but not a
 * multigraph index; a transitive walk is the {@code packs.application} walker's job, not an edge index here.
 *
 * <p>Beside the relationships the graph holds its {@link Tension}s, in declared order (ADR-0028 Decision 3, ADR-0044): a
 * tension is not a relationship and is outside the pair identity, so a pair may hold one structural relationship and a
 * tension. A node declared twice keeps its first declaration whole, tags and provenance included.
 */
public final class DoctrineGraph {

    private final Map<Urn, DrgNode> nodes;
    private final List<Relationship> relationships;
    private final List<Tension> tensions;

    /**
     * A graph of {@code nodes} and {@code relationships} with no tension; a node whose URN was already declared is
     * dropped.
     *
     * @param nodes the nodes, in declaration order
     * @param relationships the relationships, in declaration order; endpoints need not be nodes of this graph
     */
    public DoctrineGraph(Collection<DrgNode> nodes, Collection<Relationship> relationships) {
        this(nodes, relationships, List.of());
    }

    /**
     * A graph of {@code nodes}, {@code relationships} and {@code tensions}; a node whose URN was already declared is
     * dropped.
     *
     * @param nodes the nodes, in declaration order
     * @param relationships the relationships, in declaration order; endpoints need not be nodes of this graph
     * @param tensions the tensions, in declaration order; endpoints need not be nodes of this graph
     */
    public DoctrineGraph(
            Collection<DrgNode> nodes, Collection<Relationship> relationships, Collection<Tension> tensions) {
        Map<Urn, DrgNode> byUrn = new LinkedHashMap<>();
        nodes.forEach(n -> byUrn.putIfAbsent(n.urn(), n));
        this.nodes = Collections.unmodifiableMap(byUrn);
        this.relationships = List.copyOf(relationships);
        this.tensions = List.copyOf(tensions);
    }

    /**
     * The graph with no nodes, relationships or tensions, the identity of {@link #merge(DoctrineGraph)}.
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
     * The relationships, in declared order.
     *
     * @return an unmodifiable list of the relationships
     */
    public List<Relationship> relationships() {
        return relationships;
    }

    /**
     * The tensions, in declared order.
     *
     * @return an unmodifiable list of the tensions
     */
    public List<Tension> tensions() {
        return tensions;
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
     * This graph followed by {@code other}: its nodes, relationships and tensions are appended, and a node of
     * {@code other} whose URN this graph already declares is dropped.
     *
     * @param other the graph of the next layer
     * @return the merged graph; neither input changes
     */
    public DoctrineGraph merge(DoctrineGraph other) {
        List<DrgNode> mergedNodes = new ArrayList<>(nodes.values());
        mergedNodes.addAll(other.nodes());
        List<Relationship> mergedRelationships = new ArrayList<>(relationships);
        mergedRelationships.addAll(other.relationships());
        List<Tension> mergedTensions = new ArrayList<>(tensions);
        mergedTensions.addAll(other.tensions());
        return new DoctrineGraph(mergedNodes, mergedRelationships, mergedTensions);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof DoctrineGraph g
                && List.copyOf(nodes.values()).equals(List.copyOf(g.nodes.values()))
                && relationships.equals(g.relationships)
                && tensions.equals(g.tensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(List.copyOf(nodes.values()), relationships, tensions);
    }
}
