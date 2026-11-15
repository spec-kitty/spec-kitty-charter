package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Paradigm} within its pack, constrained by the schema's {@code ^[a-z][a-z0-9]*(-[a-z0-9]+)*$}, the shared letter-first slug (ADR-0075 decision 2).
 *
 * @param value the raw identifier text
 */
public record ParadigmIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(IdentifierGrammar.letterFirstSlug(IdentifierGrammar.MAX_SLUG_LENGTH)
                    .described("A paradigm identifier must match ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ and be at most 64"
                            + " characters."));

    /**
     * Checks that the identifier matches the schema pattern.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or malformed
     */
    public ParadigmIdentifier {
        RULES.guard(value);
    }
}
