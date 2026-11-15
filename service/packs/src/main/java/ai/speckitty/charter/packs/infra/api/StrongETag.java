package ai.speckitty.charter.packs.infra.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

/**
 * A strong entity tag that is the SHA-256 digest of the bytes a read serves, {@code "sha256:<64 lower-case hex>"}, and
 * the {@code If-None-Match} rule the service applies to it: the contract document's ({@link ContractDocument}) and the
 * kind taxonomy's.
 */
final class StrongETag {

    private static final String DIGEST_PREFIX = "sha256:";

    private final String opaqueTag;

    private StrongETag(String opaqueTag) {
        this.opaqueTag = opaqueTag;
    }

    /**
     * The tag of the given bytes.
     *
     * @param bytes the bytes served
     * @return their tag
     */
    static StrongETag of(byte[] bytes) {
        return new StrongETag(DIGEST_PREFIX + HexFormat.of().formatHex(sha256(bytes)));
    }

    /**
     * The tag, double quotes included, as the {@code ETag} header carries it.
     *
     * @return {@code "sha256:<64 lower-case hex>"}
     */
    String etag() {
        return '"' + opaqueTag + '"';
    }

    /**
     * Whether an {@code If-None-Match} value matches this tag (RFC 9110, section 13.1.2): it is {@code *}, or a
     * list of entity tags one of which has this opaque tag, compared weakly (a {@code W/} tag matches
     * too). A value that is not an entity-tag list matches nothing, whatever tags it holds. Several header lines
     * arrive joined by commas, which is the same list.
     *
     * @param ifNoneMatch the request's {@code If-None-Match}, or null when it sent none
     * @return true when the client holds the representation this tag names
     */
    boolean matchedBy(@Nullable String ifNoneMatch) {
        if (ifNoneMatch == null) {
            return false;
        }
        if (stripWhitespace(ifNoneMatch).equals("*")) {
            return true;
        }
        return new EntityTagList(ifNoneMatch).contains(opaqueTag);
    }

    /** The value without the optional whitespace (space or tab, RFC 9110 section 5.6.3) around it. */
    private static String stripWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform provides SHA-256", e);
        }
    }

    /** A parser of {@code #entity-tag} (RFC 9110, sections 5.6.1 and 8.8.3), empty list elements allowed. */
    private static final class EntityTagList {

        private final String value;
        private int at;

        EntityTagList(String value) {
            this.value = value;
        }

        /** Whether the value is a well-formed list holding the opaque tag; false for any malformed value. */
        boolean contains(String opaqueTag) {
            boolean found = false;
            while (true) {
                skipWhitespace();
                if (at == value.length()) {
                    return found;
                }
                if (value.charAt(at) == ',') {
                    at++;
                    continue;
                }
                String tag = opaqueTag();
                if (tag == null) {
                    return false;
                }
                found |= tag.equals(opaqueTag);
                skipWhitespace();
                if (at < value.length() && value.charAt(at++) != ',') {
                    return false;
                }
            }
        }

        /** The opaque tag of the entity tag at the cursor, without its quotes, or null when there is none. */
        private @Nullable String opaqueTag() {
            if (value.startsWith("W/", at)) {
                at += 2;
            }
            if (at == value.length() || value.charAt(at) != '"') {
                return null;
            }
            int start = ++at;
            while (at < value.length() && isEtagc(value.charAt(at))) {
                at++;
            }
            if (at == value.length() || value.charAt(at) != '"') {
                return null;
            }
            return value.substring(start, at++);
        }

        private void skipWhitespace() {
            while (at < value.length() && isWhitespace(value.charAt(at))) {
                at++;
            }
        }

        /** {@code etagc = %x21 / %x23-7E / obs-text}; a header value arrives as ISO-8859-1 characters. */
        private static boolean isEtagc(char c) {
            return c == 0x21 || (c >= 0x23 && c <= 0x7E) || (c >= 0x80 && c <= 0xFF);
        }
    }
}
