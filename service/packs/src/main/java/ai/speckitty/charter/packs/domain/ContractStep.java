package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One step of a {@link MissionStepContract}.
 *
 * @param id the step's {@code id}; not blank
 * @param description the step's {@code description}; required, and may be empty as the schema allows
 * @param command the step's {@code command}; empty when absent or null
 * @param inputs the step's {@code inputs}, in authored order with duplicates kept; empty when absent
 * @param delegatesTo the step's {@code delegates_to}; empty when absent or null
 * @param guidance the step's {@code guidance}; empty when absent or null
 */
public record ContractStep(
        String id,
        String description,
        Optional<String> command,
        List<StepInput> inputs,
        Optional<StepDelegation> delegatesTo,
        Optional<String> guidance) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step contract step needs an id that is not blank.", text -> !text.isBlank()));

    /**
     * Checks that required fields are present and copies the inputs.
     *
     * @param id the step's id
     * @param description the step's description
     * @param command the command, possibly empty
     * @param inputs the inputs
     * @param delegatesTo the delegation, possibly empty
     * @param guidance the guidance, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the id is blank
     */
    public ContractStep {
        Objects.requireNonNull(id, "id");
        RULES.guard(id);
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(command, "command");
        inputs = List.copyOf(inputs);
        Objects.requireNonNull(delegatesTo, "delegatesTo");
        Objects.requireNonNull(guidance, "guidance");
    }
}
