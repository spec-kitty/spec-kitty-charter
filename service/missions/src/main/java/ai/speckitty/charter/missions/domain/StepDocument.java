package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.markdown.MarkdownLines;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A step's prompt or guidelines: an optional title, a preamble and titled sections in order. A prompt may also carry a
 * detailed description, longer than the step's own one-line {@linkplain MissionStep#description() description}
 * (ruling Q1 as refined on 2026-10-30); guidelines carry none. Its text follows the {@code markdown} content form's
 * line rules (the kernel's {@link MarkdownLines}), checked when it is built (#299), so a document that builds is one
 * the pack store can write. How it is written as Markdown is the pack store's; the store also checks, at write, that
 * the detailed description is a YAML string it can write.
 *
 * @param title the document's title, when it has one
 * @param description the document's detailed description, when it has one
 * @param preamble the text before the first section, possibly empty
 * @param sections the sections, in order
 */
public record StepDocument(
        Optional<String> title, Optional<String> description, String preamble, List<StepSection> sections) {

    private static final ModularRuleset<String> TITLE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A step document's title is not blank.", text -> !text.isBlank()));
    private static final ModularRuleset<String> DESCRIPTION_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A step document's detailed description is not blank.", text -> !text.isBlank()));

    /**
     * Checks that every part is present and copies the sections.
     *
     * @param title the title, possibly empty
     * @param description the detailed description, possibly empty; never blank
     * @param preamble the preamble, possibly empty text
     * @param sections the sections, in order
     * @throws ConstraintBreachException when the title or the description is blank, or the title, the preamble or the
     *     opening breaks the {@code markdown} form's line rules
     */
    public StepDocument {
        Objects.requireNonNull(title, "title").ifPresent(TITLE_RULES::guard);
        Objects.requireNonNull(description, "description").ifPresent(DESCRIPTION_RULES::guard);
        Objects.requireNonNull(preamble, "preamble");
        title.ifPresent(text -> guard("title", MarkdownLines.headingProblem(text)));
        guard("preamble", MarkdownLines.blockProblem(preamble));
        if (title.isEmpty() && description.isEmpty()) {
            // A description is written as frontmatter, so only a document with neither opens with its preamble.
            guard("preamble", MarkdownLines.openingProblem(preamble));
        }
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    }

    /**
     * A part the {@code markdown} form's line rules refuse is a breach of the step document's rules, naming the part.
     *
     * @param part the part's name, which starts the breach's description
     * @param problem the rule's answer
     */
    static void guard(String part, Optional<String> problem) {
        problem.ifPresent(why -> {
            throw new ConstraintBreachException(Reason.failed(
                    "A step document's parts follow the Markdown model's rules.",
                    "A step document's " + part + " " + why));
        });
    }
}
