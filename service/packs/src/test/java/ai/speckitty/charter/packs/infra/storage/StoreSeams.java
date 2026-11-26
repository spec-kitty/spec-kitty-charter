package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Path;

/** Stores with one collaborator of a test's own, the others the production ones (#659). */
final class StoreSeams {

    private StoreSeams() {}

    /**
     * A store whose atomic writes move through {@code rename}: a test of write atomicity.
     *
     * @param root the directory that holds the pack directories
     * @param strategy the format
     * @param rename the move step
     * @return the store
     */
    static FilesystemPackStore renamingWith(
            Path root, SerializationStrategy strategy, FilesystemPackStore.Rename rename) {
        return new FilesystemPackStore(root, strategy, rename, Companions.Rules.bundled(), FilesystemPackStore.FILES);
    }

    /**
     * A store that reads a payload's bytes through {@code payloadRead}: a test of a payload listed but not readable.
     *
     * @param root the directory that holds the pack directories
     * @param strategy the format
     * @param payloadRead the read of an asset file's or a template's bytes
     * @return the store
     */
    static FilesystemPackStore readingPayloadsWith(
            Path root, SerializationStrategy strategy, FilesystemPackStore.PayloadRead payloadRead) {
        return new FilesystemPackStore(
                root, strategy, FilesystemPackStore.ATOMIC, Companions.Rules.bundled(), payloadRead);
    }
}
