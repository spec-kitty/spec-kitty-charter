package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.regex.Pattern;

/**
 * The identity of a {@link MissionStepContract} within its pack, constrained to {@code ^[a-z][a-z0-9-]*$}.
 *
 * @param value the raw identifier text
 */
public record MissionStepContractIdentifier(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step contract identifier must be lowercase letters, digits and hyphens, starting with a"
                            + " letter.",
                    text -> text != null && SHAPE.matcher(text).matches()));

    /**
     * Checks that the identifier matches the pattern.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or malformed
     */
    public MissionStepContractIdentifier {
        RULES.guard(value);
    }
}
