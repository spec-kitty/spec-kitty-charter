package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.regex.Pattern;

/**
 * The identity of a {@link Lifecycle} within its pack: the schema's {@code id}, a kebab slug that is
 * also its file stem (ADR-0050 decision 17).
 *
 * @param value the identifier text
 */
public record LifecycleIdentifier(String value) {

    /** The id grammar of a lifecycle, as the {@code lifecycle} schema has it. */
    static final Pattern ID = Pattern.compile("[a-z0-9]++(?:-[a-z0-9]++)*+");

    /** The longest lifecycle id accepted, checked before the pattern (#440). */
    static final int MAX_LENGTH = 64;

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A lifecycle identifier is a kebab slug of at most 64 characters.",
                    text -> text != null
                            && text.length() <= MAX_LENGTH
                            && ID.matcher(text).matches()));

    /**
     * Checks the identifier's grammar.
     *
     * @param value the identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is not a letter-first
     *     kebab slug
     */
    public LifecycleIdentifier {
        RULES.guard(value);
    }
}
