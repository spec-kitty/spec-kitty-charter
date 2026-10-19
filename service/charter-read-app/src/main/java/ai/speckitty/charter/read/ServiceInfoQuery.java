package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.PackSources;
import java.util.Objects;

/** The worktree the service serves and the fingerprint of the files it answers from (ADR-0011). */
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
     * The worktree and the current source fingerprint.
     *
     * @return the service info
     * @throws ai.speckitty.charter.domain.CharterRefusal with {@code PACK_UNREADABLE} when a pack file cannot be read
     */
    public ServiceInfo answer() {
        return new ServiceInfo(worktree, sources.fingerprint());
    }
}
