package ai.speckitty.charter.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A {@code sha256:} digest of a sequence of fields. Each field is length-prefixed, so two different sequences never
 * feed the digest the same bytes. Not thread-safe: build one fingerprint per digest.
 */
public final class Fingerprint {

    private static final String PREFIX = "sha256:";

    private final MessageDigest digest;

    private Fingerprint() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("the JDK provides SHA-256", e);
        }
    }

    /**
     * A fingerprint of no fields yet.
     *
     * @return a new fingerprint
     */
    public static Fingerprint sha256() {
        return new Fingerprint();
    }

    /**
     * Adds a text field, as UTF-8.
     *
     * @param field the field
     * @return this fingerprint
     */
    public Fingerprint add(String field) {
        return add(field.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Adds a byte field.
     *
     * @param field the field
     * @return this fingerprint
     */
    public Fingerprint add(byte[] field) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(field.length).array());
        digest.update(field);
        return this;
    }

    /**
     * The digest of the fields added so far; the fingerprint is spent afterwards.
     *
     * @return {@code sha256:} and 64 lower-case hex digits
     */
    public String value() {
        return PREFIX + HexFormat.of().formatHex(digest.digest());
    }
}
