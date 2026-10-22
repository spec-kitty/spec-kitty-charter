package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.regex.Pattern;

/**
 * The identity of a {@link Styleguide} within its pack.
 *
 * @param value the raw identifier text, matching {@code ^[a-z][a-z0-9-]*$}
 */
public record StyleguideIdentifier(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A styleguide identifier must match ^[a-z][a-z0-9-]*$.",
                    text -> text != null && SHAPE.matcher(text).matches()));

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
