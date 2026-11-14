package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link GlossaryContext} within its pack, and its file stem: lowercase kebab-case of at most 64
 * characters, the grammar the {@code glossary} schema pins (ADR-0049 decision 2), so a context id is one path segment.
 *
 * @param value the raw identifier text
 */
public record GlossaryContextIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A glossary context identifier must be lowercase letters and digits joined by single hyphens,"
                            + " starting with a letter, at most 64 characters.",
                    IdentifierGrammar::isLetterFirstSlug));

    /**
     * Checks that the identifier matches the grammar.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or malformed
     */
    public GlossaryContextIdentifier {
        RULES.guard(value);
    }
}
