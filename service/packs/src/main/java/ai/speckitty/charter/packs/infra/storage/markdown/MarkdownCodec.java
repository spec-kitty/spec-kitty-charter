package ai.speckitty.charter.packs.infra.storage.markdown;

import ai.speckitty.charter.packs.infra.storage.FrontmatterYaml;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument.Section;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownScanner.Kind;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownScanner.Line;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * Reads Markdown text into a {@link MarkdownDocument} and writes a document back in its canonical form.
 *
 * <p><b>Reading</b> (research D6, D12). The text must not start with a byte order mark or contain a carriage return.
 * Frontmatter exists only when line 1 is exactly {@code ---}; it ends at the next line that is exactly {@code ---},
 * and its text must load as a YAML mapping with string keys (blank frontmatter is the empty mapping). The rest is
 * scanned by {@link MarkdownScanner}'s line rules (fences, H1 and H2 at column 0, the ambiguity refusals). An H1 must be
 * the first block after the frontmatter, and there is at most one. The preamble is the text after the H1 (or after the
 * frontmatter, or from the start) up to the first H2; a section's content is the text after its H2 up to the next H2
 * or the end. Leading and trailing blank lines of the preamble and of each content are dropped (that is the canonical
 * form, not a refusal). The empty text is the {@link MarkdownDocument#empty() empty document}.
 *
 * <p><b>Writing</b>, the canonical form: the frontmatter, if present, as {@code ---}, the mapping as YAML in the
 * adapter's writer settings (nothing for the empty mapping), {@code ---}; then the blocks, each separated from the next
 * by one blank line: {@code # title} if present, the preamble if not empty, and for each section {@code ## title}
 * followed by its content if not empty. The frontmatter's closing {@code ---} is followed directly by the first block,
 * with no blank line, as every frontmatter-carrying Markdown file at the open-packs pin is written (SC-006). The text
 * ends in exactly one newline; the empty document writes as the empty string. {@link #write} reads its own output back
 * and fails unless it reads as the same document.
 *
 * <p>Deliberate divergences from CommonMark: an HTML block or comment is text, so a column-0 {@code ## } inside one is
 * read as a heading; a fence may open at any indentation; a tab is never indentation.
 */
public final class MarkdownCodec {

    private static final String DELIMITER = "---";
    private static final char BYTE_ORDER_MARK = '﻿';

    private MarkdownCodec() {}

    /** The outcome of reading Markdown text: the document, or why it is refused. */
    public sealed interface Outcome permits Parsed, MarkdownRefusal {}

    /**
     * Markdown text read into its model.
     *
     * @param document the document
     */
    public record Parsed(MarkdownDocument document) implements Outcome {
        /**
         * Checks the field.
         *
         * @param document the document
         */
        public Parsed {
            Objects.requireNonNull(document, "document");
        }
    }

    /**
     * Reads Markdown text.
     *
     * @param text the text
     * @return the document, or the refusal naming the line
     */
    public static Outcome parse(String text) {
        Objects.requireNonNull(text, "text");
        try {
            return new Parsed(read(text));
        } catch (MarkdownScanner.Refused e) {
            return e.refusal();
        }
    }

    /**
     * Writes a document in the canonical form, then reads the output back.
     *
     * @param document the document
     * @return the canonical text
     * @throws IllegalStateException when the output does not read back as the same document (a model the form cannot
     *     express, such as a title that ends in a closing {@code #} sequence)
     */
    public static String write(MarkdownDocument document) {
        Objects.requireNonNull(document, "document");
        String text = render(document);
        Outcome back = parse(text);
        if (back instanceof MarkdownRefusal refusal) {
            throw new IllegalStateException(
                    "The canonical form of the document does not read back: " + refusal.message());
        }
        MarkdownDocument reread = ((Parsed) back).document();
        if (!reread.equals(document)) {
            throw new IllegalStateException("The canonical form of the document reads back differently at "
                    + MarkdownDocument.differences(document, reread));
        }
        return text;
    }

    private static String render(MarkdownDocument document) {
        StringBuilder out = new StringBuilder();
        document.frontmatter().ifPresent(mapping -> {
            out.append(DELIMITER).append('\n');
            if (!mapping.isEmpty()) {
                out.append(FrontmatterYaml.dump(mapping));
            }
            out.append(DELIMITER).append('\n');
        });
        List<String> blocks = new ArrayList<>();
        document.title().ifPresent(title -> blocks.add("# " + title));
        if (!document.preamble().isEmpty()) {
            blocks.add(document.preamble());
        }
        for (Section section : document.sections()) {
            blocks.add("## " + section.title());
            if (!section.content().isEmpty()) {
                blocks.add(section.content());
            }
        }
        if (!blocks.isEmpty()) {
            out.append(String.join("\n\n", blocks)).append('\n');
        }
        return out.toString();
    }

    private static MarkdownDocument read(String text) {
        if (text.isEmpty()) {
            return MarkdownDocument.empty();
        }
        if (text.charAt(0) == BYTE_ORDER_MARK) {
            throw MarkdownScanner.refused(1, "a byte order mark is not admitted");
        }
        int cr = text.indexOf('\r');
        if (cr >= 0) {
            throw MarkdownScanner.refused(lineOf(text, cr), "a carriage return is not admitted");
        }
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        if (text.endsWith("\n")) {
            lines.removeLast();
        }
        int bodyStart = 0;
        Optional<Map<String, Object>> frontmatter = Optional.empty();
        if (lines.getFirst().equals(DELIMITER)) {
            int end = lines.subList(1, lines.size()).indexOf(DELIMITER) + 1;
            if (end == 0) {
                throw MarkdownScanner.refused(1, "unterminated frontmatter: no closing --- line");
            }
            frontmatter = Optional.of(frontmatter(String.join("\n", lines.subList(1, end))));
            bodyStart = end + 1;
        }
        return body(frontmatter, MarkdownScanner.scan(lines.subList(bodyStart, lines.size()), bodyStart + 1));
    }

    private static Map<String, Object> frontmatter(String yaml) {
        if (yaml.isBlank()) {
            return Map.of();
        }
        @Nullable Object loaded;
        try {
            loaded = FrontmatterYaml.load(yaml + "\n");
        } catch (YamlEngineException e) {
            int line = e instanceof MarkedYamlEngineException marked
                    ? marked.getProblemMark().map(mark -> mark.getLine() + 2).orElse(1)
                    : 1;
            throw MarkdownScanner.refused(line, "frontmatter is not valid YAML: " + firstLine(e.getMessage()));
        }
        if (!(loaded instanceof Map<?, ?> mapping)) {
            throw MarkdownScanner.refused(1, "frontmatter must be a YAML mapping");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw MarkdownScanner.refused(1, "frontmatter keys must be strings, but one is " + entry.getKey());
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static MarkdownDocument body(Optional<Map<String, Object>> frontmatter, List<Line> lines) {
        Optional<String> title = Optional.empty();
        int titleLine = 0;
        boolean anyBlock = false;
        List<String> preamble = new ArrayList<>();
        List<String> current = preamble;
        List<String> sectionTitles = new ArrayList<>();
        List<List<String>> sectionLines = new ArrayList<>();
        for (Line line : lines) {
            Kind kind = line.kind();
            if (kind == Kind.H1) {
                if (title.isPresent()) {
                    throw MarkdownScanner.refused(
                            line.number(), "a second H1 (the first is at line " + titleLine + ")");
                }
                if (anyBlock) {
                    throw MarkdownScanner.refused(line.number(), "an H1 that is not the first block");
                }
                title = Optional.of(line.title());
                titleLine = line.number();
                anyBlock = true;
            } else if (kind == Kind.H2) {
                current = new ArrayList<>();
                sectionTitles.add(line.title());
                sectionLines.add(current);
                anyBlock = true;
            } else {
                current.add(line.text());
                anyBlock |= kind != Kind.BLANK;
            }
        }
        List<Section> sections = new ArrayList<>(sectionTitles.size());
        for (int i = 0; i < sectionTitles.size(); i++) {
            sections.add(new Section(sectionTitles.get(i), trimmed(sectionLines.get(i))));
        }
        return new MarkdownDocument(frontmatter, title, trimmed(preamble), sections);
    }

    /** The lines joined with newlines, without leading or trailing blank lines. */
    private static String trimmed(List<String> lines) {
        int from = 0;
        int to = lines.size();
        while (from < to && lines.get(from).isBlank()) {
            from++;
        }
        while (to > from && lines.get(to - 1).isBlank()) {
            to--;
        }
        return String.join("\n", lines.subList(from, to));
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String firstLine(@Nullable String message) {
        if (message == null) {
            return "unreadable";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
