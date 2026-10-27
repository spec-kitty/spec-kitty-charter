package ai.speckitty.charter.profiles.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of an {@link AgentProfile} within its pack: the schema's {@code profile-id}. The schema sets no pattern,
 * so the only constraint is that it names something.
 *
 * @param value the raw identifier text
 */
public record AgentProfileIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An agent profile identifier must not be blank.", text -> text != null && !text.isBlank()));

    /**
     * Checks that the identifier is not blank.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or blank
     */
    public AgentProfileIdentifier {
        RULES.guard(value);
    }
}
