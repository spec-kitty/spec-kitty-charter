package ai.speckitty.charter.packs.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The URL of a source as it may be kept or answered (NFR-010, NFR-012): its query, its fragment and its user
 * information cut, so a presigned signature or a credential in the URL is never held in a status, a log or an audit
 * record. Only the scheme, the host, the port and the path remain. The user information is cut first, on the URL as
 * sent, up to the last at sign of the authority, which ends at the first slash, query or fragment.
 */
public final class SourceUrls {

    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://");
    private static final Pattern HOST_AND_PORT = Pattern.compile("^(\\[[0-9A-Fa-f:.]*\\]|[^\\s:@/\\[\\]]*)(:[0-9]*)?$");

    private SourceUrls() {}

    /**
     * The URL without its query, its fragment and its user information.
     *
     * @param url a URL as the request sent it
     * @return the URL that may be recorded
     */
    public static String withoutSecrets(String url) {
        return cutAt(cutAt(withoutUserInformation(url), '?'), '#');
    }

    // The user information is found on the URL as sent, before the query and the fragment are cut: a malformed
    // credential may hold a slash, a question mark or a hash (https://u:p?w@host), and cutting those first would leave
    // part of it in place.
    private static String withoutUserInformation(String url) {
        Matcher scheme = SCHEME.matcher(url);
        if (!scheme.find()) {
            return url;
        }
        int start = scheme.end();
        int end = firstOf(url, start, "/?#");
        String authority = url.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            return url.substring(0, start) + url.substring(start + at + 1);
        }
        if (!HOST_AND_PORT.matcher(authority).matches()) {
            // No host and port before the first slash, query or fragment: the credential holds one of them, a malformed
            // URL that is refused but still recorded. Everything up to the last at sign is user information then; a
            // well-formed authority never takes this branch, so an at sign in its path or query stays.
            int last = url.lastIndexOf('@');
            if (last >= start) {
                return url.substring(0, start) + url.substring(last + 1);
            }
        }
        return url;
    }

    private static int firstOf(String value, int from, String marks) {
        for (int i = from; i < value.length(); i++) {
            if (marks.indexOf(value.charAt(i)) >= 0) {
                return i;
            }
        }
        return value.length();
    }

    private static String cutAt(String value, char mark) {
        int at = value.indexOf(mark);
        return at < 0 ? value : value.substring(0, at);
    }
}
