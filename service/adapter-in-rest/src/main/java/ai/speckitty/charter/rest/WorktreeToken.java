package ai.speckitty.charter.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jspecify.annotations.Nullable;

/**
 * The per-worktree token (ADR-0011), held as its SHA-256 digest and never as text. A presented credential is hashed
 * and compared with {@link MessageDigest#isEqual}, so the time a comparison takes depends neither on the content of
 * the credential nor on its length. {@link #toString} is redacted, so a log line, an exception message or a debugger
 * that prints the holder shows no secret.
 */
public final class WorktreeToken {

    private final byte[] digest;

    private WorktreeToken(byte[] digest) {
        this.digest = digest;
    }

    /**
     * A holder of {@code token}. The token itself is not kept.
     *
     * @param token the secret, as the launcher generated it
     * @return the holder
     */
    public static WorktreeToken of(String token) {
        return new WorktreeToken(sha256(token));
    }

    /**
     * Whether {@code presented} is the token.
     *
     * @param presented the credential the client sent, or {@code null} when it sent none
     * @return {@code true} only when the credential's digest equals the token's
     */
    public boolean matches(@Nullable String presented) {
        return presented != null && MessageDigest.isEqual(digest, sha256(presented));
    }

    /**
     * The redacted form.
     *
     * @return {@code WorktreeToken[redacted]}, whatever the token is
     */
    @Override
    public String toString() {
        return "WorktreeToken[redacted]";
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform provides SHA-256.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
