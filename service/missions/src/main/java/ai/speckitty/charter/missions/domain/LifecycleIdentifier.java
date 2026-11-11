package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Lifecycle} within its pack: the schema's {@code id}, a kebab slug that is
 * also its file stem (ADR-0050 decision 17).
 *
 * @param value the identifier text
 */
public record LifecycleIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A lifecycle identifier is a kebab slug of at most 64 characters.", IdentifierGrammar::isSlug));

    /**
     * Checks the identifier's grammar.
     *
     * @param value the identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is not a letter-first
     *     kebab slug
     */
    public LifecycleIdentifier {
        RULES.guard(value);
    }
}
