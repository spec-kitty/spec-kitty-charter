package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackCodes;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * The pack refusals of the API edge: one mapping per edge.
 *
 * <ul>
 *   <li>The read services': a pack the storage cannot read (its aggregate or its revision) is, to the caller, a pack
 *       that is not there ({@link #packRefusal}, {@link #asPackNotFound}). The original {@code PACK_UNREADABLE} refusal
 *       is kept as the cause so the inbound edge can leave a diagnostic trail although the 404 body names nothing of
 *       it; any other refusal passes through, an identifier no configured pack answers to being already refused
 *       {@code PACK_NOT_FOUND} by the storage.
 *   <li>A registration's (#554, FR-008, S6): the verdict of the one read, as the domain port {@code PackReading}
 *       returns it, is the refusal of the upload ({@link #of}) and the diagnostics of an accepted one
 *       ({@link #diagnostics}). It keeps the order it is given, which is registration's, and names no storage code: it
 *       reads the verdict's failures only.
 * </ul>
 */
final class PackRefusals {

    private PackRefusals() {}

    /**
     * The refusal of an upload whose verdict fails: {@code validation_failed}, one violation per violation of the
     * verdict, in its order, each at its file in the one wording ({@link PackCodes#detail}), and the problem detail
     * {@code The pack cannot be registered: N fault(s), the first at <file>.}
     *
     * @param verdict the verdict of the one read
     * @return the refusal
     * @throws IllegalArgumentException when the verdict passes
     */
    static CharterRefusal of(Rationale verdict) {
        List<RefusalViolation> violations =
                verdict.violations().stream().map(PackRefusals::violation).toList();
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
     * The diagnostics of an accepted upload: one per warning of the verdict, in its order (the load's warnings, then
     * the files not held in path order), each with the diagnostic code its token spells.
     *
     * @param verdict the verdict of the one read
     * @return the diagnostics
     * @throws IllegalStateException for a warning no diagnostic code spells
     */
    static List<Diagnostic> diagnostics(Rationale verdict) {
        return verdict.warnings().stream().map(PackRefusals::diagnostic).toList();
    }

    private static RefusalViolation violation(Reason reason) {
        String file = reason.location().file();
        return new RefusalViolation(file, reason.code().value(), PackCodes.detail(file, reason.detail()));
    }

    private static Diagnostic diagnostic(Reason reason) {
        Diagnostic.Code code = Arrays.stream(Diagnostic.Code.values())
                .filter(candidate -> candidate.token().equals(reason.code().value()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(reason.code().value() + " is a warning no diagnostic code spells"));
        return new Diagnostic(code, PackCodes.detail(reason.location().file(), reason.detail()));
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

    /**
     * Runs a pack read, mapping its refusal as {@link #packRefusal} does.
     *
     * @param pack the pack read
     * @param load the read
     * @param <T> what it returns
     * @return what the read returns
     * @throws CharterRefusal the mapped refusal
     */
    static <T> T asPackNotFound(PackIdentifier pack, Supplier<T> load) {
        try {
            return load.get();
        } catch (CharterRefusal refused) {
            throw packRefusal(pack, refused);
        }
    }
}
