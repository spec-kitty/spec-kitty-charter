package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.regex.Pattern;

/**
 * The identity of a {@link MissionType} within its pack: the schema's {@code id}, a letter-first kebab slug that is
 * also the name of the type's directory and file (ADR-0044).
 *
 * @param value the identifier text
 */
public record MissionTypeIdentifier(String value) {

    /** The id grammar of a mission type and of a step, as the {@code mission-type} schema has it. */
    static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission type identifier is a letter-first kebab slug.",
                    text -> text != null && ID.matcher(text).matches()));

    /**
     * Checks the identifier's grammar.
     *
     * @param value the identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is not a letter-first
     *     kebab slug
     */
    public MissionTypeIdentifier {
        RULES.guard(value);
    }
}
