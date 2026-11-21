package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * An artifact file breaks its schema, a rule of its kind that the schema cannot state, or the reference closure
     * ({@code ReferenceClosure}, #355): the code the one reader, the assembly and the PACK ruleset state such a file
     * with, at the file.
     */
    public static final ReasonCode ARTIFACT_SCHEMA_ERROR = violation("artifact_schema_error");

    /** A graph document breaks the DRG schema or a graph rule. */
    public static final ReasonCode DRG_SCHEMA_ERROR = violation(RefusalCode.DRG_SCHEMA_ERROR.token());

    /** An edge whose relation does not allow its source and target kinds ({@link RelationshipEndpointRule}). */
    public static final ReasonCode RELATIONSHIP_ENDPOINT_NOT_ALLOWED = violation(RelationshipEndpointRule.CODE);

    /** An {@code overrides} edge whose source is not of an activatable kind ({@link RelationshipEndpointRule}). */
    public static final ReasonCode OVERRIDES_SOURCE_NOT_ACTIVATABLE =
            violation(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE);

    /** A pack writes a second tension on an unordered pair it already joins by a tension (ADR-0045 decision 6). */
    public static final ReasonCode TENSION_DUPLICATED = violation("tension_duplicated");

    /**
     * A tension written in the wrong orientation (ADR-0045 decisions 1 and 6): the pack owns one side and the other is
     * the source, or the pack owns both sides, or neither, and the source is not the smaller bare URN.
     */
    public static final ReasonCode TENSION_ORIENTATION = violation("tension_orientation");

    /** A tension whose two sides have the same bare URN, two versions of one artifact (ADR-0045 decision 1). */
    public static final ReasonCode TENSION_SELF = violation("tension_self");

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

    /**
     * A pack that cannot be read, or, with violations of its own, a substep delegation the closure does not hold: built
     * here once, and named by the storage edge's {@code StorageCodes} (#556).
     */
    public static final ReasonCode PACK_UNREADABLE = violation(RefusalCode.PACK_UNREADABLE.token());

    /**
     * A pack refused for faults of its own content: built here once, and named by the storage edge's
     * {@code StorageCodes} for the assembly's refusal (#556).
     */
    public static final ReasonCode VALIDATION_FAILED = violation(RefusalCode.VALIDATION_FAILED.token());

    /** The codes of the content rules, in this class's order. */
    public static final List<ReasonCode> CONTENT = List.of(
            DRG_SCHEMA_ERROR,
            ARTIFACT_SCHEMA_ERROR,
            RELATIONSHIP_ENDPOINT_NOT_ALLOWED,
            OVERRIDES_SOURCE_NOT_ACTIVATABLE,
            TENSION_DUPLICATED,
            TENSION_ORIENTATION,
            TENSION_SELF,
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

    /**
     * The codes a closure check can find a fault with (#556, ADR-0075 decision 6), each a violation: the {@code CLOSURE}
     * codes, {@code pack_unreadable} and {@code validation_failed}, the qualified references, the relation rules, the
     * ADR-0068 fallback profile, the role pins, the profile enhancements, the mutability policy and the dependents of a
     * removal. A {@code ClosureFault} carries one of them, and nothing else.
     */
    private static final Map<RefusalCode, ReasonCode> CLOSURE_CHECKS = buildClosureChecks();

    /** Every code this class builds: the content codes, the {@code CLOSURE} codes, then the other closure-check codes. */
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

    /**
     * The reason of a {@link #DUPLICATE_ARTIFACT_ID}: the one wording validate mode and the load give a second file that
     * declares an id of its kind (#554, R2 row 10).
     *
     * @param kind the kind's token, for example {@code tactic}
     * @param id the repeated id
     * @param first the pack-relative file that declared it first
     * @return the reason, without the second file
     */
    public static String duplicateArtifactId(String kind, String id, String first) {
        return kind + " id '" + id + "' is already declared in " + first;
    }

    private static ReasonCode violation(String token) {
        return new ReasonCode(token, Severity.VIOLATION);
    }

    /**
     * The code a closure check finds a fault of a refusal code with.
     *
     * @param code a refusal code of the closure checks
     * @return its one code
     * @throws IllegalArgumentException when no closure check finds a fault with that code
     */
    public static ReasonCode closure(RefusalCode code) {
        ReasonCode found = CLOSURE_CHECKS.get(Objects.requireNonNull(code, "code"));
        if (found == null) {
            throw new IllegalArgumentException(code.token() + " is no code of a closure check");
        }
        return found;
    }

    /**
     * Whether a closure check finds faults with a refusal code.
     *
     * @param code the refusal code
     * @return true for a code of {@link #closure(RefusalCode)}'s set
     */
    public static boolean isClosureCheck(RefusalCode code) {
        return CLOSURE_CHECKS.containsKey(code);
    }

    /**
     * The refusal code of a closure check's code: the inverse of {@link #closure(RefusalCode)}.
     *
     * @param code a code of the closure checks
     * @return its refusal code
     * @throws IllegalArgumentException when the code is no closure check's
     */
    public static RefusalCode refusalOf(ReasonCode code) {
        return CLOSURE_CHECKS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(code))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(code.value() + " is no code of a closure check"));
    }

    /**
     * Every refusal code of the closure checks, in a stable order.
     *
     * @return the codes
     */
    public static Set<RefusalCode> closureChecks() {
        return CLOSURE_CHECKS.keySet();
    }

    private static Map<RefusalCode, ReasonCode> buildClosureChecks() {
        Map<RefusalCode, ReasonCode> checks = new LinkedHashMap<>();
        for (ReasonCode code : CLOSURE) {
            checks.put(
                    Arrays.stream(RefusalCode.values())
                            .filter(candidate -> candidate.token().equals(code.value()))
                            .findFirst()
                            .orElseThrow(),
                    code);
        }
        checks.put(RefusalCode.PACK_UNREADABLE, PACK_UNREADABLE);
        checks.put(RefusalCode.VALIDATION_FAILED, VALIDATION_FAILED);
        for (RefusalCode code : List.of(
                RefusalCode.PACK_REFERENCE_UNQUALIFIED,
                RefusalCode.PACK_REFERENCE_PACK_MISSING,
                RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                RefusalCode.SELF_OVERRIDE,
                RefusalCode.CONFLICTING_RELATIONS,
                RefusalCode.OVERRIDE_NOT_PERMITTED,
                RefusalCode.OVERRIDE_CYCLE,
                RefusalCode.ENHANCES_CYCLE,
                RefusalCode.SPECIALIZATION_CYCLE,
                RefusalCode.FALLBACK_PROFILE_UNKNOWN,
                RefusalCode.FALLBACK_PROFILE_NOT_LOCKED,
                RefusalCode.FALLBACK_PROFILE_NOT_ROLE_NEUTRAL,
                RefusalCode.FALLBACK_PROFILE_NOT_AGENT,
                RefusalCode.ROLE_PIN_UNKNOWN,
                RefusalCode.ROLE_PIN_NOT_FITTING,
                RefusalCode.ROLE_PIN_CONFLICT,
                RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD,
                RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                RefusalCode.PACK_HAS_DEPENDENTS)) {
            checks.put(code, violation(code.token()));
        }
        return Collections.unmodifiableMap(checks);
    }

    private static Set<ReasonCode> all() {
        Set<ReasonCode> all = new LinkedHashSet<>(CONTENT);
        all.addAll(CLOSURE);
        all.addAll(CLOSURE_CHECKS.values());
        return Set.copyOf(all);
    }
}
