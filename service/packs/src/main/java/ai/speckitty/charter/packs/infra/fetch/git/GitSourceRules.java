package ai.speckitty.charter.packs.infra.fetch.git;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.SourceRefusal;
import ai.speckitty.charter.packs.infra.fetch.GuardedHttpsExchange;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What the request may name before any process starts (T090, plan.md "argument injection through the ref or URL",
 * research R9): the URL and the ref are validated here, and a value that is not of the grammar is a
 * {@code pack_source_refused} with a fixed reason, which names nothing the client sent, and never reaches git. Both are
 * passed to git after {@code --} in a fixed argument list; this is the check that no part of either can be taken for an
 * option or another transport.
 *
 * <ul>
 *   <li><b>The URL</b> is {@code https} (any letter case) with a plain host name of letters, digits and inner hyphens,
 *       port 443 or none, no user information, no query, no fragment, no IP literal, and a path of plain segments
 *       ({@code [A-Za-z0-9._~-]}, none empty, {@code .} or {@code ..}, none starting with a hyphen). The host is then
 *       exactly on the git allowlist. The repository is rebuilt from the parsed parts: the lower-case host and the path.
 *   <li><b>The ref</b> is a 40- or 64-digit lower-case commit id, or {@code refs/heads/<name>} or
 *       {@code refs/tags/<name>} with a name of {@code [A-Za-z0-9._/-]}, 1 to 200 characters, none of whose components
 *       starts with a hyphen or a dot or ends with {@code .lock}, with no {@code ..}, no {@code //}, and no trailing
 *       slash or dot, so it also passes {@code git check-ref-format}.
 * </ul>
 */
final class GitSourceRules {

    private static final int MAX_URL_CHARS = 2048;
    private static final int MAX_REF_BYTES = 255;
    private static final int MAX_NAME_CHARS = 200;
    private static final int HTTPS_PORT = 443;
    private static final String PORT_SUFFIX = ":" + HTTPS_PORT;
    private static final String BRANCHES = "refs/heads/";
    private static final String TAGS = "refs/tags/";
    private static final Pattern HOST_NAME =
            Pattern.compile("(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");
    private static final Pattern PATH = Pattern.compile("(/[A-Za-z0-9._~-]+)+");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._/-]+");

    private GitSourceRules() {}

    /**
     * The repository a URL names.
     *
     * @param url the URL as sent
     * @param allowlist the git allowlist
     * @return the repository, rebuilt from the parsed parts
     * @throws CharterRefusal {@code url_refused} or {@code host_not_allowed}
     */
    static GitRepository repository(String url, GuideAllowlist allowlist) {
        URI uri = parse(url);
        String host = hostOf(uri);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw SourceRefusal.URL_REFUSED.refusal();
        }
        String path = uri.getRawPath();
        if (path == null || !plainPath(path)) {
            throw SourceRefusal.URL_REFUSED.refusal();
        }
        if (!allowlist.allows(host)) {
            throw SourceRefusal.HOST_NOT_ALLOWED.refusal();
        }
        return new GitRepository(host, "https://" + host + path);
    }

    private static URI parse(String url) {
        if (url.length() > MAX_URL_CHARS) {
            throw SourceRefusal.URL_REFUSED.refusal();
        }
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw SourceRefusal.URL_REFUSED.refusal();
            }
            return uri;
        } catch (URISyntaxException unparsable) {
            throw SourceRefusal.URL_REFUSED.refusal();
        }
    }

    /** The normalised host of an authority that has no user information, no IP literal and port 443 or none. */
    private static String hostOf(URI uri) {
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty() || authority.contains("@")) {
            throw SourceRefusal.URL_REFUSED.refusal();
        }
        String hostPart = authority.endsWith(PORT_SUFFIX)
                ? authority.substring(0, authority.length() - PORT_SUFFIX.length())
                : authority;
        String host = GuideAllowlist.normalise(hostPart);
        if (hostPart.contains(":") || !HOST_NAME.matcher(host).matches() || GuardedHttpsExchange.isIpLiteral(host)) {
            throw SourceRefusal.URL_REFUSED.refusal();
        }
        return host;
    }

    private static boolean plainPath(String path) {
        if (!PATH.matcher(path).matches()) {
            return false;
        }
        for (String segment : path.substring(1).split("/", -1)) {
            if (segment.equals(".") || segment.equals("..") || segment.startsWith("-")) {
                return false;
            }
        }
        return true;
    }

    /**
     * The ref of a request.
     *
     * @param ref the ref as sent
     * @return the ref
     * @throws CharterRefusal {@code bad_ref}
     */
    static GitRef ref(String ref) {
        if (COMMIT.matcher(ref).matches()) {
            return new GitRef(ref, GitRef.Kind.COMMIT);
        }
        if (ref.length() <= MAX_REF_BYTES) {
            if (ref.startsWith(BRANCHES) && plainName(ref.substring(BRANCHES.length()))) {
                return new GitRef(ref, GitRef.Kind.BRANCH);
            }
            if (ref.startsWith(TAGS) && plainName(ref.substring(TAGS.length()))) {
                return new GitRef(ref, GitRef.Kind.TAG);
            }
        }
        throw SourceRefusal.BAD_REF.refusal();
    }

    private static boolean plainName(String name) {
        if (name.isEmpty()
                || name.length() > MAX_NAME_CHARS
                || !NAME.matcher(name).matches()
                || name.contains("..")
                || name.endsWith("/")
                || name.endsWith(".")) {
            return false;
        }
        for (String component : name.split("/", -1)) {
            if (component.isEmpty()
                    || component.startsWith("-")
                    || component.startsWith(".")
                    || component.endsWith(".lock")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks the commit a request names.
     *
     * @param commit the commit as sent, or null
     * @return the commit, or null
     * @throws CharterRefusal {@code bad_ref} for a value that is not a full commit id
     */
    static @Nullable String commit(@Nullable String commit) {
        if (commit != null && !COMMIT.matcher(commit).matches()) {
            throw SourceRefusal.BAD_REF.refusal();
        }
        return commit;
    }
}
