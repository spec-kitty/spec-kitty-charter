package ai.speckitty.charter.packs.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * One named principle of a {@link Paradigm} or a {@link Styleguide}: a core idea with a short name and one or two
 * sentences (ADR-0067). A value object; the rules of a list of principles live in {@link Principles}, the one validator
 * both kinds and the pack reader use.
 *
 * @param name the principle's short name
 * @param description one or two sentences stating it
 */
public record Principle(String name, String description) {

    /** The longest name a principle may carry; the cap of a name (#446). */
    public static final int NAME_MAX = 64;

    /** The longest description a principle may carry; the cap of an entry (#446). */
    public static final int DESCRIPTION_MAX = 255;

    /**
     * Checks the parts are present.
     *
     * @param name the principle's short name
     * @param description its description
     */
    public Principle {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }

    /**
     * The key two principles of one list must not share: the name, trimmed of white space as the schema's {@code \s}
     * reads it, and lower-cased.
     *
     * @param name a principle name
     * @return its uniqueness key
     */
    public static String key(String name) {
        int start = 0;
        int end = name.length();
        while (start < end && isSpace(name.codePointAt(start))) {
            start += Character.charCount(name.codePointAt(start));
        }
        while (end > start && isSpace(name.codePointBefore(end))) {
            end -= Character.charCount(name.codePointBefore(end));
        }
        return name.substring(start, end).toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a text holds nothing but white space, as the schema's {@code pattern: \S} reads it.
     *
     * @param text the text
     * @return {@code true} when every code point is white space (an empty text included)
     */
    public static boolean isBlank(String text) {
        return text.codePoints().allMatch(Principle::isSpace);
    }

    /** The characters of ECMA-262 {@code \s}, which is what a JSON Schema {@code pattern} of {@code \S} tests. */
    private static boolean isSpace(int codePoint) {
        return switch (codePoint) {
            case '\t', '\n', 0x0B, '\f', '\r', ' ', 0xA0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF ->
                true;
            default -> codePoint >= 0x2000 && codePoint <= 0x200A;
        };
    }
}
