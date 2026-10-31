package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A pack's description, its {@code pack.md} (#260, ADR-0042 decision 8), in the shape of the {@code markdown} content
 * form's model (#194, ADR-0040 as amended): optional frontmatter, an optional title (the H1), a preamble (the text
 * before the first H2) and the H2 sections in order.
 *
 * <p>Reading and writing Markdown are the content form's, where the document is stored; the rules a part must keep to
 * stand in a document are the Markdown model's own ({@link MarkdownDocument}), and the document checks every part
 * against them when it is built (#271), so it refuses exactly the parts the codec refuses: a title or section title
 * that is not a single, non-blank, trimmed line or would not read back as a heading's text; a preamble or content
 * with a carriage return, a leading or trailing blank line, an H1 or H2 outside a fence, an open fence or anything
 * else the form refuses as ambiguous; a preamble that opens the file with a {@code ---} line or a byte order mark. Two
 * documents are equal exactly when every part is: the frontmatter as loaded YAML values, every string exactly. An
 * absent frontmatter and an empty one are different documents.
 *
 * @param frontmatter the frontmatter mapping as loaded YAML values, if the document has one: string keys; values that
 *     are strings, booleans, numbers, lists, string-keyed mappings or a YAML null (kept as a {@code null} value, as
 *     #194's model holds it); copied deeply, unmodifiable at every level
 * @param title the H1's text, if the document has one: a heading's text, by the Markdown model's rule
 * @param preamble the text after the title (or the frontmatter, or the start) and before the first H2; may be empty;
 *     a block, by the Markdown model's rule
 * @param sections the H2 sections in document order; copied, unmodifiable
 */
public record PackDocument(
        Optional<Map<String, Object>> frontmatter, Optional<String> title, String preamble, List<Section> sections) {

    /**
     * Checks the parts and copies the frontmatter and sections.
     *
     * @param frontmatter the frontmatter mapping, if any
     * @param title the H1's text, if any
     * @param preamble the preamble
     * @param sections the H2 sections
     * @throws ConstraintBreachException when the title, the preamble or the opening breaks the Markdown model's rules
     * @throws IllegalArgumentException when the frontmatter holds a key that is not a string or a value that is not a
     *     YAML value
     */
    public PackDocument {
        Objects.requireNonNull(frontmatter, "frontmatter");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(preamble, "preamble");
        Objects.requireNonNull(sections, "sections");
        frontmatter = frontmatter.map(PackDocument::frozen);
        title.ifPresent(t -> guard("title", MarkdownDocument.headingProblem(t)));
        guard("preamble", MarkdownDocument.blockProblem(preamble));
        if (frontmatter.isEmpty() && title.isEmpty()) {
            guard("preamble", MarkdownDocument.openingProblem(preamble));
        }
        sections = List.copyOf(sections);
    }

    /**
     * One H2 section.
     *
     * @param title the H2's text: a heading's text, by the Markdown model's rule
     * @param content the text after the H2 up to the next H2 or the end; may be empty; a block, by the Markdown
     *     model's rule
     */
    public record Section(String title, String content) {
        /**
         * Checks the parts.
         *
         * @param title the H2's text
         * @param content the section's content
         * @throws ConstraintBreachException when the title or the content breaks the Markdown model's rules
         */
        public Section {
            guard("section title", MarkdownDocument.headingProblem(Objects.requireNonNull(title, "title")));
            guard("section content", MarkdownDocument.blockProblem(Objects.requireNonNull(content, "content")));
        }
    }

    /** A deep, unmodifiable copy of a loaded mapping; a YAML null stays a value, so no {@code Map.copyOf}. */
    private static Map<String, Object> frozen(Map<?, ?> mapping) {
        Map<String, Object> copy = new LinkedHashMap<>();
        mapping.forEach((key, value) -> {
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("frontmatter keys must be strings, but one is " + key);
            }
            copy.put(name, frozenValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static @Nullable Object frozenValue(@Nullable Object value) {
        if (value instanceof Map<?, ?> nested) {
            return frozen(nested);
        }
        if (value instanceof List<?> list) {
            List<@Nullable Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(frozenValue(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        throw new IllegalArgumentException("frontmatter values must be YAML values, but one is a "
                + value.getClass().getName());
    }

    /** A part the Markdown model refuses is a breach of the document's rules, naming the part. */
    private static void guard(String part, Optional<String> problem) {
        problem.ifPresent(why -> {
            throw new ConstraintBreachException(part + " " + why);
        });
    }
}
