package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;

/**
 * The read side of the pack revision: what a read needs to answer an {@code ETag}, and nothing of the write path.
 * {@link PackWriteStore} extends it, so the one write-store binding serves both.
 */
@FunctionalInterface
public interface PackRevisions {

    /**
     * The revision of the pack's committed state as it stands now.
     *
     * @param pack the pack's identity
     * @return the current revision
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the pack cannot be
     *     resolved or read
     */
    PackRevision currentRevision(PackIdentifier pack);
}
