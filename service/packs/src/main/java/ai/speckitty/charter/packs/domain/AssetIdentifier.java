package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of an {@link Asset} within its pack, the sidecar's {@code id}: lowercase kebab-case of at most 64
 * characters, one path segment, the grammar a skill identifier and the {@code asset} schema share (FR-006 of slice A2).
 *
 * @param value the raw identifier text
 */
public record AssetIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An asset identifier must be lowercase letters and digits joined by single hyphens, starting with a"
                            + " letter, at most 64 characters.",
                    IdentifierGrammar::isLetterFirstSlug));

    /**
     * Checks that the identifier matches the grammar.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or malformed
     */
    public AssetIdentifier {
        RULES.guard(value);
    }
}
