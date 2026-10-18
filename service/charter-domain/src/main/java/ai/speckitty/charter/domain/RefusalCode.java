package ai.speckitty.charter.domain;

/** Refusal codes; part of the contract ({@code CharterRefusalCode}), so spellings never change. */
public enum RefusalCode {
    MISSION_TYPE_NOT_FOUND("MISSION_TYPE_NOT_FOUND"),
    ACTION_NOT_FOUND("ACTION_NOT_FOUND"),
    PACK_LEVEL_MISMATCH("PACK_LEVEL_MISMATCH"),
    PACK_UNREADABLE("PACK_UNREADABLE"),
    DRG_SCHEMA_ERROR("DRG_SCHEMA_ERROR"),
    UNRESOLVED_EDGE_ENDPOINT("unresolved_edge_endpoint");

    private final String code;

    RefusalCode(String code) { this.code = code; }

    public String code() { return code; }

    /** True when the refusal is about what was asked, not about the offering. */
    public boolean notFound() { return this == MISSION_TYPE_NOT_FOUND || this == ACTION_NOT_FOUND; }
}
