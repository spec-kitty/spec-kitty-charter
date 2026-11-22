package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.Arrays;
import java.util.List;

/**
 * The one mapping of a pack's verdict to what its caller answers (#478, S3; #554, FR-008, S6): the refusal of a verdict
 * that fails ({@link #of}) and the diagnostics of one that passes ({@link #diagnostics}). Registration
 * ({@code PackRefusals}) and pack-doctor both call it, so a pack the service would refuse is refused in the same words
 * wherever it is checked. It keeps the order it is given, which is registration's, and names no storage code: it reads
 * the verdict's failures only.
 */
public final class PackRefusalMapping {

    private PackRefusalMapping() {}

    /**
     * The refusal of a pack whose verdict fails: {@code validation_failed}, one violation per violation of the verdict,
     * in its order, each at its file in the one wording ({@link PackCodes#detail}), and the problem detail
     * {@code The pack cannot be registered: N fault(s), the first at <file>.}
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
}
