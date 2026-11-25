package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.Arrays;
import java.util.List;

/**
 * The one mapping of a pack's verdict to what its caller answers (#478, S3; #554, FR-008, S6): the refusal of a verdict
 * that fails ({@link #of}), the diagnostics of one that passes ({@link #diagnostics}), and the refusal of a pack whose
 * graph documents cannot be read ({@link #graphUnreadable}, #617). Registration ({@code PackRefusals}), pack-doctor and
 * the reads that refuse an unreadable graph document all call it, so a pack the service would refuse is refused in the
 * same words wherever it is checked. It keeps the order it is given, which is registration's, and names no storage
 * code: it reads the verdict's failures only.
 */
public final class PackRefusalMapping {

    private PackRefusalMapping() {}

    /**
     * The refusal of a pack whose verdict fails: {@code validation_failed}, one violation per violation of the verdict,
     * in its order, each at its file in the one wording ({@link PackCodes#detail}), and the problem detail
     * {@code The pack cannot be registered: N fault(s), the first at <pointer>.}
     *
     * @param verdict the verdict of the one read
     * @return the refusal
     * @throws IllegalArgumentException when the verdict passes
     */
    public static CharterRefusal of(Rationale verdict) {
        List<RefusalViolation> violations = violations(verdict);
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("a verdict with no violation refuses nothing");
        }
        return CharterRefusal.withViolations(
                RefusalCode.VALIDATION_FAILED,
                "The pack cannot be registered: " + violations.size() + " fault(s), the first at "
                        + violations.getFirst().pointer() + ".",
                violations);
    }

    /**
     * The refusal of a pack whose graph documents cannot all be read (#617, #628): {@code pack_unreadable}, one
     * violation per violation of the graph faults, in their order, in the words {@link #of} gives a registration of the
     * same pack, and the problem detail
     * {@code a graph document of <pack> cannot be read: <pack>/<file>: <reason>}, naming the first. The edge read, the
     * composing read and removal's authored read all refuse through here, so a broken pack is refused alike by each and
     * its author reads the same violations a registration lists.
     *
     * @param pack the pack, as the refusal names it
     * @param faults the graph faults, each at its pack-relative document with its reason
     * @return the refusal
     * @throws IllegalArgumentException when the faults hold no violation
     */
    public static CharterRefusal graphUnreadable(PackIdentifier pack, Rationale faults) {
        Reason first = faults.violations().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("graph faults with no violation refuse nothing"));
        return CharterRefusal.withViolations(
                RefusalCode.PACK_UNREADABLE,
                "a graph document of " + pack.value() + " cannot be read: " + pack.value() + "/"
                        + first.location().file() + ": " + first.detail(),
                violations(faults));
    }

    /**
     * The violations of a verdict, one per violation in its order, each at its file in the one wording
     * ({@link PackCodes#detail}): the violations {@link #of} refuses with. A surface that words its own problem around
     * them (pack-doctor's round trip, #478) maps them here, so a violation reads the same wherever it is reported.
     *
     * @param verdict a verdict
     * @return its violations; empty when it has none
     */
    public static List<RefusalViolation> violations(Rationale verdict) {
        return verdict.violations().stream().map(PackRefusalMapping::violation).toList();
    }

    /**
     * The diagnostics of an accepted pack: one per warning of the verdict, in its order (the load's warnings, then the
     * files not held in path order), each with the diagnostic code its token spells.
     *
     * @param verdict the verdict of the one read
     * @return the diagnostics
     * @throws IllegalStateException for a warning no diagnostic code spells
     */
    public static List<Diagnostic> diagnostics(Rationale verdict) {
        return verdict.warnings().stream().map(PackRefusalMapping::diagnostic).toList();
    }

    /**
     * A pack-file finding as the wire lists it: at its {@linkplain #pointer pointer}, with its code and its detail led by
     * its file. A registration and a per-file write refused because its pack would not revalidate state a violation
     * through this one mapping (#654).
     *
     * @param reason the finding
     * @return the violation
     */
    public static RefusalViolation violation(Reason reason) {
        String file = reason.location().file();
        return new RefusalViolation(
                pointer(reason.location()), reason.code().value(), PackCodes.detail(file, reason.detail()));
    }

    /**
     * The wire pointer of a pack-file violation (#617 follow-up): a URI reference, the pack-relative file, then
     * {@code #} and the RFC 6901 JSON pointer into it when the place inside the file is known, for example
     * {@code drg/directive.graph.yaml#/edges/3}; the bare file when it is not.
     *
     * @param location where the violation is
     * @return the pointer the violation is listed at
     */
    static String pointer(Location location) {
        return location.pointer().isEmpty() ? location.file() : location.file() + "#" + location.pointer();
    }

    private static Diagnostic diagnostic(Reason reason) {
        Diagnostic.Code code = Arrays.stream(Diagnostic.Code.values())
                .filter(candidate -> candidate.token().equals(reason.code().value()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(reason.code().value() + " is a warning no diagnostic code spells"));
        return new Diagnostic(code, PackCodes.detail(reason.location().file(), reason.detail()));
    }
}
