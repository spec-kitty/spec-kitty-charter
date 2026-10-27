package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;

/**
 * A declared runtime input of a command-backed {@link ContractStep}.
 *
 * @param flag the input's {@code flag}; not blank
 * @param source the input's {@code source}; not blank
 * @param optional the input's {@code optional} as written; empty when absent, which is not the same as {@code false}
 */
public record StepInput(String flag, String source, Optional<Boolean> optional) {

    private static final ModularRuleset<String> FLAG_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step contract input needs a flag that is not blank.", text -> !text.isBlank()));

    private static final ModularRuleset<String> SOURCE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step contract input needs a source that is not blank.", text -> !text.isBlank()));

    /**
     * Checks that required fields are present and not blank.
     *
     * @param flag the input's flag
     * @param source the input's source
     * @param optional the optional marker as written, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the flag or source is blank
     */
    public StepInput {
        Objects.requireNonNull(flag, "flag");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(optional, "optional");
        FLAG_RULES.guard(flag);
        SOURCE_RULES.guard(source);
    }
}
