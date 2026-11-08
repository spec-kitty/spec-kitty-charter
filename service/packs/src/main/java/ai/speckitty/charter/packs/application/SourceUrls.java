package ai.speckitty.charter.packs.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The URL of a source as it may be kept or answered (NFR-010, NFR-012): its query, its fragment and its user
 * information cut, so a presigned signature or a credential in the URL is never held in a status, a log or an audit
 * record. Only the scheme, the host, the port and the path remain. The user information is cut up to the last at sign of
 * the authority, which ends at the first slash, query or fragment.
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
        String kept = cutAt(cutAt(url, '?'), '#');
        Matcher scheme = SCHEME.matcher(kept);
        if (!scheme.find()) {
            return kept;
        }
        int start = scheme.end();
        int slash = kept.indexOf('/', start);
        String authority = kept.substring(start, slash < 0 ? kept.length() : slash);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            return kept.substring(0, start) + kept.substring(start + at + 1);
        }
        if (!HOST_AND_PORT.matcher(authority).matches()) {
            // No host and port: the credential has a slash in it (https://u:p/w@host), a malformed URL that is refused
            // but still recorded. Everything up to the last at sign is user information then; a well-formed authority
            // never takes this branch, so an at sign in its path stays.
            int last = kept.lastIndexOf('@');
            if (last >= start) {
                return kept.substring(0, start) + kept.substring(last + 1);
            }
        }
        return kept;
    }

    private static String cutAt(String value, char mark) {
        int at = value.indexOf(mark);
        return at < 0 ? value : value.substring(0, at);
    }
}
