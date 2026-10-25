package ai.speckitty.charter.kernel.domain;

/**
 * The orientation a {@link RelationType} carries. It is derived from the relation's meaning and is
 * never stored on a {@link Relationship}; a relationship keeps its authored {@code from}/{@code to}
 * orientation regardless (ADR-0027).
 */
public enum Directionality {
    /** The relation holds {@code from → to} only. */
    DIRECTED,
    /** The relation holds both ways; {@code from → to} and {@code to → from} are the same claim. */
    BIDIRECTIONAL
}
