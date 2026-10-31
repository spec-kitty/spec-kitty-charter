package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.Objects;

/**
 * A freeform section of a toolguide's {@link Guide}: an H2 whose heading is not one of the {@link GuideBlock} headings
 * in any letter case, with its Markdown content. Authored order is kept by the guide.
 *
 * @param title the H2 heading text: a heading's text by the Markdown model's rule ({@link MarkdownDocument#headingProblem},
 *     #271), not a fixed heading
 * @param content the section's Markdown; it obeys the {@linkplain Guide guide content rules}
 */
public record GuideSection(String title, String content) {

    private static final ModularRuleset<String> TITLE_RULES = ModularRuleset.<String>emptyRules()
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
        MarkdownDocument.headingProblem(title).ifPresent(problem -> {
            throw new ConstraintBreachException("A guide section's title " + problem);
        });
        TITLE_RULES.guard(title);
        Guide.guardContent(content);
    }
}
