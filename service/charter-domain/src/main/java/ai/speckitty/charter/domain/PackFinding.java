package ai.speckitty.charter.domain;

import java.util.List;
import java.util.Objects;

/**
 * One thing {@link PackPolicy} found in a pack.
 *
 * @param code what was found
 * @param file the pack-relative path of the file the finding belongs to
 * @param place the pointer segments inside the file; empty for the whole file
 * @param detail a sentence for the reader; it names pack-relative paths only
 */
public record PackFinding(PackFindingCode code, String file, List<String> place, String detail) {
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
    public PackFindingCode.Severity severity() {
        return code.severity();
    }

    /**
     * The refusal this finding projects onto, for the load path.
     *
     * @return the refusal, with the finding's detail
     * @throws IllegalStateException when the code has no wire refusal
     */
    public CharterRefusal toRefusal() {
        return new CharterRefusal(code.refusal(), detail);
    }

    /**
     * The diagnostic this finding projects onto, for the load path.
     *
     * @return the diagnostic, with the finding's detail
     * @throws IllegalStateException when the code has no wire diagnostic
     */
    public Diagnostic toDiagnostic() {
        return new Diagnostic(code.diagnostic(), detail);
    }
}
