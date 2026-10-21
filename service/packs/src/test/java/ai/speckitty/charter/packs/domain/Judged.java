package ai.speckitty.charter.packs.domain;

import java.util.List;

/** Test view of {@link PackPolicy#judge}: what a caller sees if it refuses on the first blocking finding. */
final class Judged {

    private Judged() {}

    /**
     * Throws the refusal of the first blocking finding, in the order {@link PackPolicy#judge} reports them.
     *
     * @param contents what the adapter read
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal when a finding blocks
     */
    static void refuseOnFirstBlocking(PackContents contents) {
        for (PackFinding finding : PackPolicy.judge(contents).findings()) {
            if (finding.severity() == PackFindingCode.Severity.BLOCKING) {
                throw finding.toRefusal();
            }
        }
    }

    /**
     * The findings as diagnostics.
     *
     * @param contents what the adapter read
     * @return one diagnostic per finding, in report order
     */
    static List<Diagnostic> diagnostics(PackContents contents) {
        return PackPolicy.judge(contents).findings().stream()
                .map(PackFinding::toDiagnostic)
                .toList();
    }
}
