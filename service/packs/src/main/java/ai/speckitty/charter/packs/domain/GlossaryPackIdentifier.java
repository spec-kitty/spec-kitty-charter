package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link GlossaryPack} within its pack: any text that is not blank, as the schema and core's model
 * allow (the internal pack's glossary is {@code spk-internal-glossary}).
 *
 * @param value the raw identifier text
 */
public record GlossaryPackIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A glossary pack identifier must not be blank.", text -> text != null && !text.isBlank()));

    /**
     * Checks that the identifier is not blank.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or blank
     */
    public GlossaryPackIdentifier {
        RULES.guard(value);
    }
}
