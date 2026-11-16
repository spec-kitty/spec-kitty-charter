package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.Optional;
import java.util.Set;

/**
 * The thin view validate mode's {@link PackPolicy} still emits through, staging inside #554's one pull request (R4 b):
 * the codes a domain class names that {@link PackCodes} does not build. They are the three placement tokens, which
 * {@code pack-validation}'s {@code PlacementCodes} builds once validate mode moves there (WP09), and the storage edge's
 * tokens that today's validate mode still reports from the domain, which the storage edge's {@code StorageCodes}
 * builds. A domain class may not name the storage edge, so until the one reader reports them (WP05, WP09) each is a
 * constant here equal to its {@code StorageCodes} twin. WP13 deletes this class; no new caller names it.
 */
public final class PackFindingCode {

    /** The pack has no {@code pack.yaml} or declares no {@code pack_level}: placement, a warning. */
    public static final ReasonCode PACK_LEVEL_MISSING = new ReasonCode("pack_level_missing", Severity.WARNING);

    /** The pack declares a level other than the configured one: placement. */
    public static final ReasonCode PACK_LEVEL_MISMATCH = violation(RefusalCode.PACK_LEVEL_MISMATCH.token());

    /** An org pack has no {@code drg/fragment.yaml}: placement. */
    public static final ReasonCode ORG_FRAGMENT_MISSING = violation("org_fragment_missing");

    /** The pack's descriptor cannot be read or parsed: {@code StorageCodes.PACK_UNREADABLE}. */
    public static final ReasonCode PACK_UNREADABLE = violation(RefusalCode.PACK_UNREADABLE.token());

    /** {@code requires_core} is not a valid specifier set: {@code StorageCodes.REQUIRES_CORE_INVALID}. */
    public static final ReasonCode REQUIRES_CORE_INVALID = violation("requires_core_invalid");

    /** {@code pack_version} is not a PEP 440 version: {@code StorageCodes.PACK_VERSION_INVALID}. */
    public static final ReasonCode PACK_VERSION_INVALID = violation("pack_version_invalid");

    /** A {@code depends_on[].range} is not a valid specifier set: {@code StorageCodes.DEPENDENCY_RANGE_INVALID}. */
    public static final ReasonCode DEPENDENCY_RANGE_INVALID = violation("dependency_range_invalid");

    /** An artifact file breaks its schema: {@code StorageCodes.ARTIFACT_SCHEMA_ERROR}. */
    public static final ReasonCode ARTIFACT_SCHEMA_ERROR = violation("artifact_schema_error");

    /** An asset path leaves the assets root; validate mode only. */
    public static final ReasonCode ASSET_PATH_ESCAPE = violation("asset_path_escape");

    /** An asset's MIME type is malformed or does not match its extension: {@code StorageCodes.ASSET_MIME_INVALID}. */
    public static final ReasonCode ASSET_MIME_INVALID = violation("asset_mime_invalid");

    /** A step prompt's slot holds text in a pack whose descriptor declares no {@code schema_version}; a warning. */
    public static final ReasonCode PROMPT_SLOT_NOT_EMPTY_UNVERSIONED =
            new ReasonCode("prompt_slot_not_empty_unversioned", Severity.WARNING);

    /** A file the store does not hold: {@code StorageCodes.FILE_NOT_HELD}, a warning. */
    public static final ReasonCode FILE_NOT_HELD =
            new ReasonCode(Diagnostic.Code.FILE_NOT_HELD.token(), Severity.WARNING);

    /** The codes of the rules that look at the pack as a whole: the placement codes. */
    public static final Set<ReasonCode> PLACEMENT =
            Set.of(PACK_LEVEL_MISSING, PACK_LEVEL_MISMATCH, ORG_FRAGMENT_MISSING);

    private PackFindingCode() {}

    /**
     * The refusal code a blocking load-path finding projects onto, by its constant.
     *
     * @param code the finding's code
     * @return the refusal code
     * @throws IllegalStateException for a code that is validate-only or a warning
     */
    public static RefusalCode refusal(ReasonCode code) {
        if (code.equals(PACK_LEVEL_MISMATCH)) {
            return RefusalCode.PACK_LEVEL_MISMATCH;
        }
        if (Set.of(
                        PACK_UNREADABLE,
                        REQUIRES_CORE_INVALID,
                        PACK_VERSION_INVALID,
                        DEPENDENCY_RANGE_INVALID,
                        ORG_FRAGMENT_MISSING)
                .contains(code)) {
            return RefusalCode.PACK_UNREADABLE;
        }
        if (code.equals(PackCodes.DRG_SCHEMA_ERROR)) {
            return RefusalCode.DRG_SCHEMA_ERROR;
        }
        if (code.equals(PackCodes.PROMPT_FRAGMENT_INVALID)) {
            return RefusalCode.PROMPT_FRAGMENT_INVALID;
        }
        if (code.equals(PackCodes.PROMPT_SLOT_MALFORMED)) {
            return RefusalCode.PROMPT_SLOT_MALFORMED;
        }
        if (code.equals(PackCodes.PROMPT_SLOT_NOT_EMPTY)) {
            return RefusalCode.PROMPT_SLOT_NOT_EMPTY;
        }
        throw new IllegalStateException(code.value() + " has no refusal on the load path");
    }

    /**
     * The diagnostic code a load-path warning projects onto, by its constant.
     *
     * @param code the finding's code
     * @return the diagnostic code; empty for a code that is a violation or validate-only
     */
    public static Optional<Diagnostic.Code> diagnostic(ReasonCode code) {
        if (code.equals(PACK_LEVEL_MISSING)) {
            return Optional.of(Diagnostic.Code.PACK_LEVEL_MISSING);
        }
        if (code.equals(PROMPT_SLOT_NOT_EMPTY_UNVERSIONED) || code.equals(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING)) {
            return Optional.of(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY);
        }
        if (code.equals(FILE_NOT_HELD)) {
            return Optional.of(Diagnostic.Code.FILE_NOT_HELD);
        }
        return Optional.empty();
    }

    private static ReasonCode violation(String token) {
        return new ReasonCode(token, Severity.VIOLATION);
    }
}
