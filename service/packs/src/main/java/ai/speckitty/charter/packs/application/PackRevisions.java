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
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_NOT_FOUND} when no configured pack has
     *     this identifier, or with {@code PACK_UNREADABLE} when the pack cannot be read
     */
    PackRevision currentRevision(PackIdentifier pack);

    /**
     * The digest a cache of what was read from the pack's files is valid for: it changes whenever the files change,
     * their names included. The default is the revision's token, which digests bytes alone; an adapter that can see the
     * paths overrides it, so that an out-of-band rename, which leaves every byte as it was, is seen (#416 invariant 4).
     *
     * @param pack the pack's identity
     * @return the digest of the pack's files as they stand now
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #currentRevision}
     */
    default String digestOf(PackIdentifier pack) {
        return currentRevision(pack).token();
    }
}
