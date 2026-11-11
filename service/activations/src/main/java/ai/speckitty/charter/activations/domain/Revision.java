package ai.speckitty.charter.activations.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An activation's revision: {@code sha256:} and the lowercase hex SHA-256 of the RFC 8785 serialization of its
 * canonical document (FR-005). It is the activation's entity tag. Equal by value.
 *
 * @param token the revision text, for example {@code sha256:0e95…6d72}
 */
public record Revision(String token) {

    private static final Pattern TOKEN_SHAPE = Pattern.compile("sha256:[0-9a-f]{64}");

    /**
     * Checks the token.
     *
     * @param token the revision text
     * @throws IllegalArgumentException when the token is not {@code sha256:} and 64 lowercase hex characters
     */
    public Revision {
        Objects.requireNonNull(token, "token");
        if (!TOKEN_SHAPE.matcher(token).matches()) {
            throw new IllegalArgumentException("A revision is sha256: and 64 lowercase hex characters.");
        }
    }

    /**
     * The revision of canonical bytes.
     *
     * @param canonicalUtf8 the RFC 8785 serialization of the canonical document
     * @return its revision
     */
    public static Revision of(byte[] canonicalUtf8) {
        return new Revision("sha256:" + HexFormat.of().formatHex(sha256().digest(canonicalUtf8)));
    }

    /**
     * Reads a revision, for example from an {@code If-Match} header.
     *
     * @param text the text
     * @return the revision; empty when the text is not one
     */
    public static Optional<Revision> parse(String text) {
        return TOKEN_SHAPE.matcher(text).matches() ? Optional.of(new Revision(text)) : Optional.empty();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256.", e);
        }
    }

    @Override
    public String toString() {
        return token;
    }
}
