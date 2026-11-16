package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
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
