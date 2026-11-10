package ai.speckitty.charter.packs.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * One principle of a {@link Paradigm}: a core idea with a short name and one or two sentences (ADR-0067). A value
 * object; its rules (not blank, the length caps) are judged by the owning paradigm, so that a breach names the
 * paradigm.
 *
 * @param name the principle's short name
 * @param description one or two sentences stating it
 */
public record ParadigmPrinciple(String name, String description) {

    /** The longest name a principle may carry; the cap of an identifier (#446). */
    public static final int NAME_MAX = 64;

    /** The longest description a principle may carry; the cap of an entry (#446). */
    public static final int DESCRIPTION_MAX = 255;

    /**
     * Checks the parts are present.
     *
     * @param name the principle's short name
     * @param description its description
     */
    public ParadigmPrinciple {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }

    /**
     * The key two principles of one paradigm must not share: the name, trimmed and lower-cased.
     *
     * @return the uniqueness key
     */
    public String key() {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    boolean wellFormed() {
        return !name.isBlank()
                && name.length() <= NAME_MAX
                && !description.isBlank()
                && description.length() <= DESCRIPTION_MAX;
    }
}
