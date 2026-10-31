package ai.speckitty.charter.kernel.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The closed, behaviour-carrying catalogue of DRG relation meanings (FR-002, ADR-0025 Decision 2,
 * ADR-0027 Decision 5). Each constant declares its {@link #directionality()} and {@link #token()};
 * directionality is derived from the type, never stored on a {@link Relationship}.
 *
 * <p>Supersedes the kernel {@link Relation} enum conceptually. The tokens mirror {@code Relation}
 * one-for-one — the catalogue is closed and the wire spelling is contractual.
 *
 * <p>{@code propagation()} is deferred (ADR-0025 remediation / ADR-0027); this type ships only
 * {@link #directionality()} and {@link #token()}.
 */
public enum RelationType {
    /** Hard dependency; cascade activation follows it. */
    REQUIRES(Directionality.DIRECTED),
    /** Soft recommendation; cascade activation also follows it. */
    SUGGESTS(Directionality.DIRECTED),
    /** An action applies a directive or tactic. */
    APPLIES(Directionality.DIRECTED),
    /** Governance scope: the target is in force within the source's scope. */
    SCOPE(Directionality.DIRECTED),
    /** Links to the glossary that supplies a node's terms. */
    VOCABULARY(Directionality.DIRECTED),
    /** A node instantiates a template. */
    INSTANTIATES(Directionality.DIRECTED),
    /** Narrows or sharpens the target without replacing it. */
    REFINES(Directionality.DIRECTED),
    /** Static lineage between profiles or artifacts; never a runtime handoff. */
    SPECIALIZES_FROM(Directionality.DIRECTED),
    /**
     * Runtime handoff from one agent to another; never inferred from lineage. Should a genuinely
     * symmetric (bidirectional) {@code delegates_to} ever be minted, it is an ADR-0027 revisit
     * trigger for the unordered-pair identity collapse; today it stays {@link Directionality#DIRECTED}.
     */
    DELEGATES_TO(Directionality.DIRECTED),
    /** Field-merge into a target artifact, keeping its action sequence and step inputs and outputs. */
    ENHANCES(Directionality.DIRECTED),
    /** Full replacement of a target artifact. */
    OVERRIDES(Directionality.DIRECTED),
    /** Supersession; kept for older hand-authored fragments. */
    REPLACES(Directionality.DIRECTED),
    /**
     * The signed {@code impacts} relation that ADR-0028 retires; its strength lives on the storage DSO. It is
     * {@link Directionality#BIDIRECTIONAL}. It is not the tension between two rules: that is a {@link Tension}, held
     * beside the relationships and outside their pair identity (ADR-0028 Decision 3, ADR-0045).
     */
    IMPACTS(Directionality.BIDIRECTIONAL),
    /** From a reconciling artifact to one side of a pair joined by a {@link Tension} (ADR-0028 Decision 4). */
    RECONCILES_TENSION(Directionality.DIRECTED),
    /** From a good artifact to an {@code anti_pattern} node. */
    REJECTS(Directionality.DIRECTED);

    private final Directionality directionality;

    RelationType(Directionality directionality) {
        this.directionality = directionality;
    }

    /**
     * The orientation this relation carries, derived from its meaning.
     *
     * @return {@link Directionality#DIRECTED} or {@link Directionality#BIDIRECTIONAL}
     */
    public Directionality directionality() {
        return directionality;
    }

    /**
     * The spelling graph documents use.
     *
     * @return the lower-case constant name, for example {@code specializes_from}
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks a relation type up by its token.
     *
     * @param token a token, for example {@code scope}
     * @return the relation type; empty when no relation type has that token
     */
    public static Optional<RelationType> fromToken(String token) {
        return Arrays.stream(values()).filter(t -> t.token().equals(token)).findFirst();
    }
}
