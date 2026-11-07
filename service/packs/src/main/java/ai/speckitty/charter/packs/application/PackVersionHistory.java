package ai.speckitty.charter.packs.application;

import java.time.Instant;
import java.util.List;

/** Stub for the red commit. */
public final class PackVersionHistory {

    private PackVersionHistory() {}

    /**
     * One registration of a pack: the version it carried, the revision it had and when it was stored.
     *
     * @param packVersion the {@code pack_version}
     * @param revision the revision token
     * @param registeredAt when it was registered
     */
    public record Entry(String packVersion, String revision, Instant registeredAt) {}

    /**
     * Refuses a registration whose version was registered before with another revision.
     *
     * @param packId the pack
     * @param history what was registered
     * @param packVersion the version of the upload
     * @param revision the revision of the upload
     */
    public static void requireNotReused(
            String packId, List<Entry> history, String packVersion, PackRevision revision) {}

    /**
     * The history with an entry appended.
     *
     * @param history the history
     * @param entry the entry
     * @return the new history
     */
    public static List<Entry> appended(List<Entry> history, Entry entry) {
        return history;
    }
}
