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
     * The key two principles of one list must not share: the name, trimmed and lower-cased.
     *
     * @param name a principle name
     * @return its uniqueness key
     */
    public static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
