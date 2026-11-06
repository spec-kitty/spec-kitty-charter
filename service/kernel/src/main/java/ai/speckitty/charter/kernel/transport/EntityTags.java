package ai.speckitty.charter.kernel.transport;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The one interpreter of entity-tag request headers (RFC 9110 &sect;8.8.3): the strong comparison an {@code If-Match}
 * takes and the weak one an {@code If-None-Match} takes. The inbound edge passes the header through verbatim; the
 * current token is the opaque tag, without its quotes.
 *
 * <p>A value is {@code *}, or a comma-separated list of entity-tags in which empty elements and optional whitespace
 * are skipped. A member written without its quotes is compared as its bare text, a tolerance kept for clients that echo
 * the token unquoted; it is never weak. A member whose quote is never closed is malformed, and the value then matches
 * nothing.
 */
public final class EntityTags {

    private static final String WEAK_PREFIX = "W/";

    private EntityTags() {}

    /**
     * Whether an {@code If-Match} field value names the current token, by the strong comparison RFC 9110 &sect;13.1.1
     * requires: {@code *} matches (the caller has already found the resource); otherwise it matches when any
     * <em>strong</em> member's opaque tag equals the token. A weak member ({@code W/"..."}) never matches.
     *
     * @param ifMatch the {@code If-Match} field value the request carried, or null when it carried none
     * @param currentToken the current opaque tag
     * @return {@code true} when {@code ifMatch} is {@code *} or lists the token as a strong entity-tag
     */
    public static boolean matches(@Nullable String ifMatch, String currentToken) {
        return anyMember(ifMatch, currentToken, false);
    }

    /**
     * Whether an {@code If-None-Match} field value names the current token, by the weak comparison RFC 9110
     * &sect;13.1.2 requires: {@code *} matches any existing resource, and a weak member matches its opaque tag too.
     *
     * @param ifNoneMatch the {@code If-None-Match} field value the request carried, or null when it carried none
     * @param currentToken the current opaque tag
     * @return {@code true} when the client already holds the current representation
     */
    public static boolean noneMatch(@Nullable String ifNoneMatch, String currentToken) {
        return anyMember(ifNoneMatch, currentToken, true);
    }

    /**
     * Whether a field value is the wildcard {@code *}, around optional whitespace.
     *
     * @param value the field value, or null when the request carried none
     * @return {@code true} for a lone {@code *}
     */
    public static boolean isWildcard(@Nullable String value) {
        return value != null && value.strip().equals("*");
    }

    /**
     * The strong entity-tag of a token, double quotes included, as the {@code ETag} header carries it.
     *
     * @param token the opaque tag
     * @return the token in double quotes
     */
    public static String quote(String token) {
        return '"' + Objects.requireNonNull(token, "token") + '"';
    }

    private static boolean anyMember(@Nullable String value, String currentToken, boolean weakMatches) {
        Objects.requireNonNull(currentToken, "currentToken");
        if (value == null) {
            return false;
        }
        if (isWildcard(value)) {
            return true;
        }
        for (int at = skipSeparators(value, 0); at < value.length(); ) {
            boolean weak = value.startsWith(WEAK_PREFIX, at);
            int start = weak ? at + WEAK_PREFIX.length() : at;
            int end = nextMember(value, start);
            if (end < 0) {
                return false; // an unterminated entity-tag is malformed and matches nothing
            }
            if ((weakMatches || !weak) && currentToken.equals(opaqueOf(value, start, end))) {
                return true;
            }
            at = skipSeparators(value, end);
        }
        return false;
    }

    /** The index past the separators (comma, space, tab) from {@code at}. */
    private static int skipSeparators(String value, int at) {
        int next = at;
        while (next < value.length() && isSeparator(value.charAt(next))) {
            next++;
        }
        return next;
    }

    private static boolean isSeparator(char c) {
        return c == ',' || c == ' ' || c == '\t';
    }

    /**
     * The end of the member whose tag starts at {@code start}: past its closing quote when it is quoted, otherwise at
     * the next comma or the end of the value.
     *
     * @return the end index, exclusive; {@code -1} when a quote is never closed
     */
    private static int nextMember(String value, int start) {
        if (isQuoted(value, start)) {
            int close = value.indexOf('"', start + 1);
            return close < 0 ? -1 : close + 1;
        }
        int comma = value.indexOf(',', start);
        return comma < 0 ? value.length() : comma;
    }

    /** The opaque tag of the member spanning {@code [start, end)}: inside its quotes, or its stripped bare text. */
    private static String opaqueOf(String value, int start, int end) {
        return isQuoted(value, start)
                ? value.substring(start + 1, end - 1)
                : value.substring(start, end).strip();
    }

    private static boolean isQuoted(String value, int start) {
        return start < value.length() && value.charAt(start) == '"';
    }
}
