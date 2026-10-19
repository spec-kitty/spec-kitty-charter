package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackSources;
import ai.speckitty.charter.domain.RefusalCode;
import java.util.Objects;

/** The worktree the service serves and the fingerprint of the inputs it answers from (ADR-0011). */
public final class ServiceInfoQuery {

    private final String worktree;
    private final PackSources sources;

    /**
     * A query for the worktree {@code worktree} over the files {@code sources} names.
     *
     * @param worktree the opaque id of the worktree the service serves
     * @param sources the pack files port; they are digested afresh for every answer
     */
    public ServiceInfoQuery(String worktree, PackSources sources) {
        this.worktree = Objects.requireNonNull(worktree, "worktree");
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    /**
     * The worktree id, which needs no file read.
     *
     * @return the opaque id of the worktree the service serves
     */
    public String worktree() {
        return worktree;
    }

    /**
     * The worktree and the current source fingerprint, digested twice. The answer carries the digest both reads agree
     * on.
     *
     * @return the service info
     * @throws ai.speckitty.charter.domain.CharterRefusal with {@code PACK_UNREADABLE} when an input cannot be read
     *     (not retryable), or with {@code STALE_INPUT} when the two digests differ because an input changed while
     *     the info was read (retryable: read it again)
     */
    public ServiceInfo answer() {
        String first = sources.fingerprint();
        String second = sources.fingerprint();
        if (!first.equals(second)) {
            throw new CharterRefusal(
                    RefusalCode.STALE_INPUT, "The inputs changed while the service info was read; read it again.");
        }
        return new ServiceInfo(worktree, first);
    }
}
