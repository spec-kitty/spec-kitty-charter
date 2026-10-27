package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of an {@link Asset} within its pack: the sidecar's {@code id}.
 *
 * @param value the raw identifier text
 */
public record AssetIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("An asset identifier must not be blank.", text -> {
                return text != null && !text.isBlank();
            }));

    /**
     * Checks that the identifier is present and not blank.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or blank
     */
    public AssetIdentifier {
        RULES.guard(value);
    }
}
