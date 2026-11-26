package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;

/** Commit stores with one or two collaborators of a test's own, the others the production ones (#659). */
public final class CommitStores {

    private CommitStores() {}

    /**
     * A commit store whose directory swap is {@code rename}: a test of the swap's atomicity or of a read in its window.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param rename the directory-swap step
     * @return the store
     */
    public static PackCommitStore swappingWith(
            Locations locations, SerializationStrategy strategy, PackCommitStore.DirectoryRename rename) {
        return new PackCommitStore(
                locations, strategy, rename, RegistryWriteLock.shared(), PackWriteGeneration.shared());
    }

    /**
     * A commit store over a registry lock and write generation of the test's own.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param registryLock the lock a whole-pack registration takes before the pack's own
     * @param generation the write generation a whole-pack swap is bracketed with
     * @return the store
     */
    public static PackCommitStore over(
            Locations locations,
            SerializationStrategy strategy,
            RegistryWriteLock registryLock,
            PackWriteGeneration generation) {
        return new PackCommitStore(locations, strategy, PackCommitStore.MOVE_ASIDE, registryLock, generation);
    }
}
