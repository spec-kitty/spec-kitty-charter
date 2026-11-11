package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Guard} within its pack. Guard ids are kebab-case, per the schema pattern
 * {@code ^[a-z0-9]+(-[a-z0-9]+)*$}; the id equals the file stem.
 *
 * @param value the raw identifier text
 */
public record GuardIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A guard identifier must match ^[a-z0-9]+(-[a-z0-9]+)*$ and be at most 64 characters.",
                    IdentifierGrammar::isSlug));

    /**
     * Checks that the identifier matches the schema pattern.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text does not match
     */
    public GuardIdentifier {
        RULES.guard(value);
    }
}
