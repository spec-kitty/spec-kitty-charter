package ai.speckitty.charter.packs.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The exact bytes of a payload file: an asset file or a template. A value object: two payloads are equal when they
 * hold the same bytes. No charset is assumed and the bytes never leave it except as a copy, so neither the input array
 * nor a {@link #bytes()} result can change it.
 */
public final class Payload {

    private final byte[] bytes;

    private Payload(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * A payload holding a copy of {@code bytes}.
     *
     * @param bytes the content, of any length including zero
     * @return the payload
     */
    public static Payload of(byte[] bytes) {
        return new Payload(Objects.requireNonNull(bytes, "bytes").clone());
    }

    /**
     * The content.
     *
     * @return a fresh copy of the bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * The content's length.
     *
     * @return the number of bytes
     */
    public int size() {
        return bytes.length;
    }

    /**
     * The SHA-256 digest of the content, for diagnostics.
     *
     * @return the digest as lower-case hex
     */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256.", e);
        }
    }

    /** Value equality: equal when the bytes are equal. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Payload that && Arrays.equals(bytes, that.bytes));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    /** Names the size and digest, never the bytes. */
    @Override
    public String toString() {
        return "Payload[" + bytes.length + " bytes, sha256=" + sha256() + "]";
    }
}
