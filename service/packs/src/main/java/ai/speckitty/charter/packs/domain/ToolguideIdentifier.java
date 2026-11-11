package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Toolguide} within its pack. Toolguide ids are lower-case words joined by
 * single hyphens, per the schema pattern {@code ^[a-z][a-z0-9]*(-[a-z0-9]+)*$}: the same grammar as the guide's file
 * name {@code <id>.guide.md}, so every id the service accepts names a guide file the schema also accepts.
 *
 * @param value the raw identifier text
 */
public record ToolguideIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A toolguide identifier must match ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ and be at most 64 characters.",
                    IdentifierGrammar::isLetterFirstSlug));

    /**
     * Checks that the identifier matches the schema pattern.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text does not match
     */
    public ToolguideIdentifier {
        RULES.guard(value);
    }
}
