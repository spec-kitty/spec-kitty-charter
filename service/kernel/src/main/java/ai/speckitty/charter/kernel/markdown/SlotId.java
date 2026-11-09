package ai.speckitty.charter.kernel.markdown;

import java.util.Optional;

/** Stub, replaced by the grammar in the next commit. */
public record SlotId(String value) implements Comparable<SlotId> {

    /**
     * Stub.
     *
     * @param text the text
     * @return the id
     */
    public static Optional<SlotId> tryParse(String text) {
        return Optional.of(new SlotId(text));
    }

    /**
     * Stub.
     *
     * @return the namespace
     */
    public String namespace() {
        return value;
    }

    /**
     * Stub.
     *
     * @return the name
     */
    public String name() {
        return value;
    }

    @Override
    public int compareTo(SlotId other) {
        return 0;
    }

    @Override
    public String toString() {
        return value;
    }
}
