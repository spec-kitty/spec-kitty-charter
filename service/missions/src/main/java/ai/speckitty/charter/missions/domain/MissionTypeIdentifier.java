package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link MissionType} within its pack: the schema's {@code id}, a letter-first kebab slug that is
 * also the name of the type's directory and file (ADR-0044).
 *
 * @param value the identifier text
 */
public record MissionTypeIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission type identifier is a letter-first kebab slug of at most 64 characters.",
                    IdentifierGrammar::isLetterFirstSlug));

    /**
     * Checks the identifier's grammar.
     *
     * @param value the identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is not a letter-first
     *     kebab slug
     */
    public MissionTypeIdentifier {
        RULES.guard(value);
    }
}
