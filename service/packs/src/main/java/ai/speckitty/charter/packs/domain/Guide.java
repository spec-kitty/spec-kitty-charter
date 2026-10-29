package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The guide of a {@link Toolguide} (#194, research D5): the fixed {@link GuideBlock blocks} it holds, each with its
 * Markdown content, and its freeform {@link GuideSection sections} in authored order. It holds no title: the toolguide's
 * {@code title} is the one home of that, and the stored file's H1 is written from it.
 *
 * <p>Content rules, the Markdown model's own (data-model "MarkdownDocument"), so that every block and section can stand
 * as an H2 section of the stored file unchanged: no carriage return, no leading or trailing blank line, no H1 or H2 at
 * column 0 outside a fenced code block, and no fence left open. Headings of level 3 and deeper stay inside content. A
 * fence opens on a line whose first non-space characters are at least three backticks (with no backtick after them)
 * or at least three tildes, at any indentation, and closes on a line of at least as many of the same character
 * followed by nothing but spaces.
 *
 * @param blocks the fixed blocks the guide holds and their content; {@link GuideBlock#OVERVIEW} is present and not
 *     blank; copied, unmodifiable, iterated in block order
 * @param sections the freeform sections in authored order; copied, unmodifiable
 */
public record Guide(Map<GuideBlock, String> blocks, List<GuideSection> sections) {

    /** A column-0 H1 or H2: one or two {@code #}, then the end of the line, a space or a tab. */
    private static final Pattern HEADING = Pattern.compile("^#{1,2}(?:[ \\t].*)?$");

    private static final int MIN_FENCE = 3;

    /** The content rules every block and section obeys. */
    static final ModularRuleset<String> CONTENT_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "Guide content has no carriage return, no leading or trailing blank line, no column-0 H1 or H2"
                            + " outside a fence, and no fence left open.",
                    Guide::standsAsSectionContent));

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
        copy.values().forEach(CONTENT_RULES::guard);
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

    /** Whether {@code content} obeys the content rules (see the class comment). */
    private static boolean standsAsSectionContent(String content) {
        if (content.isEmpty()) {
            return true;
        }
        if (content.indexOf('\r') >= 0) {
            return false;
        }
        String[] lines = content.split("\n", -1);
        if (lines[0].isBlank() || lines[lines.length - 1].isBlank()) {
            return false;
        }
        char fence = 0;
        int fenceLength = 0;
        for (String line : lines) {
            String body = line.substring(indent(line));
            if (fence != 0) {
                int run = run(body, fence);
                if (run >= fenceLength && body.substring(run).chars().allMatch(c -> c == ' ')) {
                    fence = 0;
                }
                continue;
            }
            int ticks = run(body, '`');
            int tildes = run(body, '~');
            if (ticks >= MIN_FENCE && body.indexOf('`', ticks) < 0) {
                fence = '`';
                fenceLength = ticks;
            } else if (tildes >= MIN_FENCE) {
                fence = '~';
                fenceLength = tildes;
            } else if (HEADING.matcher(line).matches()) {
                return false;
            }
        }
        return fence == 0;
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static int run(String text, char c) {
        int i = 0;
        while (i < text.length() && text.charAt(i) == c) {
            i++;
        }
        return i;
    }
}
