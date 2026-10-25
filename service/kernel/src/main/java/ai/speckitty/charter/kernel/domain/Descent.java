package ai.speckitty.charter.kernel.domain;

/**
 * What a {@link GraphWalker} tells the lazy walk driver to do after visiting a node or crossing a
 * relationship (ADR-0025 Decision 4).
 */
public enum Descent {
    /** Descend into this node's or relationship's neighbours. */
    CONTINUE,
    /** Do not descend past this one, but keep walking its siblings. */
    SKIP,
    /** Halt the whole walk. */
    STOP
}
