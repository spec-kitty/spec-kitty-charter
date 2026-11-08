package ai.speckitty.charter.packs.infra.fetch.git;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** The git source (FR-004, IC-05, C-008): a scaffold until the fetch is built. */
public final class GitSourceFetcher implements PackSourceFetcher {

    /** The most git fetches that run at once. */
    public static final int MAX_CONCURRENT = 2;

    /** The bytes of blobs a fetch may hold: 32 MiB. */
    public static final long MAX_BLOB_BYTES = 32L * 1024 * 1024;

    /** What git may add to the blobs in the directory of one fetch: its pack, its trees and its commit. */
    public static final long OBJECT_ALLOWANCE_BYTES = 8L * 1024 * 1024;

    /**
     * A fetcher over the host git.
     *
     * @param check the resolved git; the source is available only when it is enabled
     * @param allowlist the hosts a repository may be fetched from
     * @param timeout the deadline over the whole fetch
     * @param reader the bounded writer of the staged tree
     * @param resolver the name resolution of the host check
     * @param remote where git is sent
     */
    public GitSourceFetcher(
            GitVersionCheck check,
            GuideAllowlist allowlist,
            Duration timeout,
            PackTreeReader reader,
            GitHostResolver resolver,
            GitRemote remote) {
        this(check, allowlist, timeout, reader, resolver, remote, MAX_BLOB_BYTES + OBJECT_ALLOWANCE_BYTES, MAX_CONCURRENT);
    }

    GitSourceFetcher(
            GitVersionCheck check,
            GuideAllowlist allowlist,
            Duration timeout,
            PackTreeReader reader,
            GitHostResolver resolver,
            GitRemote remote,
            long maxFetchBytes,
            int maxConcurrent) {}

    @Override
    public PackSourceKind kind() {
        return PackSourceKind.GIT;
    }

    @Override
    public Staged fetch(PackSourceRequest request, Path directory) throws CharterRefusal {
        return new Staged(new StagedPack(directory), Map.of());
    }
}
