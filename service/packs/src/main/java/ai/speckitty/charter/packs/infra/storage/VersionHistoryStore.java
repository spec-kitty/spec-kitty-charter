package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.PackVersionHistory.Entry;
import java.nio.file.Path;
import java.util.List;

/** Stub for the red commit. */
public final class VersionHistoryStore {

    private final Path writableRoot;

    /**
     * A store under the writable pack root.
     *
     * @param writableRoot the directory packs are written into
     */
    public VersionHistoryStore(Path writableRoot) {
        this.writableRoot = writableRoot;
    }

    /**
     * The file of a pack's history.
     *
     * @param packId the pack
     * @return the path
     */
    public Path fileOf(String packId) {
        return writableRoot.resolve(".history").resolve(packId + ".json");
    }

    /**
     * Reads a pack's history.
     *
     * @param packId the pack
     * @return the entries
     */
    public List<Entry> read(String packId) {
        return List.of();
    }

    /**
     * Writes the next history beside its file.
     *
     * @param packId the pack
     * @param next the entries
     * @return the staged file
     */
    public Path stage(String packId, List<Entry> next) {
        return fileOf(packId);
    }

    /**
     * Publishes a staged history.
     *
     * @param packId the pack
     * @param staged the staged file
     */
    public void publish(String packId, Path staged) {}
}
