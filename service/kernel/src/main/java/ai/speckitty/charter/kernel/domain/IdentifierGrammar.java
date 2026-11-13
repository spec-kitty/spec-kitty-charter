package ai.speckitty.charter.kernel.domain;

import ai.speckitty.charter.kernel.validation.Constraint;
import ai.speckitty.charter.kernel.validation.Constraints;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The identifier grammars that packs and missions share (#444): the kebab slug, digit-first or letter-first, the
 * dotted key, and the relative path. Each predicate checks the length cap before it matches, and every repeat in the
 * grammars is possessive, so an over-long text is refused cheaply and cannot overflow the stack (#440). An identifier
 * type checks its text through one of these, never through a copy of the grammar.
 *
 * <p>The pack name of a {@link PackQualifiedReference} follows a grammar of its own, {@link
 * PackQualifiedReference#PACK_NAME_GRAMMAR}, which allows underscores.
 */
public final class IdentifierGrammar {

    /** The longest kebab slug accepted, as the pack schemas cap an id. */
    public static final int MAX_SLUG_LENGTH = 64;

    /** The longest dotted key or relative path accepted. */
    public static final int MAX_PATH_LENGTH = 255;

    /**
     * Lowercase letters and digits, joined by single hyphens: {@code ^[a-z0-9]+(-[a-z0-9]+)*$}. It may start with a
     * digit.
     */
    public static final String SLUG = "[a-z0-9]++(?:-[a-z0-9]++)*+";

    /** A {@link #SLUG} that starts with a letter: {@code ^[a-z][a-z0-9]*(-[a-z0-9]+)*$}. */
    public static final String LETTER_FIRST_SLUG = "[a-z][a-z0-9]*+(?:-[a-z0-9]++)*+";

    /**
     * A letter-first segment that may hold hyphens, then one or more segments after a dot, each of lowercase letters,
     * digits, hyphens and underscores: {@code ^[a-z][a-z0-9-]*(\.[a-z0-9_-]+)+$}.
     */
    public static final String DOTTED_KEY = "[a-z][a-z0-9-]*+(?:\\.[a-z0-9_-]++)++";

    /**
     * A relative path of plain segments: no leading slash, no {@code ..} or hidden segment, no space or backslash.
     * Each segment starts with a letter, a digit or an underscore.
     */
    public static final String RELATIVE_PATH = "[A-Za-z0-9_][A-Za-z0-9_.-]*+(?:/[A-Za-z0-9_][A-Za-z0-9_.-]*+)*+";

    private static final Pattern SLUG_PATTERN = Pattern.compile(SLUG);
    private static final Pattern LETTER_FIRST_SLUG_PATTERN = Pattern.compile(LETTER_FIRST_SLUG);
    private static final Pattern DOTTED_KEY_PATTERN = Pattern.compile(DOTTED_KEY);
    private static final Pattern RELATIVE_PATH_PATTERN = Pattern.compile(RELATIVE_PATH);

    private IdentifierGrammar() {}

    /**
     * Whether a text is a {@link #SLUG} of at most {@value #MAX_SLUG_LENGTH} characters.
     *
     * @param text the candidate, possibly {@code null}
     * @return {@code true} when it is
     */
    public static boolean isSlug(@Nullable String text) {
        return text != null
                && text.length() <= MAX_SLUG_LENGTH
                && SLUG_PATTERN.matcher(text).matches();
    }

    /**
     * Whether a text is a {@link #LETTER_FIRST_SLUG} of at most {@value #MAX_SLUG_LENGTH} characters.
     *
     * @param text the candidate, possibly {@code null}
     * @return {@code true} when it is
     */
    public static boolean isLetterFirstSlug(@Nullable String text) {
        return text != null
                && text.length() <= MAX_SLUG_LENGTH
                && LETTER_FIRST_SLUG_PATTERN.matcher(text).matches();
    }

    /**
     * Whether a text is a {@link #DOTTED_KEY} of at most {@value #MAX_PATH_LENGTH} characters.
     *
     * @param text the candidate, possibly {@code null}
     * @return {@code true} when it is
     */
    public static boolean isDottedKey(@Nullable String text) {
        return text != null
                && text.length() <= MAX_PATH_LENGTH
                && DOTTED_KEY_PATTERN.matcher(text).matches();
    }

    /**
     * Whether a text is a {@link #RELATIVE_PATH} of at most {@value #MAX_PATH_LENGTH} characters.
     *
     * @param text the candidate, possibly {@code null}
     * @return {@code true} when it is
     */
    public static boolean isRelativePath(@Nullable String text) {
        return text != null
                && text.length() <= MAX_PATH_LENGTH
                && RELATIVE_PATH_PATTERN.matcher(text).matches();
    }

    /**
     * The rule that a text is a {@link #SLUG} of at most {@code max} characters (ADR-0075 decision 1).
     *
     * @param max the cap, from 1 to {@value #MAX_SLUG_LENGTH}
     * @return the rule; its detail names the grammar and the cap
     * @throws IllegalArgumentException when the cap is out of range
     */
    public static Constraint<String> slug(int max) {
        int cap = cap(max);
        return Constraints.of(
                "must be a kebab slug of at most " + cap + " characters", text -> isSlug(text) && text.length() <= cap);
    }

    /**
     * The rule that a text is a {@link #LETTER_FIRST_SLUG} of at most {@code max} characters (ADR-0075 decision 1).
     *
     * @param max the cap, from 1 to {@value #MAX_SLUG_LENGTH}
     * @return the rule; its detail names the grammar and the cap
     * @throws IllegalArgumentException when the cap is out of range
     */
    public static Constraint<String> letterFirstSlug(int max) {
        int cap = cap(max);
        return Constraints.of(
                "must be a letter-first kebab slug of at most " + cap + " characters",
                text -> isLetterFirstSlug(text) && text.length() <= cap);
    }

    private static int cap(int max) {
        if (max < 1 || max > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "A slug's cap is from 1 to " + MAX_SLUG_LENGTH + " characters, not " + max + ".");
        }
        return max;
    }
}
