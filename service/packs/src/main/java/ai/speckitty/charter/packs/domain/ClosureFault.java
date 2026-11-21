package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RequiringPack;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One fault a closure check finds (#556, ADR-0075 decision 6): a kernel {@link Reason}, with the parts of a refusal a
 * reason cannot carry. Those are the packs it names, its own violations and the packs whose floor requires what it is
 * about. Every closure check (the {@code CLOSURE} composition and its preset phase, the compose phases, the relation
 * rules, the closure walk, the mutability policy and the dependents of a removal) states its faults in this one type
 * (#614: there is no other closure fault type), and the closure edge's mapper turns a list of them into the write's one
 * refusal.
 *
 * <p>A fault is built one way, {@link #of(Origin, RefusalCode, String, List)} (with its long form for the violations
 * and the requiring packs): the check that found it, as the reason's {@link Origin}, its code, its detail and its packs.
 * The reason's rule name is the origin's name, so the {@code CLOSURE} reasons keep their rule names
 * ({@code closure}, {@code closure.presets}).
 *
 * <p>Its code is one of {@link PackCodes#closureChecks()}, so each token is built once ({@code PACK_CODES_HAVE_ONE_OWNER}).
 * A read failure is never a fault ({@link #isReadFailure}): {@link #lift} leaves it, and a refusal of another check, to
 * propagate, so a caller rethrows what does not lift.
 *
 * @param reason the failing reason: the check that found it, the code, unplaced, and the detail
 * @param packs the packs the fault names, the one that broke the rule first
 * @param violations the fault's own violations, when its refusal carries some
 * @param requiredBy the packs whose floor requires what the fault is about, when its refusal names them
 */
public record ClosureFault(
        Reason reason, List<RefusalPack> packs, List<RefusalViolation> violations, List<RequiringPack> requiredBy) {

    /** The rule name of a closure check that names no phase of its own. */
    public static final String RULE = "closure";

    /**
     * The origin of a closure check that names no phase of its own (the compose phases, the relation rules, the closure
     * walk, the mutability policy, the dependents of a removal, and a lifted refusal): {@value #RULE}, the rule name its
     * reasons carry.
     */
    public static final Origin ORIGIN = new Origin(RULE);

    /**
     * Checks the fields and copies the lists.
     *
     * @param reason the failing reason, of a closure check's code
     * @param packs the packs it names
     * @param violations its own violations
     * @param requiredBy the packs whose floor requires it
     * @throws IllegalArgumentException when the reason passes or its code is no closure check's
     */
    public ClosureFault {
        Objects.requireNonNull(reason, "reason");
        packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        requiredBy = List.copyOf(Objects.requireNonNull(requiredBy, "requiredBy"));
        if (!reason.isFailing()) {
            throw new IllegalArgumentException("a closure fault is a failing reason");
        }
        PackCodes.refusalOf(reason.code());
    }

    /**
     * A fault a check found, naming packs, with no violations of its own.
     *
     * @param origin the check that found it; its name is the rule name the reason carries
     * @param code the refusal code, one of {@link PackCodes#closureChecks()}
     * @param detail what is wrong
     * @param packs the packs it names
     * @return the fault
     */
    public static ClosureFault of(Origin origin, RefusalCode code, String detail, List<RefusalPack> packs) {
        return of(origin, code, detail, packs, List.of(), List.of());
    }

    /**
     * A fault a check found, with every part of a refusal.
     *
     * @param origin the check that found it; its name is the rule name the reason carries
     * @param code the refusal code, one of {@link PackCodes#closureChecks()}
     * @param detail what is wrong
     * @param packs the packs it names
     * @param violations its own violations
     * @param requiredBy the packs whose floor requires it
     * @return the fault
     */
    public static ClosureFault of(
            Origin origin,
            RefusalCode code,
            String detail,
            List<RefusalPack> packs,
            List<RefusalViolation> violations,
            List<RequiringPack> requiredBy) {
        return new ClosureFault(reason(origin, code, detail), packs, violations, requiredBy);
    }

    /**
     * The same fault with another detail and other packs, its origin, code, violations and requiring packs kept: how a
     * caller places a fault in the closure it was found in (#556, US1.3).
     *
     * @param detail the detail
     * @param named the packs it names
     * @return the fault
     */
    public ClosureFault with(String detail, List<RefusalPack> named) {
        return new ClosureFault(reason(origin(), code(), detail), named, violations, requiredBy);
    }

    /**
     * A refusal a closure check raised, as a fault: its code, detail, packs, violations and the packs that require it,
     * from {@link #ORIGIN}. A refusal that is no closure check's (a read failure, or a code of another check) is not a
     * fault.
     *
     * @param refused the refusal
     * @return the fault; empty when the refusal must propagate as it is
     */
    public static Optional<ClosureFault> lift(CharterRefusal refused) {
        Objects.requireNonNull(refused, "refused");
        if (!PackCodes.isClosureCheck(refused.code()) || isReadFailure(refused)) {
            return Optional.empty();
        }
        return Optional.of(new ClosureFault(
                reason(ORIGIN, refused.code(), String.valueOf(refused.getMessage())),
                refused.packs(),
                refused.violations(),
                refused.requiredBy()));
    }

    /**
     * Whether a refusal says a pack could not be read: any {@code pack_unreadable} but the substep delegation's
     * ({@link #isUnresolvedDelegation()}), which is a fault of the closure.
     *
     * @param refused the refusal
     * @return true for a read failure
     */
    static boolean isReadFailure(CharterRefusal refused) {
        return refused.code() == RefusalCode.PACK_UNREADABLE && !unresolvedDelegation(refused.violations());
    }

    /**
     * Whether this is the substep delegation's {@code pack_unreadable}: every violation it carries is
     * {@value SubstepDelegationRule#UNRESOLVED}, which makes it a fault of the closure and no read failure.
     *
     * @return true for an unresolved delegation
     */
    public boolean isUnresolvedDelegation() {
        return code() == RefusalCode.PACK_UNREADABLE && unresolvedDelegation(violations);
    }

    private static boolean unresolvedDelegation(List<RefusalViolation> violations) {
        return !violations.isEmpty()
                && violations.stream().allMatch(violation -> SubstepDelegationRule.UNRESOLVED.equals(violation.code()));
    }

    /**
     * The check that found the fault.
     *
     * @return its reason's origin
     */
    public Origin origin() {
        return reason.origin();
    }

    /**
     * The refusal code of the fault.
     *
     * @return the code its reason's token spells
     */
    public RefusalCode code() {
        return PackCodes.refusalOf(reason.code());
    }

    /**
     * What is wrong, as the refusal says it.
     *
     * @return the detail
     */
    public String detail() {
        return reason.detail();
    }

    private static Reason reason(Origin origin, RefusalCode code, String detail) {
        Objects.requireNonNull(origin, "origin");
        return new Reason(origin.name(), origin, Evaluation.FAIL, PackCodes.closure(code), Location.NOWHERE, detail);
    }
}
