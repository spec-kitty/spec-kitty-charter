package ai.speckitty.charter.kernel.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@code Pack}. It wraps the raw id so that a later change of identifier scheme does not ripple
 * into the ports.
 *
 * @param value the raw identifier text
 */
public record PackIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A pack identifier must not be blank.", text -> {
                return text != null && !text.isBlank();
            }));

    /**
     * Checks that the identifier is present and not blank.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or blank
     */
    public PackIdentifier {
        RULES.guard(value);
    }
}
