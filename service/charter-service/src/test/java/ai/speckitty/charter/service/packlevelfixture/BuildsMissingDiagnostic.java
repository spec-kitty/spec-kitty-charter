package ai.speckitty.charter.service.packlevelfixture;

import ai.speckitty.charter.domain.Diagnostic;

/** Route A, the positive control: reads the wire diagnostic code that only {@code PackPolicy} may name. */
public final class BuildsMissingDiagnostic {

    private BuildsMissingDiagnostic() {}

    /** The code a class outside the policy must not name. */
    public static Diagnostic.Code code() {
        return Diagnostic.Code.PACK_LEVEL_MISSING;
    }
}
