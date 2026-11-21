package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The guard a transition entry names (ADR-0054 decision 2): its bare id in the same pack, or
 * {@code <pack>::guard:<id>} in another pack (ADR-0053 decision 7).
 *
 * @param pack the other pack's name, a pack id; empty for the entry's own pack
 * @param id the guard's id
 */
public record GuardReference(Optional<String> pack, String id) {

    /** The longest guard id a reference accepts (#440). */
    private static final int MAX_ID_LENGTH = IdentifierGrammar.MAX_SLUG_LENGTH;

    private static final String SEPARATOR = "::guard:";

    /**
     * The longest spelling {@link #parse(String)} matches: a pack id ({@value PackIdentifier#MAX_LENGTH}), the separator
     * and a guard id, 135 characters, the {@code maxLength} of the lifecycle schema's guard {@code ref}.
     */
    static final int MAX_SPELLING_LENGTH = PackIdentifier.MAX_LENGTH + SEPARATOR.length() + MAX_ID_LENGTH;

    private static final Pattern SPELLING =
            Pattern.compile("(?:(" + IdentifierGrammar.SLUG + ")" + SEPARATOR + ")?(" + IdentifierGrammar.SLUG + ")");

    /**
     * Checks the parts.
     *
     * @param pack the other pack's name, possibly empty; a pack id (#582)
     * @param id the guard id
     * @throws IllegalArgumentException when the id is over 64 characters, or the pack name is no pack id
     */
    public GuardReference {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(id, "id");
        if (id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "a guard reference's guard id is at most " + MAX_ID_LENGTH + " characters");
        }
        pack.filter(name -> !PackQualifiedReference.isPackName(name)).ifPresent(name -> {
            throw new IllegalArgumentException(PackQualifiedReference.refusal(name));
        });
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
        // A qualifier that is no pack id is refused for that reason, in the kernel's words (#582), not as a bad
        // spelling: the shape below only reads a slug.
        int qualifier = text.indexOf("::");
        if (qualifier >= 0 && !PackQualifiedReference.isPackName(text.substring(0, qualifier))) {
            throw new IllegalArgumentException(PackQualifiedReference.refusal(text.substring(0, qualifier)));
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
