package ai.speckitty.charter.packs.application;

import java.util.regex.Pattern;

/**
 * The URL of a source as it may be kept or answered (NFR-010, NFR-012): its query, its fragment and its user
 * information cut, so a presigned signature or a credential in the URL is never held in a status, a log or an audit
 * record. Only the scheme, the host, the port and the path remain.
 */
public final class SourceUrls {

    private static final Pattern USER_INFORMATION = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://)[^/]*@");

    private SourceUrls() {}

    /**
     * The URL without its query, its fragment and its user information.
     *
     * @param url a URL as the request sent it
     * @return the URL that may be recorded
     */
    public static String withoutSecrets(String url) {
        return USER_INFORMATION.matcher(cutAt(cutAt(url, '?'), '#')).replaceFirst("$1");
    }

    private static String cutAt(String value, char mark) {
        int at = value.indexOf(mark);
        return at < 0 ? value : value.substring(0, at);
    }
}
