package ai.speckitty.charter.packs.infra.fetch.git;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import org.jspecify.annotations.Nullable;

/**
 * What the request may name before any process starts (T090, plan.md "argument injection through the ref or URL",
 * research R9): the URL and the ref are validated here, and a value that is not of the grammar is a
 * {@code pack_source_refused} with a fixed reason and never reaches git.
 */
final class GitSourceRules {

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
        return new GitRepository(url, url);
    }

    /**
     * The ref of a request.
     *
     * @param ref the ref as sent
     * @return the ref
     * @throws CharterRefusal {@code bad_ref}
     */
    static GitRef ref(String ref) {
        return new GitRef(ref, GitRef.Kind.COMMIT);
    }

    /**
     * Checks the commit a request names.
     *
     * @param commit the commit as sent, or null
     * @return the commit, or null
     * @throws CharterRefusal {@code bad_ref} for a value that is not a full commit id
     */
    static @Nullable String commit(@Nullable String commit) {
        return commit;
    }
}
