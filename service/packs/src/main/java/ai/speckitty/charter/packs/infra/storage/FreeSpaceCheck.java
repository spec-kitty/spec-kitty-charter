package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The free-space refusal of NFR-009: a registration is refused {@code 503 pack_storage_full} when the volumes it writes
 * to have less than its peak free, before it writes anything (#418, A6).
 *
 * <p>The peak of one registration is about three times the archive bound (32 MiB): the archive, as spooled; the tree it
 * expands to, which the extractor bounds at the same 32 MiB; and the copy of that tree the commit makes beside the pack
 * before the swap. The archive and the tree are in the staging root and the copy is in the pack domain. On one volume,
 * which is the default, the room is the whole peak, {@code archive + 2 x bound}; on two, the staging volume needs
 * {@code archive + bound} and the pack volume {@code bound}. The archive is the declared length of the upload at
 * admission, and the bound when it is not known (a chunked body, a source). When the job runs a spooled archive is on the
 * volume already, so the check asks only what is left to write, the tree and the copy; the peak of a job is still the
 * three, since the archive was counted at admission.
 */
public final class FreeSpaceCheck implements RegistrationJobs.StorageRoom {

    private static final long MIB = 1024L * 1024L;

    /** The archive bound of a registration, 32 MiB (NFR-004). */
    public static final long ARCHIVE_BOUND_BYTES = 32 * MIB;

    /** The room a registration of a full-size archive needs on one volume: its peak, three times the archive bound. */
    public static final long REQUIRED_BYTES = 3 * ARCHIVE_BOUND_BYTES;

    private final Supplier<FileStore> packVolume;
    private final Supplier<FileStore> stagingVolume;
    private final boolean oneSupplier;

    /**
     * A check over one volume, which holds the staging root and the pack domain, asked on every check.
     *
     * @param volume the volume
     */
    public FreeSpaceCheck(Supplier<FileStore> volume) {
        this.packVolume = Objects.requireNonNull(volume, "volume");
        this.stagingVolume = volume;
        this.oneSupplier = true;
    }

    /**
     * A check over the volume of the pack domain and the volume of the staging root, asked on every check. When they are
     * the same volume the whole peak is needed on it.
     *
     * @param packVolume the volume of the pack domain
     * @param stagingVolume the volume of the staging root
     */
    public FreeSpaceCheck(Supplier<FileStore> packVolume, Supplier<FileStore> stagingVolume) {
        this.packVolume = Objects.requireNonNull(packVolume, "packVolume");
        this.stagingVolume = Objects.requireNonNull(stagingVolume, "stagingVolume");
        this.oneSupplier = false;
    }

    /**
     * A check over the volumes that hold the pack domain and the staging root, or the nearest ancestor of each that exists
     * when it is not created yet.
     *
     * @param packRoot the pack domain's directory
     * @param stagingRoot the staging root
     * @return the check
     */
    public static FreeSpaceCheck of(Path packRoot, Path stagingRoot) {
        Objects.requireNonNull(packRoot, "packRoot");
        Objects.requireNonNull(stagingRoot, "stagingRoot");
        return new FreeSpaceCheck(() -> volumeOf(packRoot), () -> volumeOf(stagingRoot));
    }

    /** The volume of the nearest ancestor of {@code root} that exists, {@code root} itself when it does. */
    static FileStore volumeOf(Path root) {
        Path existing = root.toAbsolutePath().normalize();
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        try {
            return Files.getFileStore(Objects.requireNonNull(existing));
        } catch (IOException | NullPointerException failure) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot find the volume of the pack domain", failure);
        }
    }

    @Override
    public void require() {
        require(UNKNOWN_BYTES);
    }

    @Override
    public void require(long archiveBytes) {
        require(archiveBytes, 0);
    }

    @Override
    public void require(long archiveBytes, long openUploadBytes) {
        long archive = archiveBytes < 0 || archiveBytes > ARCHIVE_BOUND_BYTES ? ARCHIVE_BOUND_BYTES : archiveBytes;
        // The uploads already open spool into the staging root: what they are declared to write is not free for this
        // one.
        check(archive, Math.max(0, openUploadBytes));
    }

    /**
     * At run time a spooled archive is on the volume already, so what is left to write is the tree and the copy: two
     * bounds on one volume, a bound on each of two. A job that has still to fetch its archive (a source) is judged as
     * at admission, with a full-size archive to write.
     */
    @Override
    public void requireForRun(long archiveBytes) {
        check(archiveBytes < 0 ? ARCHIVE_BOUND_BYTES : 0, 0);
    }

    /** The room for {@code archive} bytes still to write, the tree and the copy, and {@code open} bytes of other uploads. */
    private void check(long archive, long open) {
        FileStore pack = packVolume.get();
        FileStore staging = oneSupplier ? pack : stagingVolume.get();
        String archiveHeld = archive > 0 ? "the archive, " : "";
        if (pack.equals(staging)) {
            requireFree(
                    pack,
                    "pack",
                    archive + 2 * ARCHIVE_BOUND_BYTES + open,
                    archiveHeld + "the tree it expands to and the copy"
                            + " the commit makes beside the pack, each at most the " + ARCHIVE_BOUND_BYTES / MIB
                            + " MiB bound" + openHolds(open));
        } else {
            requireFree(
                    staging,
                    "staging",
                    archive + ARCHIVE_BOUND_BYTES + open,
                    (archive > 0 ? "the archive and " : "") + "the tree it expands to" + openHolds(open));
            requireFree(pack, "pack", ARCHIVE_BOUND_BYTES, "the copy the commit makes beside the pack");
        }
    }

    private static String openHolds(long open) {
        return open == 0 ? "" : ", and the " + open / MIB + " MiB the uploads already open are declared to write";
    }

    private static void requireFree(FileStore volume, String name, long needed, String holds) {
        long usable;
        try {
            usable = volume.getUsableSpace();
        } catch (IOException failure) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot read the free space of the " + name + " volume", failure);
        }
        if (usable < needed) {
            throw new CharterRefusal(
                    RefusalCode.PACK_STORAGE_FULL,
                    "The " + name + " volume has " + usable / MIB + " MiB free; a registration needs " + needed / MIB
                            + " MiB there: " + holds + ".");
        }
    }
}
