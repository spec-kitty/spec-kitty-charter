package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;

/**
 * What a wrapper-form {@link Skill} expands to.
 *
 * @param target the {@code target}: a {@code builtin:} command or a {@code cli:spec-kitty} argv; not blank
 * @param args the {@code args}, when written
 */
public record SkillExpansion(String target, Optional<String> args) {

    private static final ModularRuleset<String> TARGET_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A skill expansion needs a target that is not blank.", text -> !text.isBlank()));

    /**
     * Checks the fields.
     *
     * @param target the target
     * @param args the args, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the target is blank
     */
    public SkillExpansion {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(args, "args");
        TARGET_RULES.guard(target);
    }
}
