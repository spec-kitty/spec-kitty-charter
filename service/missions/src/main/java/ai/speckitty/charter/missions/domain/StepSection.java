package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.markdown.MarkdownLines;
import java.util.Objects;

/**
 * A titled section of a {@link StepDocument}, in the order the document holds it. Its title and content follow the
 * {@code markdown} form's line rules, checked when it is built.
 *
 * @param title the section's heading text
 * @param content the section's body
 */
public record StepSection(String title, String content) {

    /**
     * Checks both parts against the {@code markdown} form's line rules (#299).
     *
     * @param title the heading text
     * @param content the body, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the title is not a heading's text,
     *     or the content cannot stand as a block
     */
    public StepSection {
        StepDocument.guard("section title", MarkdownLines.headingProblem(Objects.requireNonNull(title, "title")));
        StepDocument.guard("section content", MarkdownLines.blockProblem(Objects.requireNonNull(content, "content")));
    }
}
