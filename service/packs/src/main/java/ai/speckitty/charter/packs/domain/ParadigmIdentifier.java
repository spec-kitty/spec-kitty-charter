package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.regex.Pattern;

/**
 * The identity of a {@link Paradigm} within its pack, constrained by the schema's {@code ^[a-z][a-z0-9-]*$}.
 *
 * @param value the raw identifier text
 */
public record ParadigmIdentifier(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A paradigm identifier must be lowercase letters, digits and hyphens, starting with a letter.",
                    text -> text != null && SHAPE.matcher(text).matches()));

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
