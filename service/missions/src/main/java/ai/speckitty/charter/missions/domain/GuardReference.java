package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The guard a transition entry names (ADR-0054 decision 2): its bare id in the same pack, or
 * {@code <pack>::guard:<id>} in another pack (ADR-0053 decision 7).
 *
 * @param pack the other pack's name; empty for the entry's own pack
 * @param id the guard's id
 */
public record GuardReference(Optional<String> pack, String id) {

    /** The longest pack name or guard id a reference accepts (#440). */
    private static final int MAX_PART_LENGTH = IdentifierGrammar.MAX_SLUG_LENGTH;

    private static final String SEPARATOR = "::guard:";

    /** The longest spelling {@link #parse(String)} matches: two capped parts and the separator. */
    private static final int MAX_SPELLING_LENGTH = 2 * MAX_PART_LENGTH + SEPARATOR.length();

    private static final Pattern SPELLING =
            Pattern.compile("(?:(" + IdentifierGrammar.SLUG + ")" + SEPARATOR + ")?(" + IdentifierGrammar.SLUG + ")");

    /**
     * Checks the parts.
     *
     * @param pack the other pack's name, possibly empty
     * @param id the guard id
     */
    public GuardReference {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(id, "id");
        if (id.length() > MAX_PART_LENGTH || pack.map(String::length).orElse(0) > MAX_PART_LENGTH) {
            throw new IllegalArgumentException(
                    "a guard reference's pack name and guard id are at most " + MAX_PART_LENGTH + " characters");
        }
    }

    /**
     * Reads the authored spelling.
     *
     * @param text a bare guard id or {@code <pack>::guard:<id>}
     * @return the reference
     * @throws IllegalArgumentException when the text is neither spelling
     */
    public static GuardReference parse(String text) {
        if (text.length() > MAX_SPELLING_LENGTH) {
            throw new IllegalArgumentException(
                    "a guard reference is not longer than " + MAX_SPELLING_LENGTH + " characters");
        }
        Matcher m = SPELLING.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("guard ref '" + text + "' is neither <id> nor <pack>::guard:<id>");
        }
        return new GuardReference(Optional.ofNullable(m.group(1)), m.group(2));
    }

    /**
     * Whether the guard is in another pack.
     *
     * @return {@code true} for {@code <pack>::guard:<id>}
     */
    public boolean crossPack() {
        return pack.isPresent();
    }

    /**
     * The authored spelling, the inverse of {@link #parse(String)}.
     *
     * @return the bare id, or {@code <pack>::guard:<id>}
     */
    public String spelling() {
        return pack.map(p -> p + SEPARATOR + id).orElse(id);
    }
}
