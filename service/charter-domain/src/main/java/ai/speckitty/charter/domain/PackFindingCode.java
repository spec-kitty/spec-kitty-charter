package ai.speckitty.charter.domain;

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
    /** {@code pack.yaml} has a key the schema does not declare; it is ignored. */
    PACK_UNKNOWN_KEY(Severity.WARNING),
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
    ASSET_MIME_INVALID(Severity.BLOCKING);

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
            case PACK_UNREADABLE, REQUIRES_CORE_INVALID, ORG_FRAGMENT_MISSING -> RefusalCode.PACK_UNREADABLE;
            case DRG_SCHEMA_ERROR -> RefusalCode.DRG_SCHEMA_ERROR;
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
            case PACK_UNKNOWN_KEY -> Diagnostic.Code.PACK_UNKNOWN_KEY;
            default -> throw new IllegalStateException(name() + " has no diagnostic on the load path");
        };
    }
}
