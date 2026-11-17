package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One thing {@link PackPolicy} found in a pack.
 *
 * @param code what was found: a {@link PackCodes} constant, or one of {@link PackFindingCode}'s staging view
 * @param file the pack-relative path of the file the finding belongs to
 * @param place the pointer segments inside the file; empty for the whole file
 * @param detail a sentence for the reader; it names pack-relative paths only
 */
public record PackFinding(ReasonCode code, String file, List<String> place, String detail) {
    /**
     * Checks the fields and copies the place.
     *
     * @param code what was found
     * @param file the pack-relative path
     * @param place the pointer segments
     * @param detail the sentence for the reader
     */
    public PackFinding {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(detail, "detail");
        place = List.copyOf(place);
    }

    /**
     * The finding as a kernel reason, with its place as a JSON pointer into its file (RFC 6901): how the one reader
     * reports a read fault (#554, FR-016). Staging inside the single pull request (R4 b): it goes with this record
     * (WP13).
     *
     * @return the failing reason
     */
    public Reason reason() {
        Location at = Location.in(file);
        if (!place.isEmpty()) {
            StringBuilder pointer = new StringBuilder();
            place.forEach(segment ->
                    pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1")));
            at = at.at(pointer.toString());
        }
        return new Reason(code.value(), Evaluation.FAIL, code, at, detail);
    }

    /**
     * The finding a kernel reason states: the inverse of {@link #reason()}, so validate mode reads the one reader's
     * read faults in its own shape until it judges the readout's rationale itself (#554, WP09). Staging inside the
     * single pull request (R4 b): it goes with this record (WP13).
     *
     * @param reason a reason with a file as its location
     * @return the finding
     */
    public static PackFinding of(Reason reason) {
        List<String> place = new ArrayList<>();
        String pointer = reason.location().pointer();
        if (!pointer.isEmpty()) {
            for (String segment : pointer.substring(1).split("/", -1)) {
                place.add(segment.replace("~1", "/").replace("~0", "~"));
            }
        }
        return new PackFinding(reason.code(), reason.location().file(), place, reason.detail());
    }

    /**
     * The severity of the finding, which its code fixes.
     *
     * @return the severity
     */
    public Severity severity() {
        return code.severity();
    }

    /**
     * The refusal this finding projects onto, for the load path.
     *
     * @return the refusal, with the finding's detail
     * @throws IllegalStateException when the code has no wire refusal
     */
    public CharterRefusal toRefusal() {
        return new CharterRefusal(PackFindingCode.refusal(code), detail);
    }

    /**
     * The diagnostic this finding projects onto, for the load path.
     *
     * @return the diagnostic, with the finding's detail
     * @throws IllegalStateException when the code has no wire diagnostic
     */
    public Diagnostic toDiagnostic() {
        return new Diagnostic(
                PackFindingCode.diagnostic(code)
                        .orElseThrow(
                                () -> new IllegalStateException(code.value() + " has no diagnostic on the load path")),
                detail);
    }
}
