package ai.speckitty.charter.kernel.domain;

/**
 * The visitor contract of a graph walk (ADR-0025 Decision 4, ADR-0082 decision 2). The kernel defines the type; it
 * never fetches and never drives. A driver calls {@link #onNode} when it visits a node and {@link #onRelationship} when
 * it crosses a relationship, and obeys the returned {@link Descent}. The artifact walk drives it over the DRG
 * ({@code GraphWalker<DrgNode, Relationship>}); the packs domain's {@code PackGraph} drives it over the pack graph.
 *
 * @param <N> the node type the driver visits
 * @param <E> the relationship type the driver crosses
 */
public interface GraphWalker<N, E> {

    /**
     * Visits a node.
     *
     * @param node the node being visited
     * @return whether the driver descends into the node's neighbours, skips them, or stops the walk
     */
    Descent onNode(N node);

    /**
     * Crosses a relationship.
     *
     * @param relationship the relationship being crossed
     * @return whether the driver descends past this relationship, skips it, or stops the walk
     */
    Descent onRelationship(E relationship);
}
