package ai.speckitty.charter.packs.application;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The source a {@code POST /packs} names, as the request sent it: the kind decides which of the other members it takes,
 * and a member that is foreign to the kind is the fetcher's to refuse ({@code source_malformed}).
 *
 * @param kind the source kind
 * @param url for https_archive and git: the URL as sent
 * @param ref for git: the ref
 * @param commit for git: the commit the ref must resolve to
 * @param sha256 for https_archive: the digest the archive must have
 * @param path for filesystem: the relative path as sent
 */
public record PackSourceRequest(
        PackSourceKind kind,
        @Nullable String url,
        @Nullable String ref,
        @Nullable String commit,
        @Nullable String sha256,
        @Nullable String path) {

    /**
     * Checks the kind.
     *
     * @param kind the source kind
     * @param url the URL
     * @param ref the ref
     * @param commit the commit
     * @param sha256 the digest
     * @param path the relative path
     */
    public PackSourceRequest {
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * Whether this is a git source whose ref is a branch and that names no commit: what it registers is whatever the
     * branch points at when it is fetched, so the request must carry a precondition on the target pack (post-plan security
     * squad 7).
     *
     * @return true for {@code refs/heads/...} with no {@code commit}
     */
    public boolean branchWithoutCommit() {
        return false;
    }
}
