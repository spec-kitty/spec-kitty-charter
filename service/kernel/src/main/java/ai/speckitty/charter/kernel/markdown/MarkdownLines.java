package ai.speckitty.charter.kernel.markdown;

import java.util.Objects;
import java.util.Optional;

/**
 * The rules a text follows to stand in a Markdown document of the {@code markdown} form: as a heading's text, as a
 * block (a preamble or a section's content), and as the opening of a document with neither frontmatter nor a title.
 * Every domain value that holds such a text checks it with these rules when it is built (#271, #299), and the codec
 * applies the same rules when it reads a file, so a value that builds is a value the codec reads back.
 *
 * <p>Each rule answers the problem, worded to follow the field's name ({@code must be a single line}), or nothing when
 * the text can stand. The caller decides how a problem is refused.
 */
public final class MarkdownLines {

    private MarkdownLines() {}

    /**
     * Why a text cannot be a title (the H1's or an H2's text), if it cannot: it must be a single line, not blank,
     * without leading or trailing whitespace, and read back as the heading's text (no closing {@code #} sequence).
     *
     * @param title the text
     * @return the problem; empty when it can
     */
    public static Optional<String> headingProblem(String title) {
        Objects.requireNonNull(title, "title");
        if (title.indexOf('\n') >= 0 || title.indexOf('\r') >= 0) {
            return Optional.of("must be a single line");
        }
        if (title.isBlank()) {
            return Optional.of("must not be blank");
        }
        if (!title.strip().equals(title)) {
            return Optional.of("must not have leading or trailing whitespace");
        }
        return MarkdownScanner.headingProblem(title).map(problem -> "cannot be a heading's text: " + problem);
    }

    /**
     * Why a text cannot be a block (the preamble or a section's content), if it cannot: no carriage return, no leading
     * or trailing blank line, and it could stand inside a document unchanged by the scanner's line rules (no column-0
     * H1 or H2 outside a fence, no fence left open, nothing the scanner refuses as ambiguous). The empty block can.
     *
     * @param block the text
     * @return the problem; empty when it can
     */
    public static Optional<String> blockProblem(String block) {
        Objects.requireNonNull(block, "block");
        if (block.isEmpty()) {
            return Optional.empty();
        }
        if (block.indexOf('\r') >= 0) {
            return Optional.of("must not contain a carriage return");
        }
        String[] lines = block.split("\n", -1);
        if (lines[0].isBlank()) {
            return Optional.of("must not start with a blank line");
        }
        if (lines[lines.length - 1].isBlank()) {
            return Optional.of("must not end with a blank line");
        }
        return MarkdownScanner.blockProblem(block).map(problem -> "cannot stand inside a document: " + problem);
    }

    /**
     * Why a preamble cannot open the written text, if it cannot: when a document has neither frontmatter nor a title,
     * its preamble must not start with a {@code ---} line (it would read back as frontmatter) or a byte order mark.
     *
     * @param preamble the preamble of a document with neither frontmatter nor a title
     * @return the problem; empty when it can
     */
    public static Optional<String> openingProblem(String preamble) {
        Objects.requireNonNull(preamble, "preamble");
        if (preamble.equals("---") || preamble.startsWith("---\n")) {
            return Optional.of("must not start with a --- line when there is neither frontmatter nor a title:"
                    + " it would read back as frontmatter");
        }
        if (!preamble.isEmpty() && preamble.charAt(0) == '\uFEFF') {
            return Optional.of("must not start with a byte order mark when there is neither frontmatter nor a title");
        }
        return Optional.empty();
    }
}
