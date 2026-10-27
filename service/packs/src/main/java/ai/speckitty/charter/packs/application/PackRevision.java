package ai.speckitty.charter.packs.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The write-side revision of a pack or artifact's committed state (ADR-0026 Decision 5): a stable digest used as the
 * {@code ETag} a registration answers with and the {@code If-Match} it is matched against.
 *
 * <p>It is deliberately <strong>distinct</strong> from the read-path source fingerprint
 * ({@link ConsistentRead}/{@code PackSources.fingerprint()}, the {@code Charter-Source-Fingerprint}). That fingerprint
 * is a freshness check over the read <em>inputs</em>; this revision is a concurrency token over the committed
 * <em>content</em>. To guarantee the two never coincide for the same bytes, the revision digest is
 * <em>domain-separated</em>: a fixed tag and a length prefix per item precede the committed content, which the read
 * fingerprint never prepends. Reusing the fingerprint as the ETag would conflate freshness with concurrency, so they
 * are computed apart.
 */
public final class PackRevision {

    /** The domain-separation tag: it never appears in the read-path fingerprint's input, so the digests diverge. */
    private static final byte[] DOMAIN_TAG = "charter-pack-revision\u0000v1\n".getBytes(StandardCharsets.UTF_8);

    private final String token;

    private PackRevision(String token) {
        this.token = Objects.requireNonNull(token, "token");
    }

    /**
     * Computes the revision of a committed state from its serialized child content.
     *
     * <p>The caller passes the committed file texts (the serialized DSOs and the registered-relationship shard) in a
     * stable order; a change to any of them changes the revision, and an unchanged commit yields an equal revision. It
     * is {@link #computeOverBytes} over each text's UTF-8 encoding, so a text and its UTF-8 bytes share one revision.
     *
     * @param committedContent the serialized contents of the committed state, in a stable order
     * @return the revision, {@code sha256:} and 64 lower-case hex digits
     */
    public static PackRevision compute(List<String> committedContent) {
        Objects.requireNonNull(committedContent, "committedContent");
        return computeOverBytes(committedContent.stream()
                .map(content -> Objects.requireNonNull(content, "content").getBytes(StandardCharsets.UTF_8))
                .toList());
    }

    /**
     * Computes the revision of a committed state from the raw bytes of its committed files, which need not be UTF-8
     * text (a binary payload is digested as it is stored).
     *
     * <p>The digest is the domain tag, then per item its decimal byte length, a {@code '\n'} and its bytes, in the
     * order given; a change to any byte changes the revision.
     *
     * @param committedContent the bytes of each committed file, in a stable order
     * @return the revision, {@code sha256:} and 64 lower-case hex digits
     */
    public static PackRevision computeOverBytes(List<byte[]> committedContent) {
        Objects.requireNonNull(committedContent, "committedContent");
        MessageDigest digest = sha256();
        digest.update(DOMAIN_TAG);
        for (byte[] content : committedContent) {
            byte[] bytes = Objects.requireNonNull(content, "content");
            // Length-prefix each item so that re-partitioning the same bytes cannot collide.
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(bytes);
        }
        return new PackRevision("sha256:" + hex(digest.digest()));
    }

    /**
     * The revision token, for the {@code ETag} header.
     *
     * @return {@code sha256:} and 64 lower-case hex digits
     */
    public String token() {
        return token;
    }

    /**
     * Whether an {@code If-Match} field value names the current revision, by the strong comparison RFC 9110 &sect;13.1.1
     * requires: {@code *} matches (the caller has already found the pack); otherwise the value is a comma-separated
     * list of entity-tags and it matches when any <em>strong</em> member's opaque tag equals the current token. A weak
     * member ({@code W/"..."}) never matches. A member written without its quotes is compared as its bare text, a
     * tolerance kept for clients that echo the token unquoted; it is never weak.
     *
     * <p>This is the one place an {@code If-Match} value is interpreted: the inbound edge passes the header through
     * verbatim.
     *
     * @param ifMatch the {@code If-Match} field value the request carried, or null when it carried none
     * @param current the revision of the committed state
     * @return {@code true} when {@code ifMatch} is {@code *} or lists the current revision as a strong entity-tag
     */
    public static boolean matches(@Nullable String ifMatch, PackRevision current) {
        Objects.requireNonNull(current, "current");
        if (ifMatch == null) {
            return false;
        }
        if (ifMatch.strip().equals("*")) {
            return true;
        }
        int at = 0;
        int length = ifMatch.length();
        while (at < length) {
            char c = ifMatch.charAt(at);
            if (c == ',' || c == ' ' || c == '\t') {
                at++;
                continue;
            }
            boolean weak = ifMatch.startsWith("W/", at);
            int start = weak ? at + 2 : at;
            int end;
            String opaque;
            if (start < length && ifMatch.charAt(start) == '"') {
                end = ifMatch.indexOf('"', start + 1);
                if (end < 0) {
                    return false; // an unterminated entity-tag is malformed and matches nothing
                }
                opaque = ifMatch.substring(start + 1, end);
                end++;
            } else {
                end = ifMatch.indexOf(',', start);
                end = end < 0 ? length : end;
                opaque = ifMatch.substring(start, end).strip();
            }
            if (!weak && current.token.equals(opaque)) {
                return true;
            }
            at = end;
        }
        return false;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof PackRevision that && token.equals(that.token));
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }

    @Override
    public String toString() {
        return token;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required algorithm of every JRE; its absence is a broken platform, not a runtime case.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
