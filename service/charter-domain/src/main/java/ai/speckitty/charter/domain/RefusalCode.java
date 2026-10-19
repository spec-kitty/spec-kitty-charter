package ai.speckitty.charter.domain;

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
    /** An edge endpoint is neither a URN nor a bare id its fragment declares. */
    UNRESOLVED_EDGE_ENDPOINT("unresolved_edge_endpoint");

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
