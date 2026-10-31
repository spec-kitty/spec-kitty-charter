package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The guide of a {@link Toolguide} (#194, research D5): the fixed {@link GuideBlock blocks} it holds, each with its
 * Markdown content, and its freeform {@link GuideSection sections} in authored order. It holds no title: the toolguide's
 * {@code title} is the one home of that, and the stored file's H1 is written from it.
 *
 * <p>Content rules: the Markdown model's own, applied by that model ({@link MarkdownDocument#blockProblem}, #271), so
 * that every block and section can stand as an H2 section of the stored file unchanged and the guide refuses, when it
 * is built, exactly the content the Markdown codec refuses. In short: no carriage return, no leading or trailing blank
 * line, no H1 or H2 outside a fenced code block, no fence left open, and nothing the form refuses as ambiguous (an
 * indented, empty or tab-separated H1 or H2, one with a closing {@code #} sequence, a setext underline). Headings of
 * level 3 and deeper stay inside content.
 *
 * @param blocks the fixed blocks the guide holds and their content; {@link GuideBlock#OVERVIEW} is present and not
 *     blank; copied, unmodifiable, iterated in block order
 * @param sections the freeform sections in authored order; copied, unmodifiable
 */
public record Guide(Map<GuideBlock, String> blocks, List<GuideSection> sections) {

    private static final ModularRuleset<String> OVERVIEW_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A guide needs an overview that is not blank.", overview -> !overview.isBlank()));

    /**
     * Checks the rules and copies the parts.
     *
     * @param blocks the fixed blocks
     * @param sections the freeform sections
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the overview is missing or blank,
     *     or a block's content breaks the content rules
     */
    public Guide {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(sections, "sections");
        EnumMap<GuideBlock, String> copy = new EnumMap<>(GuideBlock.class);
        blocks.forEach((block, content) ->
                copy.put(Objects.requireNonNull(block, "block"), Objects.requireNonNull(content, "content")));
        OVERVIEW_RULES.guard(copy.getOrDefault(GuideBlock.OVERVIEW, ""));
        copy.values().forEach(Guide::guardContent);
        blocks = Collections.unmodifiableMap(copy);
        sections = List.copyOf(sections);
    }

    /**
     * A guide that holds only its overview.
     *
     * @param overview the overview's Markdown
     * @return the guide
     */
    public static Guide of(String overview) {
        return new Guide(Map.of(GuideBlock.OVERVIEW, overview), List.of());
    }

    /**
     * The overview.
     *
     * @return its Markdown, never blank
     */
    public String overview() {
        return block(GuideBlock.OVERVIEW).orElseThrow();
    }

    /**
     * One fixed block's content.
     *
     * @param block the block
     * @return its Markdown, or empty when the guide does not hold the block
     */
    public Optional<String> block(GuideBlock block) {
        return Optional.ofNullable(blocks.get(block));
    }

    /**
     * Checks a block's or a section's content against the content rules (see the class comment).
     *
     * @param content the content
     * @throws ConstraintBreachException naming why the content cannot stand as an H2 section of the stored file
     */
    static void guardContent(String content) {
        MarkdownDocument.blockProblem(content).ifPresent(problem -> {
            throw new ConstraintBreachException("Guide content " + problem);
        });
    }
}
