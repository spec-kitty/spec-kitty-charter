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
     * would report might disagree; the read may be retried. Raised by {@code ConsistentLoad} and by the service
     * info.
     */
    STALE_INPUT("stale_input");

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
     * @return {@code true} for {@link #MISSION_TYPE_NOT_FOUND} and {@link #ACTION_NOT_FOUND}
     */
    public boolean notFound() {
        return this == MISSION_TYPE_NOT_FOUND || this == ACTION_NOT_FOUND;
    }
}
