package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Tactic} within its pack: a letter-first kebab slug of at most 64 characters, {@code
 * ^[a-z][a-z0-9]*(-[a-z0-9]+)*$}, the shared grammar (ADR-0075 decision 2).
 *
 * @param value the raw identifier text
 */
public record TacticIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(IdentifierGrammar.letterFirstSlug(IdentifierGrammar.MAX_SLUG_LENGTH)
                    .described("A tactic identifier must match ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ and be at most 64"
                            + " characters."));

    /**
     * Checks that the identifier is a letter-first kebab slug of at most 64 characters.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or malformed
     */
    public TacticIdentifier {
        RULES.guard(value);
    }
}
