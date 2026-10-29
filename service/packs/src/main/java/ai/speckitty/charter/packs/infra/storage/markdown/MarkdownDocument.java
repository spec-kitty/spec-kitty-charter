package ai.speckitty.charter.packs.infra.storage.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A Markdown document as the {@code markdown} content form models it (ADR-0040, research D6): optional frontmatter,
 * an optional title (the H1), a preamble (the text before the first H2) and the H2 sections in order.
 *
 * <p>Two documents are equal in meaning exactly when their models are equal: the frontmatter compares as loaded YAML
 * values (mappings, lists and scalars, so {@code Map.equals} on the loaded values is the YAML meaning), every string
 * compares exactly. The blank lines between blocks and the final newline are not part of the model, so documents
 * that differ only there are equal. An absent frontmatter and an empty one ({@code ---} directly followed by
 * {@code ---}) are different models.
 *
 * <p>Construction invariants, refused with an {@link IllegalArgumentException} naming the field: the title and every
 * section title are a single line, not blank, without leading or trailing whitespace; the preamble and every section's
 * content have no leading or trailing blank line, contain no carriage return, and could stand inside a document
 * unchanged, judged by the scanner's own line rules ({@link MarkdownScanner}): no column-0 H1 or H2 outside a fence,
 * no fence left open, nothing the scanner refuses as ambiguous. The empty document is valid.
 *
 * @param frontmatter the frontmatter mapping as loaded YAML, if the document has one; copied, unmodifiable at its top
 *     level
 * @param title the H1's text, if the document has an H1
 * @param preamble the text after the H1 (or the frontmatter, or the start) and before the first H2; may be empty
 * @param sections the H2 sections in document order; copied, unmodifiable
 */
public record MarkdownDocument(
        Optional<Map<String, Object>> frontmatter, Optional<String> title, String preamble, List<Section> sections) {

    /**
     * Checks the invariants and copies the frontmatter and sections.
     *
     * @param frontmatter the frontmatter mapping, if any
     * @param title the H1's text, if any
     * @param preamble the preamble
     * @param sections the H2 sections
     */
    public MarkdownDocument {
        Objects.requireNonNull(frontmatter, "frontmatter");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(preamble, "preamble");
        Objects.requireNonNull(sections, "sections");
        frontmatter = frontmatter.map(mapping -> Collections.unmodifiableMap(new LinkedHashMap<>(mapping)));
        title.ifPresent(t -> requireTitle("title", t));
        requireBlock("preamble", preamble);
        sections = List.copyOf(sections);
    }

    /**
     * One H2 section.
     *
     * @param title the H2's text
     * @param content the lines after the H2 up to the next H2 or the end, without leading or trailing blank lines;
     *     may be empty
     */
    public record Section(String title, String content) {
        /**
         * Checks the invariants.
         *
         * @param title the H2's text
         * @param content the section's content
         */
        public Section {
            requireTitle("section title", title);
            requireBlock("section content", content);
        }
    }

    /**
     * The empty document: no frontmatter, no title, an empty preamble, no sections. An empty file reads as it.
     *
     * @return the empty document
     */
    public static MarkdownDocument empty() {
        return new MarkdownDocument(Optional.empty(), Optional.empty(), "", List.of());
    }

    /**
     * Where two documents differ, as model paths: {@code frontmatter}, {@code title}, {@code preamble},
     * {@code sections[i].title}, {@code sections[i].content}, and {@code sections[i]} for a section only one side has.
     *
     * @param a one document
     * @param b the other
     * @return the differing paths in model order; empty exactly when the documents are equal
     */
    public static List<String> differences(MarkdownDocument a, MarkdownDocument b) {
        List<String> paths = new ArrayList<>();
        if (!a.frontmatter().equals(b.frontmatter())) {
            paths.add("frontmatter");
        }
        if (!a.title().equals(b.title())) {
            paths.add("title");
        }
        if (!a.preamble().equals(b.preamble())) {
            paths.add("preamble");
        }
        int common = Math.min(a.sections().size(), b.sections().size());
        for (int i = 0; i < common; i++) {
            Section left = a.sections().get(i);
            Section right = b.sections().get(i);
            if (!left.title().equals(right.title())) {
                paths.add("sections[" + i + "].title");
            }
            if (!left.content().equals(right.content())) {
                paths.add("sections[" + i + "].content");
            }
        }
        for (int i = common; i < Math.max(a.sections().size(), b.sections().size()); i++) {
            paths.add("sections[" + i + "]");
        }
        return List.copyOf(paths);
    }

    private static void requireTitle(String field, String title) {
        Objects.requireNonNull(title, field);
        if (title.indexOf('\n') >= 0 || title.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must be a single line");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!title.strip().equals(title)) {
            throw new IllegalArgumentException(field + " must not have leading or trailing whitespace");
        }
    }

    private static void requireBlock(String field, String block) {
        Objects.requireNonNull(block, field);
        if (block.isEmpty()) {
            return;
        }
        if (block.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must not contain a carriage return");
        }
        String[] lines = block.split("\n", -1);
        if (lines[0].isBlank()) {
            throw new IllegalArgumentException(field + " must not start with a blank line");
        }
        if (lines[lines.length - 1].isBlank()) {
            throw new IllegalArgumentException(field + " must not end with a blank line");
        }
        MarkdownScanner.blockProblem(block).ifPresent(problem -> {
            throw new IllegalArgumentException(field + " cannot stand inside a document: " + problem);
        });
    }
}
