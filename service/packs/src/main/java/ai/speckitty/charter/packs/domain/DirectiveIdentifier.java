package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.regex.Pattern;

/**
 * The identity of a {@link Directive} within its pack. Directive ids are upper-case, per the schema pattern
 * {@code ^[A-Z][A-Z0-9_-]*$}.
 *
 * @param value the raw identifier text
 */
public record DirectiveIdentifier(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_-]*$");

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A directive identifier must match ^[A-Z][A-Z0-9_-]*$.",
                    text -> text != null && PATTERN.matcher(text).matches()));

    /**
     * Checks that the identifier matches the schema pattern.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text does not match
     */
    public DirectiveIdentifier {
        RULES.guard(value);
    }
}
