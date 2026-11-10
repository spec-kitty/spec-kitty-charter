package ai.speckitty.charter.activations.application;

import java.util.Arrays;
import java.util.Objects;

/**
 * The exact bytes of a file a pack stores beside a mission type (a skeleton, a step's prompt or guidelines), as the
 * activations slice hands them on: a value that never hands its array out, so what a read serves is what was stored.
 */
public final class HeldBytes {

    private final byte[] bytes;

    private HeldBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * The bytes of a stored file, copied.
     *
     * @param bytes the file's content, of any length
     * @return the held bytes
     */
    public static HeldBytes of(byte[] bytes) {
        return new HeldBytes(Objects.requireNonNull(bytes, "bytes").clone());
    }

    /**
     * The content.
     *
     * @return a fresh copy of the bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HeldBytes held && Arrays.equals(bytes, held.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "HeldBytes[" + bytes.length + " bytes]";
    }
}
