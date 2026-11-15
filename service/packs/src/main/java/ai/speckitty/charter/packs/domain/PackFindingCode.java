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
    /**
     * A step of a mission type declares a substep id twice (FR-004). Validate mode reports it at the repeat; the load
     * holds no such mission type and records the same code at the type's file.
     */
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
    PROMPT_SLOT_NOT_EMPTY_UNVERSIONED(Severity.WARNING),
    /**
     * A file of a registered tree that is not pack content (an exclusion names it, or the {@code ignored} fallback takes
     * it), so the registration does not hold it (#451). A warning on the registration's answer, never a violation.
     */
    FILE_NOT_HELD(Severity.WARNING),
    /**
     * An {@code overrides} edge of a graph document whose source is not of an activatable kind (FR-014,
     * {@link RelationshipEndpointRule}). It is the violation code
     * {@value ai.speckitty.charter.kernel.domain.EndpointRule#OVERRIDES_SOURCE_NOT_ACTIVATABLE} of the graph document at
     * registration and the cause of a {@code pack_unreadable} read; it has no refusal code of its own.
     */
    OVERRIDES_SOURCE_NOT_ACTIVATABLE(Severity.BLOCKING),
    /**
     * An edge of a graph document whose relation does not allow its source and target kinds (ADR-0064 decision 13,
     * {@link RelationshipEndpointRule}). It is the violation code {@value RelationshipEndpointRule#CODE} of the graph
     * document at registration and the cause of a {@code pack_unreadable} read; it has no refusal code of its own.
     */
    RELATIONSHIP_ENDPOINT_NOT_ALLOWED(Severity.BLOCKING),
    /**
     * A step of a mission type with no authored {@code requires} edge from its type, or such an edge to an action that
     * is not one of its steps ({@link MissionStepLinkRule}). It is the violation code {@value MissionStepLinkRule#CODE}
     * at registration and the cause of a {@code pack_unreadable} read; it has no refusal code of its own.
     */
    MISSION_STEP_UNLINKED(Severity.BLOCKING),
    /**
     * A substep's delegation candidate that is no artifact of the delegation's kind in the pack
     * ({@link SubstepDelegationRule}, FR-005). It is the violation code {@value SubstepDelegationRule#UNRESOLVED} at
     * registration and the cause of a {@code pack_unreadable} read; it has no refusal code of its own.
     */
    SUBSTEP_DELEGATION_UNRESOLVED(Severity.BLOCKING),
    /**
     * A substep's delegation candidate the step's action has no {@code scope} edge to ({@link SubstepDelegationRule},
     * FR-005). It is the violation code {@value SubstepDelegationRule#UNSCOPED} at registration and the cause of a
     * {@code pack_unreadable} read; it has no refusal code of its own.
     */
    SUBSTEP_DELEGATION_UNSCOPED(Severity.BLOCKING);

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
            case FILE_NOT_HELD -> Diagnostic.Code.FILE_NOT_HELD;
            default -> throw new IllegalStateException(name() + " has no diagnostic on the load path");
        };
    }
}
