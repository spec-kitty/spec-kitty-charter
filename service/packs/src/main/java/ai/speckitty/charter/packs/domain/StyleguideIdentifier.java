package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Styleguide} within its pack.
 *
 * @param value the raw identifier text, matching {@code ^[a-z][a-z0-9]*(-[a-z0-9]+)*$}, the shared letter-first slug (ADR-0075 decision 2)
 */
public record StyleguideIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(IdentifierGrammar.letterFirstSlug(IdentifierGrammar.MAX_SLUG_LENGTH)
                    .described("A styleguide identifier must match ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ and be at most 64"
                            + " characters."));

    /**
     * Checks that the identifier has the schema's shape.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text does not match
     */
    public StyleguideIdentifier {
        RULES.guard(value);
    }
}
