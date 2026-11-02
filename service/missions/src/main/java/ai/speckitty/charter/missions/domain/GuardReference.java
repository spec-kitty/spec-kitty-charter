package ai.speckitty.charter.missions.domain;

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

    private static final String KEBAB = "[a-z0-9]+(?:-[a-z0-9]+)*";
    private static final Pattern SPELLING = Pattern.compile("(?:(" + KEBAB + ")::guard:)?(" + KEBAB + ")");

    /**
     * Checks the parts.
     *
     * @param pack the other pack's name, possibly empty
     * @param id the guard id
     */
    public GuardReference {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(id, "id");
    }

    /**
     * Reads the authored spelling.
     *
     * @param text a bare guard id or {@code <pack>::guard:<id>}
     * @return the reference
     * @throws IllegalArgumentException when the text is neither spelling
     */
    public static GuardReference parse(String text) {
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
        return pack.map(p -> p + "::guard:" + id).orElse(id);
    }
}
