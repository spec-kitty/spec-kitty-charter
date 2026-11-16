package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The codes the pack domain's rules emit, and the codes of the {@code CLOSURE} composition (#554, R2, S3): each a kernel
 * {@link ReasonCode} that owns its severity (ruling 4), with one wording ({@link #detail}). Each token is built exactly
 * once: here when a core rule emits it, in the storage edge's {@code StorageCodes} when only the storage edge does, and
 * in {@code pack-validation}'s {@code PlacementCodes} for the placement tokens. The storage edge names these constants
 * where it reports the same fault, as it does for an asset sidecar whose payload is missing.
 *
 * <p>Where a contract enum already spells a token ({@link RefusalCode}, the kernel's {@link EndpointRule}) or a rule
 * declares its own ({@link MissionStepLinkRule#CODE}), the constant is built from that spelling, so the literal exists
 * once. The one condition with two severities, a step prompt's slot that holds text, is two constants with one token
 * ({@link #PROMPT_SLOT_NOT_EMPTY} and {@link #PROMPT_SLOT_NOT_EMPTY_WARNING}): a mapper tells them apart by the constant,
 * never by its {@link ReasonCode#value()}.
 */
public final class PackCodes {

    /** A graph document breaks the DRG schema or a graph rule. */
    public static final ReasonCode DRG_SCHEMA_ERROR = violation(RefusalCode.DRG_SCHEMA_ERROR.token());

    /** An edge whose relation does not allow its source and target kinds ({@link RelationshipEndpointRule}). */
    public static final ReasonCode RELATIONSHIP_ENDPOINT_NOT_ALLOWED = violation(RelationshipEndpointRule.CODE);

    /** An {@code overrides} edge whose source is not of an activatable kind ({@link RelationshipEndpointRule}). */
    public static final ReasonCode OVERRIDES_SOURCE_NOT_ACTIVATABLE =
            violation(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE);

    /** A step of a mission type declares a substep id twice (FR-004). */
    public static final ReasonCode DUPLICATE_STEP_ID = violation("duplicate_step_id");

    /** Two artifacts of one kind share an id; the first file in path order owns it. */
    public static final ReasonCode DUPLICATE_ARTIFACT_ID = violation("duplicate_artifact_id");

    /**
     * An asset sidecar the pack cannot hold: its path escapes {@code assets/}, or names no payload the pack holds. The
     * reader names it for a missing payload, the asset rule for an escape.
     */
    public static final ReasonCode ASSET = violation(ArtifactKind.ASSET.token());

    /** A mission type's step with no authored {@code requires} edge, or a stray one ({@link MissionStepLinkRule}). */
    public static final ReasonCode MISSION_STEP_UNLINKED = violation(MissionStepLinkRule.CODE);

    /** A substep's delegation candidate that is no artifact of its kind in the pack ({@link SubstepDelegationRule}). */
    public static final ReasonCode SUBSTEP_DELEGATION_UNRESOLVED = violation(SubstepDelegationRule.UNRESOLVED);

    /** A substep's delegation candidate the step's action has no {@code scope} edge to ({@link SubstepDelegationRule}). */
    public static final ReasonCode SUBSTEP_DELEGATION_UNSCOPED = violation(SubstepDelegationRule.UNSCOPED);

    /** A step enhancement or a prompt fragment breaks a rule of ADR-0066. */
    public static final ReasonCode PROMPT_FRAGMENT_INVALID = violation(RefusalCode.PROMPT_FRAGMENT_INVALID.token());

    /** A step prompt's slot declaration is malformed, repeated, nested or unpaired (FR-007). */
    public static final ReasonCode PROMPT_SLOT_MALFORMED = violation(RefusalCode.PROMPT_SLOT_MALFORMED.token());

    /** A step prompt's slot holds text, in a pack whose descriptor declares {@code schema_version} {@code "1.0"}. */
    public static final ReasonCode PROMPT_SLOT_NOT_EMPTY = violation(RefusalCode.PROMPT_SLOT_NOT_EMPTY.token());

    /**
     * A step prompt's slot holds text, in a pack whose descriptor declares no {@code schema_version}: the same token as
     * {@link #PROMPT_SLOT_NOT_EMPTY}, a warning (ADR-0066 version gate).
     */
    public static final ReasonCode PROMPT_SLOT_NOT_EMPTY_WARNING =
            new ReasonCode(RefusalCode.PROMPT_SLOT_NOT_EMPTY.token(), Severity.WARNING);

    /** The codes of the {@code CLOSURE} composition: the resolver's cycle, dependency, parent, level and conflict kinds. */
    public static final List<ReasonCode> CLOSURE = List.of(
            violation(RefusalCode.PACK_DEPENDENCY_CYCLE.token()),
            violation(RefusalCode.PACK_DEPENDENCY_UNKNOWN.token()),
            violation(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE.token()),
            violation(RefusalCode.PACK_PARENT_UNRESOLVED.token()),
            violation(RefusalCode.PACK_LEVEL_VIOLATION.token()),
            violation(RefusalCode.PACK_NAME_CONFLICT.token()),
            violation(RefusalCode.PACK_DUPLICATE_ID.token()),
            violation(RefusalCode.PACK_SIBLING_CONFLICT.token()));

    /** The codes of the content rules, in this class's order. */
    public static final List<ReasonCode> CONTENT = List.of(
            DRG_SCHEMA_ERROR,
            RELATIONSHIP_ENDPOINT_NOT_ALLOWED,
            OVERRIDES_SOURCE_NOT_ACTIVATABLE,
            DUPLICATE_STEP_ID,
            DUPLICATE_ARTIFACT_ID,
            ASSET,
            MISSION_STEP_UNLINKED,
            SUBSTEP_DELEGATION_UNRESOLVED,
            SUBSTEP_DELEGATION_UNSCOPED,
            PROMPT_FRAGMENT_INVALID,
            PROMPT_SLOT_MALFORMED,
            PROMPT_SLOT_NOT_EMPTY,
            PROMPT_SLOT_NOT_EMPTY_WARNING);

    /** Every code this class builds: the content codes, then the {@code CLOSURE} codes. */
    public static final Set<ReasonCode> ALL = all();

    private PackCodes() {}

    /**
     * The one wording of a fault at a file, whatever its code and whichever surface reports it (R2): the reason after
     * the pack-relative file, as {@code <file>: <reason>}. A reason that already names the file is the detail as it is,
     * and so is a fault of no file.
     *
     * @param file the pack-relative file the fault belongs to; empty for none
     * @param reason what is wrong
     * @return the detail
     */
    public static String detail(String file, String reason) {
        return file.isEmpty() || reason.contains(file) ? reason : file + ": " + reason;
    }

    private static ReasonCode violation(String token) {
        return new ReasonCode(token, Severity.VIOLATION);
    }

    private static Set<ReasonCode> all() {
        Set<ReasonCode> all = new LinkedHashSet<>(CONTENT);
        all.addAll(CLOSURE);
        return Set.copyOf(all);
    }
}
