package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The version history of a pack name (IC-16, FR-026, B1): every {@code (pack_version, revision)} pair the name was
 * registered with, kept by the registry beside the packs and surviving a {@code DELETE}. A {@code pack_version} is a
 * label, not an order, so a registration may go back to an older one; what it may not do is put other content under a
 * label that already names content, whether or not that label is the stored one.
 *
 * <p>The judge is a pure function of the history it is given. Reading and writing the file is
 * {@code infra.storage.VersionHistoryStore}; the swap that stores the pack writes the entry.
 */
public final class PackVersionHistory {

    private PackVersionHistory() {}

    /**
     * One registration of a pack name.
     *
     * @param packVersion the {@code pack_version} the pack carried
     * @param revision the revision token the stored pack had ({@code sha256:} and 64 hex digits)
     * @param registeredAt when it was stored
     */
    public record Entry(String packVersion, String revision, Instant registeredAt) {

        /**
         * Checks the fields.
         *
         * @param packVersion the {@code pack_version}
         * @param revision the revision token
         * @param registeredAt when it was stored
         */
        public Entry {
            Objects.requireNonNull(packVersion, "packVersion");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(registeredAt, "registeredAt");
        }
    }

    /**
     * Refuses an upload whose {@code pack_version} the history holds with another revision.
     *
     * @param packId the name the pack is stored under
     * @param history what the name was registered with
     * @param packVersion the {@code pack_version} of the upload
     * @param revision the revision of the upload
     * @throws CharterRefusal {@link RefusalCode#PACK_VERSION_CONFLICT} (409) when the version was registered with
     *     another revision
     */
    public static void requireNotReused(String packId, List<Entry> history, String packVersion, PackRevision revision) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        for (Entry entry : history) {
            if (entry.packVersion().equals(packVersion) && !entry.revision().equals(revision.token())) {
                throw new CharterRefusal(
                        RefusalCode.PACK_VERSION_CONFLICT,
                        "The pack " + packId + " was registered at pack_version " + packVersion + " on "
                                + entry.registeredAt() + " with another revision; send a new pack_version, or the"
                                + " content that was registered then.");
            }
        }
    }

    /**
     * The history with an entry added. A pair the history already holds is not recorded twice.
     *
     * @param history the history
     * @param entry the registration
     * @return a new list, the history when it already holds the pair
     */
    public static List<Entry> appended(List<Entry> history, Entry entry) {
        Objects.requireNonNull(entry, "entry");
        boolean held = history.stream()
                .anyMatch(known -> known.packVersion().equals(entry.packVersion())
                        && known.revision().equals(entry.revision()));
        if (held) {
            return history;
        }
        List<Entry> next = new ArrayList<>(history);
        next.add(entry);
        return List.copyOf(next);
    }

    /**
     * The history with the pair the stored pack has now, put back when the history knows nothing of that
     * {@code pack_version}: a registration whose entry was never published (the process died between its swap and the
     * publish). A history that already holds the version is returned as it is, whatever revision the stored pack has: a
     * per-artifact write moves the stored revision without registering anything, so its revision is no registration of
     * the version, and recording it would put a second revision under the label and make the label unrestorable.
     *
     * @param history the history
     * @param stored the pair the stored pack has now
     * @return a new list, or the history when it holds the stored pair's {@code pack_version}
     */
    public static List<Entry> healed(List<Entry> history, Entry stored) {
        Objects.requireNonNull(stored, "stored");
        boolean known = history.stream().anyMatch(entry -> entry.packVersion().equals(stored.packVersion()));
        return known ? history : appended(history, stored);
    }
}
