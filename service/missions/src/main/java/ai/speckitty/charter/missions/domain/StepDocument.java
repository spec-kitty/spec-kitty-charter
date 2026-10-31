package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A step's prompt or guidelines: an optional title, a preamble and titled sections in order. A prompt may also carry a
 * detailed description, longer than the step's own one-line {@linkplain MissionStep#description() description}
 * (ruling Q1 as refined on 2026-10-30); guidelines carry none. How a document is written as Markdown, and the rules
 * its text follows there, are the pack store's (the {@code markdown} content form).
 *
 * @param title the document's title, when it has one
 * @param description the document's detailed description, when it has one
 * @param preamble the text before the first section, possibly empty
 * @param sections the sections, in order
 */
public record StepDocument(
        Optional<String> title, Optional<String> description, String preamble, List<StepSection> sections) {

    private static final ModularRuleset<String> DESCRIPTION_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A step document's detailed description is not blank.", text -> !text.isBlank()));

    /**
     * Checks that every part is present and copies the sections.
     *
     * @param title the title, possibly empty
     * @param description the detailed description, possibly empty; never blank
     * @param preamble the preamble, possibly empty text
     * @param sections the sections, in order
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the description is blank
     */
    public StepDocument {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description").ifPresent(DESCRIPTION_RULES::guard);
        Objects.requireNonNull(preamble, "preamble");
        sections = List.copyOf(sections);
    }
}
