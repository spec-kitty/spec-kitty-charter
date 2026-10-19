package ai.speckitty.charter.domain;

/**
 * Refusal codes; part of the contract ({@code CharterRefusalCode}). The constant names are Java's; the
 * {@link #token()} is the snake_case wire spelling, which never changes once released.
 */
public enum RefusalCode {
    MISSION_TYPE_NOT_FOUND("mission_type_not_found"),
    ACTION_NOT_FOUND("action_not_found"),
    PACK_LEVEL_MISMATCH("pack_level_mismatch"),
    PACK_UNREADABLE("pack_unreadable"),
    DRG_SCHEMA_ERROR("drg_schema_error"),
    UNRESOLVED_EDGE_ENDPOINT("unresolved_edge_endpoint");

    private final String token;

    RefusalCode(String token) {
        this.token = token;
    }

    /** The wire spelling of the code. */
    public String token() {
        return token;
    }

    /** True when the refusal is about what was asked, not about the offering. */
    public boolean notFound() {
        return this == MISSION_TYPE_NOT_FOUND || this == ACTION_NOT_FOUND;
    }
}
