package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Tactic} within its pack. It wraps the raw id so that a later change of identifier scheme
 * does not ripple into the ports.
 *
 * @param value the raw identifier text
 */
public record TacticIdentifier(String value) {

    /** The longest identifier accepted, as the {@code tactic} schema caps it (#440). */
    private static final int MAX_LENGTH = 64;

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A tactic identifier must not be blank.", text -> {
                return text != null && !text.isBlank();
            }))
            .must(Constraints.of(
                    "A tactic identifier must be at most 64 characters.", text -> text.length() <= MAX_LENGTH));

    /**
     * Checks that the identifier is present, not blank and at most 64 characters.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null, blank or longer than 64 characters
     */
    public TacticIdentifier {
        RULES.guard(value);
    }
}
