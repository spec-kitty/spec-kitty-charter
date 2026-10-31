package ai.speckitty.charter.kernel.domain;

import java.util.Arrays;
import java.util.Optional;

/** The relation of a DRG edge (docs/architecture/logical-model.md, "Relations"). */
public enum Relation {
    /** Hard dependency; cascade activation follows it. */
    REQUIRES,
    /** Soft recommendation; cascade activation also follows it. */
    SUGGESTS,
    /** An action applies a directive or tactic. */
    APPLIES,
    /** Governance scope: the target is in force within the source's scope. */
    SCOPE,
    /** Links to the glossary that supplies a node's terms. */
    VOCABULARY,
    /** A node instantiates a template. */
    INSTANTIATES,
    /** Narrows or sharpens the target without replacing it. */
    REFINES,
    /** Static lineage between profiles or artifacts; never a runtime handoff. */
    SPECIALIZES_FROM,
    /** Runtime handoff from one agent to another; never inferred from lineage. */
    DELEGATES_TO,
    /** Field-merge into a target artifact, keeping its action sequence and step inputs and outputs. */
    ENHANCES,
    /** Full replacement of a target artifact. */
    OVERRIDES,
    /** Supersession; kept for older hand-authored fragments. */
    REPLACES,
    /**
     * Directed and signed: the edge's {@code impacts} strength runs from -1 (sabotages) to +1 (reinforces), carried on
     * the storage DSO ({@code packs.infra.storage.DrgEdge}), not on a kernel type. ADR-0028 retires it as a relation;
     * a tension between two rules is a {@link Tension}, written {@code in_tension_with} in today's pack files, never an
     * {@code impacts} edge (ADR-0044).
     */
    IMPACTS,
    /** From a reconciling artifact to one side of a pair joined by a {@link Tension} (ADR-0028 Decision 4). */
    RECONCILES_TENSION,
    /** From a good artifact to an {@code anti_pattern} node. */
    REJECTS;

    /**
     * The spelling graph documents use.
     *
     * @return the lower-case constant name, for example {@code specializes_from}
     */
    public String token() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Looks a relation up by its token.
     *
     * @param token a token, for example {@code scope}
     * @return the relation; empty when no relation has that token
     */
    public static Optional<Relation> fromToken(String token) {
        return Arrays.stream(values()).filter(r -> r.token().equals(token)).findFirst();
    }
}
