package ai.speckitty.charter.packs.domain.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The line rules of the {@code markdown} form (research D6, D12), shared by the parser and by the model's
 * construction invariants so that both apply one rule.
 *
 * <p>A line is, in this order: part of a fence (opener, body or closer); blank; a heading (H1 or H2 at column 0); or
 * text. Fences follow CommonMark: a line whose first non-space characters are at least three backticks or at least
 * three tildes opens a fence at any indentation (a backtick opener's info string holds no backtick); the fence closes
 * on a line whose first non-space characters are the opener's character repeated at least as many times, followed by
 * nothing but spaces. While a fence is open no line is a heading. Refused as ambiguous: an H1 or H2 with 1 to 3
 * leading spaces, an empty H1 or H2, one separated from its text by a tab, one with a closing {@code #} sequence, a
 * setext underline ({@code =} or {@code -} line directly under a paragraph line), and a fence still open at the end.
 * H3 and deeper, a thematic break after a blank line, HTML, lists, blockquotes and tables are text.
 */
public final class MarkdownScanner {

    /** A setext underline: up to three spaces, a run of {@code =} or of {@code -}, then only spaces or tabs. */
    private static final Pattern SETEXT_UNDERLINE = Pattern.compile("^ {0,3}(?:=+|-+)[ \\t]*$");

    /** A heading text that ends in a closing sequence: only {@code #}s, or a space then {@code #}s. */
    private static final Pattern CLOSING_SEQUENCE = Pattern.compile("^(?:#+|.*[ \\t]#+)$");

    private static final int MAX_HEADING_INDENT = 3;
    private static final int MAX_ATX_LEVEL = 6;
    private static final int MIN_FENCE_LENGTH = 3;
    private static final int MIN_THEMATIC_BREAK_LENGTH = 3;

    private MarkdownScanner() {}

    /** What the scanner reads a line as. */
    public enum Kind {
        /** A fence opener, a line inside a fence, or a fence closer. */
        FENCED,
        /** An empty or whitespace-only line outside a fence. */
        BLANK,
        /** A column-0 {@code # } heading outside a fence. */
        H1,
        /** A column-0 {@code ## } heading outside a fence. */
        H2,
        /** Any other line. */
        TEXT
    }

    /**
     * One scanned line.
     *
     * @param number the 1-based line number in the scanned text
     * @param text the line, without its newline
     * @param kind what the scanner reads it as
     * @param title the heading's title for {@link Kind#H1} and {@link Kind#H2}, stripped; empty otherwise
     */
    public record Line(int number, String text, Kind kind, String title) {
        /**
         * Checks the fields.
         *
         * @param number the 1-based line number in the scanned text
         * @param text the line, without its newline
         * @param kind what the scanner reads it as
         * @param title the heading's title, or empty
         */
        public Line {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(title, "title");
        }
    }

    /**
     * A scan stopped by a refusal: the text is ambiguous at a named line. The codec reads it as its refusal; the model
     * reads it as the problem of a block.
     */
    public static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** The 1-based line the refusal names. */
        private final int line;

        /** Why the text is refused: a short, stable phrase. */
        private final String reason;

        /**
         * A stop at a line.
         *
         * @param line the 1-based line
         * @param reason why
         */
        Refused(int line, String reason) {
            super("line " + line + ": " + reason, null, false, false);
            this.line = line;
            this.reason = reason;
        }

        /**
         * The line the refusal names.
         *
         * @return the 1-based line
         */
        public int line() {
            return line;
        }

        /**
         * Why the text is refused.
         *
         * @return the reason, used verbatim in violations
         */
        public String reason() {
            return reason;
        }
    }

    /**
     * An open fence.
     *
     * @param marker the fence character, a backtick or a tilde
     * @param length how many marker characters opened it
     * @param line the 1-based line of the opener
     */
    record Fence(char marker, int length, int line) {}

    /** Tracks whether a fence is open across successive lines; the one fence rule of the form. */
    static final class FenceTracker {
        private @Nullable Fence open;

        /** A tracker with no fence open. */
        FenceTracker() {}

        /**
         * Feeds the next line.
         *
         * @param line the line, without its newline
         * @param number its 1-based line number
         * @return whether the line belongs to a fence (opener, body or closer)
         */
        boolean fenced(String line, int number) {
            Fence current = open;
            if (current != null) {
                if (closes(current, line)) {
                    open = null;
                }
                return true;
            }
            Fence opened = opener(line, number);
            if (opened != null) {
                open = opened;
                return true;
            }
            return false;
        }

        /**
         * The fence still open, if any.
         *
         * @return the open fence
         */
        Optional<Fence> open() {
            return Optional.ofNullable(open);
        }
    }

    /**
     * Scans lines with the form's line rules.
     *
     * @param lines the lines, without newlines
     * @param firstNumber the 1-based number of the first line
     * @return every line, classified
     * @throws Refused when a line is ambiguous, or a fence is still open at the end
     */
    public static List<Line> scan(List<String> lines, int firstNumber) {
        FenceTracker fences = new FenceTracker();
        List<Line> scanned = new ArrayList<>(lines.size());
        boolean underParagraph = false;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int number = firstNumber + i;
            if (fences.fenced(text, number)) {
                scanned.add(new Line(number, text, Kind.FENCED, ""));
                underParagraph = false;
                continue;
            }
            if (text.isBlank()) {
                scanned.add(new Line(number, text, Kind.BLANK, ""));
                underParagraph = false;
                continue;
            }
            Line heading = heading(text, number);
            if (heading != null) {
                scanned.add(heading);
                underParagraph = false;
                continue;
            }
            if (underParagraph && SETEXT_UNDERLINE.matcher(text).matches()) {
                throw refused(number, "a setext heading underline is ambiguous; use an ATX heading or a blank line");
            }
            scanned.add(new Line(number, text, Kind.TEXT, ""));
            underParagraph = !isThematicBreak(text) && !isDeeperHeading(text);
        }
        fences.open().ifPresent(fence -> {
            throw refused(fence.line(), "unclosed fence opened at line " + fence.line());
        });
        return scanned;
    }

    /**
     * Why a block of text (a preamble or a section's content) could not stand inside a document, if it could not.
     *
     * @param block the block
     * @return the problem, naming the block's own 1-based line
     */
    static Optional<String> blockProblem(String block) {
        try {
            for (Line line : scan(List.of(block.split("\n", -1)), 1)) {
                if (line.kind() == Kind.H1 || line.kind() == Kind.H2) {
                    return Optional.of("line " + line.number() + " is a column-0 "
                            + (line.kind() == Kind.H1 ? "H1" : "H2") + " heading outside a fence");
                }
            }
            return Optional.empty();
        } catch (Refused e) {
            return Optional.of(String.valueOf(e.getMessage()));
        }
    }

    /** The opener a line is, if it opens a fence. */
    static @Nullable Fence opener(String line, int number) {
        String rest = line.substring(leadingSpaces(line));
        if (rest.isEmpty()) {
            return null;
        }
        char marker = rest.charAt(0);
        if (marker != '`' && marker != '~') {
            return null;
        }
        int length = run(rest, marker);
        if (length < MIN_FENCE_LENGTH) {
            return null;
        }
        if (marker == '`' && rest.indexOf('`', length) >= 0) {
            return null;
        }
        return new Fence(marker, length, number);
    }

    /** Whether a line closes the fence: the same character, at least as many, then only spaces. */
    static boolean closes(Fence fence, String line) {
        String rest = line.substring(leadingSpaces(line));
        int length = run(rest, fence.marker());
        if (length < fence.length()) {
            return false;
        }
        for (int i = length; i < rest.length(); i++) {
            if (rest.charAt(i) != ' ') {
                return false;
            }
        }
        return true;
    }

    /** The H1 or H2 a non-fenced, non-blank line is; {@code null} when it is text (H3 and deeper included). */
    private static @Nullable Line heading(String text, int number) {
        int indent = leadingSpaces(text);
        if (indent > MAX_HEADING_INDENT) {
            return null;
        }
        String rest = text.substring(indent);
        int level = run(rest, '#');
        if (level == 0 || level > MAX_ATX_LEVEL) {
            return null;
        }
        String after = rest.substring(level);
        if (!after.isEmpty() && after.charAt(0) != ' ' && after.charAt(0) != '\t') {
            return null;
        }
        if (level > 2) {
            return null;
        }
        String name = level == 1 ? "an H1" : "an H2";
        if (indent > 0) {
            throw refused(
                    number,
                    name + " indented by " + indent + " space" + (indent == 1 ? "" : "s")
                            + " is ambiguous; start it at column 0");
        }
        if (after.isBlank()) {
            throw refused(number, "an empty heading is ambiguous");
        }
        if (after.charAt(0) == '\t') {
            throw refused(number, name + " separated from its text by a tab is ambiguous");
        }
        String title = after.strip();
        if (CLOSING_SEQUENCE.matcher(title).matches()) {
            throw refused(number, name + " with a closing # sequence is ambiguous");
        }
        return new Line(number, text, level == 1 ? Kind.H1 : Kind.H2, title);
    }

    /**
     * Whether a line is a thematic break: up to three spaces, then three or more of one of {@code - * _} with only
     * spaces or tabs between and after them. One pass over the line, so no line length can exhaust the stack (a
     * repeated regex group recursed once per marker).
     *
     * @param text the line, without its newline
     * @return whether it is a thematic break
     */
    static boolean isThematicBreak(String text) {
        int indent = leadingSpaces(text);
        if (indent > MAX_HEADING_INDENT || indent == text.length()) {
            return false;
        }
        char marker = text.charAt(indent);
        if (marker != '-' && marker != '*' && marker != '_') {
            return false;
        }
        int markers = 0;
        for (int i = indent; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == marker) {
                markers++;
            } else if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return markers >= MIN_THEMATIC_BREAK_LENGTH;
    }

    /** Whether a line is an ATX heading of level 3 to 6 (text for this form, not a paragraph line). */
    private static boolean isDeeperHeading(String text) {
        int indent = leadingSpaces(text);
        if (indent > MAX_HEADING_INDENT) {
            return false;
        }
        String rest = text.substring(indent);
        int level = run(rest, '#');
        return level > 2
                && level <= MAX_ATX_LEVEL
                && (rest.length() == level || rest.charAt(level) == ' ' || rest.charAt(level) == '\t');
    }

    /**
     * A refusal to throw.
     *
     * @param line the 1-based line it names
     * @param reason why the text is refused
     * @return the stop, to throw
     */
    public static Refused refused(int line, String reason) {
        return new Refused(line, reason);
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static int run(String text, char c) {
        int count = 0;
        while (count < text.length() && text.charAt(count) == c) {
            count++;
        }
        return count;
    }
}
