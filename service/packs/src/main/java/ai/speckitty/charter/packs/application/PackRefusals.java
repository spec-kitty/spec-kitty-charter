package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RequiringPack;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ClosureVerdict;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.ImpactCheck;
import ai.speckitty.charter.packs.domain.PackRefusalMapping;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The pack refusals of the API edge: one mapping per edge.
 *
 * <ul>
 *   <li>The read services': a pack the storage cannot read (its aggregate or its revision) is, to the caller, a pack
 *       that is not there ({@link #packRefusal}). The original {@code PACK_UNREADABLE} refusal
 *       is kept as the cause so the inbound edge can leave a diagnostic trail although the 404 body names nothing of
 *       it; any other refusal passes through, an identifier no configured pack answers to being already refused
 *       {@code PACK_NOT_FOUND} by the storage.
 *   <li>A registration's (#554, FR-008, S6): the verdict of the one read, as the domain port {@code PackReading}
 *       returns it, is the refusal of the upload ({@link #of}) and the diagnostics of an accepted one
 *       ({@link #diagnostics}), through the domain's one mapping ({@link PackRefusalMapping}, #478 S3), which
 *       pack-check calls too.
 *   <li>The closure checks' (#556, ADR-0075 decision 6, D1): the verdict of a write's closure checks is the write's one
 *       refusal ({@link #of(ClosureVerdict)}), the closure edge's mapper.
 * </ul>
 */
public final class PackRefusals {

    private PackRefusals() {}

    /**
     * The refusal of an upload whose verdict fails ({@link PackRefusalMapping#of}).
     *
     * @param verdict the verdict of the one read
     * @return the refusal
     * @throws IllegalArgumentException when the verdict passes
     */
    static CharterRefusal of(Rationale verdict) {
        return PackRefusalMapping.of(verdict);
    }

    /**
     * The diagnostics of an accepted upload ({@link PackRefusalMapping#diagnostics}).
     *
     * @param verdict the verdict of the one read
     * @return the diagnostics
     * @throws IllegalStateException for a warning no diagnostic code spells
     */
    static List<Diagnostic> diagnostics(Rationale verdict) {
        return PackRefusalMapping.diagnostics(verdict);
    }

    /**
     * The one refusal of a write whose closure checks fail (#556, D1).
     *
     * <p>One fault is refused exactly as its check refuses it alone: its code, detail, packs, violations and the packs
     * that require it. Several faults are refused as follows:
     *
     * <ul>
     *   <li>the code is the first fault's, in the order the checks judged them;
     *   <li>the violations are, fault by fault, a header {@code ("", code, detail)} and then the fault's own violations,
     *       so each fault's code stays named;
     *   <li>the packs and the packs that require it are each the union, in the order first seen;
     *   <li>the detail is the first fault's, followed by {@code (N fault(s); every one is in violations)}.
     * </ul>
     *
     * @param verdict the closure checks' verdict
     * @return the refusal
     * @throws IllegalArgumentException when the verdict passes
     */
    public static CharterRefusal of(ClosureVerdict verdict) {
        List<ClosureFault> faults = verdict.faults();
        if (faults.isEmpty()) {
            throw new IllegalArgumentException("a verdict with no fault refuses nothing");
        }
        ClosureFault first = faults.getFirst();
        if (faults.size() == 1) {
            return CharterRefusal.of(
                    first.code(), first.detail(), first.packs(), first.violations(), first.requiredBy());
        }
        Set<RefusalPack> packs = new LinkedHashSet<>();
        Set<RequiringPack> requiredBy = new LinkedHashSet<>();
        List<RefusalViolation> violations = new ArrayList<>();
        for (ClosureFault fault : faults) {
            packs.addAll(fault.packs());
            requiredBy.addAll(fault.requiredBy());
            violations.add(new RefusalViolation("", fault.code().token(), fault.detail()));
            violations.addAll(fault.violations());
        }
        return CharterRefusal.of(
                first.code(),
                first.detail() + " (" + faults.size() + " fault(s); every one is in violations)",
                List.copyOf(packs),
                violations,
                List.copyOf(requiredBy));
    }

    /**
     * The notices of a write the impact check passed, or the write's one refusal when it did not (#556).
     *
     * @param impact what the impact check answered
     * @return the floor and lock changes of the write
     * @throws CharterRefusal every fault of the verdict, by {@link #of(ClosureVerdict)}, when it fails
     */
    public static ImpactCheck.Notices noticesOrRefuse(ImpactCheck.Impact impact) {
        if (!impact.verdict().isPassing()) {
            throw of(impact.verdict());
        }
        return impact.notices();
    }

    /**
     * Passes when the verdict passes, or refuses the write with every fault of it (#556).
     *
     * @param verdict the check's verdict
     * @throws CharterRefusal every fault of the verdict, by {@link #of(ClosureVerdict)}, when it fails
     */
    public static void require(ClosureVerdict verdict) {
        if (!verdict.isPassing()) {
            throw of(verdict);
        }
    }

    /**
     * The refusal the caller sees for one a pack read raised.
     *
     * @param pack the pack read
     * @param refused the refusal the read raised
     * @return {@code PACK_NOT_FOUND} caused by {@code refused} when it is {@code PACK_UNREADABLE}, else {@code refused}
     */
    static CharterRefusal packRefusal(PackIdentifier pack, CharterRefusal refused) {
        if (refused.code() != RefusalCode.PACK_UNREADABLE) {
            return refused;
        }
        return new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.", refused);
    }
}
