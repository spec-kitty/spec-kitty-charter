package ai.speckitty.charter.kernel.domain;

/**
 * Refusal codes; part of the contract ({@code CharterRefusalCode}). The constant names are Java's; the
 * {@link #token()} is the snake_case wire spelling, which never changes once released.
 */
public enum RefusalCode {
    /** The asked mission type is not a node of the merged graph. */
    MISSION_TYPE_NOT_FOUND("mission_type_not_found"),
    /** The asked action is not a node of the merged graph. */
    ACTION_NOT_FOUND("action_not_found"),
    /** A pack declares a level other than the one it is configured at. */
    PACK_LEVEL_MISMATCH("pack_level_mismatch"),
    /** A pack's files cannot be read or parsed. */
    PACK_UNREADABLE("pack_unreadable"),
    /** A graph document breaks the DRG schema, for example with an unknown key or relation. */
    DRG_SCHEMA_ERROR("drg_schema_error"),
    /**
     * An edge endpoint is neither a URN nor a bare id its fragment declares. No longer raised: an endpoint is a URN,
     * and one that is not is {@link #DRG_SCHEMA_ERROR}. The code stays so that the contract keeps accepting it.
     */
    UNRESOLVED_EDGE_ENDPOINT("unresolved_edge_endpoint"),
    /**
     * The service failed in a way no other code names. No load or query raises it: the inbound adapter refuses
     * an unexpected exception with it, so that every 500 the contract answers is a refusal.
     */
    INTERNAL_ERROR("internal_error"),
    /**
     * Reserved (ADR-0009): a lower-tier pack breaks the mutability policy of the pack that owns an artifact. No load
     * or query raises it yet; the code is in the contract so that the policy work adds no breaking change.
     */
    GOVERNANCE_POLICY_VIOLATION("governance_policy_violation"),
    /**
     * The pack or configuration files changed while the read was answered, so the answer and the fingerprint it
     * would report might disagree; the read may be retried. Raised by the service info and the tactic reads.
     */
    STALE_INPUT("stale_input"),
    /** The asked pack is not one the service has, or its files cannot be read as a pack; the caller asked wrongly. */
    PACK_NOT_FOUND("pack_not_found"),
    /** The asked pack has no tactic with the asked identity. */
    TACTIC_NOT_FOUND("tactic_not_found"),
    /** The asked pack has no directive with the asked identity. Added for the directive read path (WP05). */
    DIRECTIVE_NOT_FOUND("directive_not_found"),
    /** The asked pack has no styleguide with the asked identity. Added for the styleguide read path (WP07). */
    STYLEGUIDE_NOT_FOUND("styleguide_not_found"),
    /** The asked pack has no paradigm with the asked identity. Added for the paradigm read path (WP08). */
    PARADIGM_NOT_FOUND("paradigm_not_found"),
    /** The asked pack has no toolguide with the asked identity. Added for the toolguide read path (WP09). */
    TOOLGUIDE_NOT_FOUND("toolguide_not_found"),
    /**
     * A registration request is well-formed but its content breaks a pack invariant or a schema (ADR-0026): the
     * mutated aggregate does not revalidate. Answered as 422. Added for the registration write path (WP05+).
     */
    VALIDATION_FAILED("validation_failed"),
    /**
     * A registration request conflicts with the pack's current state: a net-new id already exists, or a relationship
     * names a {@code from} the pack does not hold (ADR-0026). Answered as 409. Added for the registration write path
     * (WP05+).
     */
    REGISTRATION_CONFLICT("registration_conflict");

    private final String token;

    RefusalCode(String token) {
        this.token = token;
    }

    /**
     * The wire spelling of the code.
     *
     * @return the snake_case token, for example {@code action_not_found}
     */
    public String token() {
        return token;
    }

    /**
     * Whether the refusal is about what was asked rather than about the offering.
     *
     * @return {@code true} for {@link #MISSION_TYPE_NOT_FOUND}, {@link #ACTION_NOT_FOUND}, {@link #PACK_NOT_FOUND},
     *     {@link #TACTIC_NOT_FOUND}, {@link #DIRECTIVE_NOT_FOUND}, {@link #STYLEGUIDE_NOT_FOUND},
     *     {@link #PARADIGM_NOT_FOUND} and {@link #TOOLGUIDE_NOT_FOUND}
     */
    public boolean notFound() {
        return this == MISSION_TYPE_NOT_FOUND
                || this == ACTION_NOT_FOUND
                || this == PACK_NOT_FOUND
                || this == TACTIC_NOT_FOUND
                || this == DIRECTIVE_NOT_FOUND
                || this == STYLEGUIDE_NOT_FOUND
                || this == PARADIGM_NOT_FOUND
                || this == TOOLGUIDE_NOT_FOUND;
    }
}
