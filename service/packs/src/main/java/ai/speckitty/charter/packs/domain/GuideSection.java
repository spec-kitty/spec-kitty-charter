package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;

/**
 * A freeform section of a toolguide's {@link Guide}: an H2 whose heading is not one of the {@link GuideBlock} headings
 * in any letter case, with its Markdown content. Authored order is kept by the guide.
 *
 * @param title the H2 heading text: one line, not blank, no leading or trailing whitespace, not a fixed heading
 * @param content the section's Markdown; it obeys the {@linkplain Guide guide content rules}
 */
public record GuideSection(String title, String content) {

    private static final ModularRuleset<String> TITLE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A guide section needs a title that is one line, not blank, without leading or trailing"
                            + " whitespace.",
                    title -> !title.isBlank()
                            && title.strip().equals(title)
                            && title.lines().count() == 1))
            .must(Constraints.of(
                    "A guide section's title is not a fixed block's heading in any letter case.",
                    title -> GuideBlock.reserving(title).isEmpty()));

    /**
     * Checks the fields.
     *
     * @param title the title
     * @param content the content
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the title is not a free heading
     *     or the content breaks the guide content rules
     */
    public GuideSection {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        TITLE_RULES.guard(title);
        Guide.CONTENT_RULES.guard(content);
    }
}
