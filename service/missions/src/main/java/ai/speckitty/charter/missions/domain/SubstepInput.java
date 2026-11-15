package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;

/**
 * A declared runtime input of a {@link Substep}'s command (operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9): the flag the
 * command takes and the source its value comes from.
 *
 * @param flag the command's flag, for example {@code --action}; not blank
 * @param source where the value comes from, for example {@code mission_slug}; not blank
 * @param optional whether the command runs without it; {@code optional: true} is written only when it does
 */
public record SubstepInput(String flag, String source, boolean optional) {

    private static final ModularRuleset<String> TEXT_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.notBlank().described("A substep input's flag and source are not blank."));

    /**
     * Checks the texts.
     *
     * @param flag the flag
     * @param source the source
     * @param optional whether it is optional
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the flag or the source is blank
     */
    public SubstepInput {
        TEXT_RULES.guard(Objects.requireNonNull(flag, "flag"));
        TEXT_RULES.guard(Objects.requireNonNull(source, "source"));
    }
}
