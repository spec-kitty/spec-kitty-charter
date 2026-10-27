package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;

/**
 * One declared parameter of a {@link Skill}, kept as written: an absent {@code required} is not {@code false}.
 *
 * @param name the parameter's {@code name}; not blank
 * @param required the {@code required} flag, when written
 * @param description the {@code description}, when written
 * @param defaultValue the {@code default}, when written
 */
public record SkillParameter(
        String name, Optional<Boolean> required, Optional<String> description, Optional<String> defaultValue) {

    private static final ModularRuleset<String> NAME_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A skill parameter needs a name that is not blank.", text -> !text.isBlank()));

    /**
     * Checks the fields.
     *
     * @param name the name
     * @param required the required flag, possibly empty
     * @param description the description, possibly empty
     * @param defaultValue the default, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the name is blank
     */
    public SkillParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(defaultValue, "defaultValue");
        NAME_RULES.guard(name);
    }
}
