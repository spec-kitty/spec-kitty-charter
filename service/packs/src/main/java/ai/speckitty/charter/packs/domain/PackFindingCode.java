package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.RefusalCode;

/** The stable codes {@link PackPolicy} emits; each has a fixed severity. */
public enum PackFindingCode {
    /** The pack has no {@code pack.yaml} or declares no {@code pack_level}. */
    PACK_LEVEL_MISSING(Severity.WARNING),
    /** The pack declares a level other than the configured one. */
    PACK_LEVEL_MISMATCH(Severity.BLOCKING),
    /** The pack's files cannot be read or parsed. */
    PACK_UNREADABLE(Severity.BLOCKING),
    /** {@code requires_core} is not a valid specifier set. */
    REQUIRES_CORE_INVALID(Severity.BLOCKING),
    /** {@code pack_version} is not a PEP 440 version, so no {@code depends_on} range can be decided against it. */
    PACK_VERSION_INVALID(Severity.BLOCKING),
    /** A {@code depends_on[].range} is not a valid specifier set. */
    DEPENDENCY_RANGE_INVALID(Severity.BLOCKING),
    /** An org pack has no {@code drg/fragment.yaml}. */
    ORG_FRAGMENT_MISSING(Severity.BLOCKING),
    /** A graph document breaks the DRG schema. */
    DRG_SCHEMA_ERROR(Severity.BLOCKING),
    /** An artifact file breaks its schema; validate mode only. */
    ARTIFACT_SCHEMA_ERROR(Severity.BLOCKING),
    /** The org charter breaks its schema; validate mode only. */
    ORG_CHARTER_SCHEMA_ERROR(Severity.BLOCKING),
    /** A step contract declares a step id twice; validate mode only. */
    DUPLICATE_STEP_ID(Severity.BLOCKING),
    /** Two artifacts share an id; validate mode only. */
    DUPLICATE_ARTIFACT_ID(Severity.BLOCKING),
    /** An asset path leaves the assets root; validate mode only. */
    ASSET_PATH_ESCAPE(Severity.BLOCKING),
    /** An asset declares a MIME type its extension does not allow; validate mode only. */
    ASSET_MIME_INVALID(Severity.BLOCKING),
    /**
     * A step enhancement or a prompt fragment breaks a rule of ADR-0066: the fragment's content, the enhancement's slots,
     * companions, edges, directory or identity (FR-004 to FR-006). It is a violation code of the file at registration
     * and the cause of a {@code pack_unreadable} read.
     */
    PROMPT_FRAGMENT_INVALID(Severity.BLOCKING),
    /**
     * A step prompt's slot declaration is malformed, repeated, nested or unpaired (FR-007), whatever the descriptor's
     * version. It is a violation code of the prompt file at registration and the cause of a {@code pack_unreadable}
     * read.
     */
    PROMPT_SLOT_MALFORMED(Severity.BLOCKING),
    /**
     * A step prompt's slot holds text in a pack whose descriptor declares {@code schema_version} {@code "1.0"}
     * (FR-007). It is a violation code of the prompt file at registration and the cause of a {@code pack_unreadable}
     * read.
     */
    PROMPT_SLOT_NOT_EMPTY(Severity.BLOCKING),
    /**
     * A step prompt's slot holds text in a pack whose descriptor declares no {@code schema_version}: the same
     * condition as {@link #PROMPT_SLOT_NOT_EMPTY}, a warning for the legacy descriptor (ADR-0066 version gate). Two
     * codes for one condition because a severity is fixed per code.
     */
    PROMPT_SLOT_NOT_EMPTY_UNVERSIONED(Severity.WARNING);

    /** How a finding weighs. */
    public enum Severity {
        /** The load goes on; the finding is reported as a diagnostic. */
        WARNING,
        /** The load stops; the finding is reported as a refusal. */
        BLOCKING
    }

    private final Severity severity;

    PackFindingCode(Severity severity) {
        this.severity = severity;
    }

    /**
     * The fixed severity of the code.
     *
     * @return the severity
     */
    public Severity severity() {
        return severity;
    }

    /**
     * The refusal code a blocking load-path finding projects onto.
     *
     * @return the refusal code
     * @throws IllegalStateException for a code that is validate-only or a warning
     */
    public RefusalCode refusal() {
        return switch (this) {
            case PACK_LEVEL_MISMATCH -> RefusalCode.PACK_LEVEL_MISMATCH;
            case PACK_UNREADABLE,
                    REQUIRES_CORE_INVALID,
                    PACK_VERSION_INVALID,
                    DEPENDENCY_RANGE_INVALID,
                    ORG_FRAGMENT_MISSING -> RefusalCode.PACK_UNREADABLE;
            case DRG_SCHEMA_ERROR -> RefusalCode.DRG_SCHEMA_ERROR;
            case PROMPT_FRAGMENT_INVALID -> RefusalCode.PROMPT_FRAGMENT_INVALID;
            case PROMPT_SLOT_MALFORMED -> RefusalCode.PROMPT_SLOT_MALFORMED;
            case PROMPT_SLOT_NOT_EMPTY -> RefusalCode.PROMPT_SLOT_NOT_EMPTY;
            default -> throw new IllegalStateException(name() + " has no refusal on the load path");
        };
    }

    /**
     * The diagnostic code a load-path warning projects onto.
     *
     * @return the diagnostic code
     * @throws IllegalStateException for a code that is validate-only or blocking
     */
    public Diagnostic.Code diagnostic() {
        return switch (this) {
            case PACK_LEVEL_MISSING -> Diagnostic.Code.PACK_LEVEL_MISSING;
            case PROMPT_SLOT_NOT_EMPTY_UNVERSIONED -> Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY;
            default -> throw new IllegalStateException(name() + " has no diagnostic on the load path");
        };
    }
}
