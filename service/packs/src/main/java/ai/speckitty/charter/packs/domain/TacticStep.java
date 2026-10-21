package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One structured step of a {@link Tactic}.
 *
 * @param title the step's {@code title}
 * @param description the step's {@code description}; empty when absent or null
 * @param examples the examples, in authored order; empty when absent
 * @param references the references, in authored order with duplicates kept; empty when absent
 */
public record TacticStep(
        String title, Optional<String> description, List<String> examples, List<TacticReference> references) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A tactic step needs a title that is not blank.", text -> !text.isBlank()));

    /**
     * Checks that required fields are present and copies the lists.
     *
     * @param title the step's title
     * @param description the description, possibly empty
     * @param examples the examples
     * @param references the references
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the title is blank
     */
    public TacticStep {
        Objects.requireNonNull(title, "title");
        RULES.guard(title);
        Objects.requireNonNull(description, "description");
        examples = List.copyOf(examples);
        references = List.copyOf(references);
    }
}
