package ai.speckitty.charter.packs.domain.graph;

/**
 * The kinds of edge the pack graph holds. Which a walk follows is the domain's choice, never the caller's. The
 * relations walk emits {@link #OVERRIDES}, {@link #ENHANCES}, {@link #SPECIALIZES} and {@link #STEP_LINK}; no mode
 * emits the others yet.
 */
public enum EdgeKind {
    /** A descriptor's {@code depends_on} entry. */
    DEPENDS_ON,
    /** A descriptor's {@code parent_pack}. */
    PARENT_PACK,
    /** An {@code overrides} edge a pack writes. */
    OVERRIDES,
    /** An {@code enhances} edge a pack writes. */
    ENHANCES,
    /** A {@code specializes_from} edge a pack writes. */
    SPECIALIZES,
    /** A reference a pack writes to an artifact, as written. */
    REFERENCES,
    /** A mission type's {@code requires} edge to one of its own steps. */
    STEP_LINK,
    /** A substep delegation a mission type writes with another pack's name. */
    DELEGATES
}
