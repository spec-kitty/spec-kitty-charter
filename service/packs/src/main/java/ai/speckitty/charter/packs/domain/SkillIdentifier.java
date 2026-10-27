package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.regex.Pattern;

/**
 * The identity of a {@link Skill} within its pack: lowercase kebab-case of at most 64 characters, the grammar core's
 * pack-skill model and the {@code skill} schema share.
 *
 * @param value the raw identifier text
 */
public record SkillIdentifier(String value) {

    private static final Pattern SHAPE = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final int MAX_LENGTH = 64;

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A skill identifier must be lowercase letters and digits joined by single hyphens, starting with a"
                            + " letter, at most 64 characters.",
                    text -> text != null
                            && text.length() <= MAX_LENGTH
                            && SHAPE.matcher(text).matches()));

    /**
     * Checks that the identifier matches the grammar.
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or malformed
     */
    public SkillIdentifier {
        RULES.guard(value);
    }
}
