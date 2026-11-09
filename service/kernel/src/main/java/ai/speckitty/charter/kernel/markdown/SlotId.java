package ai.speckitty.charter.kernel.markdown;

import java.util.Objects;
import java.util.Optional;

/**
 * The id of a prompt slot (ADR-0066 decision 2): {@code <namespace>:<name>}, each part {@code [a-z][a-z0-9-]*}, so the
 * whole matches {@code ^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$}. It is the id a step prompt's marker pair declares and a
 * step enhancement's fragment fills.
 *
 * <p>The namespace names a concern, not a pack: nothing is read from it. Renaming a slot is a breaking change for the
 * packs that fill it, so an id is as stable as a pack name.
 *
 * <p>Ids compare by code point. The grammar admits ASCII only, so that is the order of the wire form, character by
 * character: {@code a-b:c < a:c < ab:c}.
 *
 * @param value the wire form
 */
public record SlotId(String value) implements Comparable<SlotId> {

    private static final char SEPARATOR = ':';

    /**
     * Builds an id.
     *
     * @throws IllegalArgumentException when {@code value} does not fit the grammar; {@link #tryParse} answers instead
     *     of throwing
     */
    public SlotId {
        Objects.requireNonNull(value, "value");
        if (!fits(value)) {
            throw new IllegalArgumentException(
                    "A slot id must match ^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$, but is '" + value + "'");
        }
    }

    /**
     * Reads an id without throwing, for a caller that classifies a bad id as a fault.
     *
     * @param text the candidate
     * @return the id; empty when the text does not fit the grammar
     */
    public static Optional<SlotId> tryParse(String text) {
        Objects.requireNonNull(text, "text");
        return fits(text) ? Optional.of(new SlotId(text)) : Optional.empty();
    }

    /**
     * The part before the colon: the concern the slot belongs to.
     *
     * @return the namespace
     */
    public String namespace() {
        return value.substring(0, value.indexOf(SEPARATOR));
    }

    /**
     * The part after the colon.
     *
     * @return the name
     */
    public String name() {
        return value.substring(value.indexOf(SEPARATOR) + 1);
    }

    @Override
    public int compareTo(SlotId other) {
        return value.compareTo(other.value);
    }

    /** The wire form. */
    @Override
    public String toString() {
        return value;
    }

    private static boolean fits(String text) {
        int colon = text.indexOf(SEPARATOR);
        return colon > 0
                && text.indexOf(SEPARATOR, colon + 1) < 0
                && partFits(text, 0, colon)
                && partFits(text, colon + 1, text.length());
    }

    private static boolean partFits(String text, int from, int to) {
        if (from >= to || !isLower(text.charAt(from))) {
            return false;
        }
        for (int i = from + 1; i < to; i++) {
            char c = text.charAt(i);
            if (!isLower(c) && !(c >= '0' && c <= '9') && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLower(char c) {
        return c >= 'a' && c <= 'z';
    }
}
