package ai.speckitty.charter.kernel.domain;

/**
 * What a {@link GraphWalker} tells the driver of a walk to do after visiting a node or crossing a relationship
 * (ADR-0025 Decision 4, ADR-0082 decision 2).
 */
public enum Descent {
    /** Descend into this node's or relationship's neighbours. */
    CONTINUE,
    /** Do not descend past this one, but keep walking its siblings. */
    SKIP,
    /** Halt the whole walk. */
    STOP
}
