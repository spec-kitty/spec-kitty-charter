package ai.speckitty.charter.kernel.domain;

import ai.speckitty.charter.kernel.validation.Constraint;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import org.jspecify.annotations.Nullable;

/**
 * The identity of a {@code Pack}: the name it is registered and stored under, also the name of its directory under the
 * pack domain. It is a {@link IdentifierGrammar#slug(int) kebab slug} of at most {@value #MAX_LENGTH} characters
 * (ADR-0075 decision 2): lower-case letters and digits in hyphen-separated runs, a digit first allowed, with no
 * leading, trailing or doubled hyphen.
 *
 * @param value the raw identifier text
 */
public record PackIdentifier(String value) {

    /** The longest pack id accepted. */
    public static final int MAX_LENGTH = 63;

    /** What a pack id must be, as a refusal says it. */
    public static final String GRAMMAR_DETAIL = "A pack id must be a kebab slug of at most " + MAX_LENGTH
            + " characters: lower-case letters and digits in hyphen-separated runs, with no leading, trailing or"
            + " doubled hyphen.";

    private static final Constraint<String> RULE =
            IdentifierGrammar.slug(MAX_LENGTH).described(GRAMMAR_DETAIL);

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.notBlank().described("A pack identifier must not be blank."));

    /**
     * Checks that the identifier is present and not blank. A registered pack id must also be {@link #isPackId}; the
     * constructor does not demand it, because the descriptor's {@code pack_id} also travels as a pack identifier
     * (#552 Q3, pending).
     *
     * @param value the raw identifier text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or blank
     */
    public PackIdentifier {
        RULES.guard(value);
    }

    /**
     * Whether a text is a pack id, for a caller that refuses a bad one in its own terms.
     *
     * @param text the candidate, possibly {@code null}
     * @return {@code true} when the text is a kebab slug of at most {@value #MAX_LENGTH} characters
     */
    public static boolean isPackId(@Nullable String text) {
        return text != null && RULE.predicate().test(text);
    }
}
