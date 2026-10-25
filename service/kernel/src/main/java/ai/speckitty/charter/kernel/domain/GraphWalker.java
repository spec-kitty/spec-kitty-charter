package ai.speckitty.charter.kernel.domain;

/**
 * The traversal contract the application-layer lazy walk driver consults (ADR-0025 Decision 4). The
 * kernel defines the type; it never fetches. The driver calls {@link #onNode} when it visits a
 * component and {@link #onRelationship} when it crosses a relationship, and obeys the returned
 * {@link Descent}.
 */
public interface GraphWalker {

    /**
     * Visits a component node.
     *
     * @param node the node being visited
     * @return whether the driver descends into the node's neighbours, skips them, or stops the walk
     */
    Descent onNode(DrgNode node);

    /**
     * Crosses a relationship.
     *
     * @param relationship the relationship being crossed
     * @return whether the driver descends past this relationship, skips it, or stops the walk
     */
    Descent onRelationship(Relationship relationship);
}
