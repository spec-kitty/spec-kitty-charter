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
 * The free-space refusal of NFR-009: a registration ends {@code refused} {@code 503 pack_storage_full} when the pack
 * volume has less than twice the archive bound (64 MiB) free, before the job writes anything. The tree is held twice
 * while it is stored, as staged and as copied beside the pack, which is why the room is twice the bound.
 */
public final class FreeSpaceCheck implements RegistrationJobs.StorageRoom {

    private static final long MIB = 1024L * 1024L;

    /** The archive bound of a registration, 32 MiB (NFR-004). */
    public static final long ARCHIVE_BOUND_BYTES = 32 * MIB;

    /** The room a registration needs: twice the archive bound. */
    public static final long REQUIRED_BYTES = 2 * ARCHIVE_BOUND_BYTES;

    private final Supplier<FileStore> volume;

    /**
     * A check over the volume the supplier names, asked on every check.
     *
     * @param volume the pack volume
     */
    public FreeSpaceCheck(Supplier<FileStore> volume) {
        this.volume = Objects.requireNonNull(volume, "volume");
    }

    /**
     * A check over the volume that holds {@code root}, or the nearest ancestor that exists when the root is not created
     * yet.
     *
     * @param root the pack domain's directory
     * @return the check
     */
    public static FreeSpaceCheck of(Path root) {
        Objects.requireNonNull(root, "root");
        return new FreeSpaceCheck(() -> volumeOf(root));
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
        long usable;
        try {
            usable = volume.get().getUsableSpace();
        } catch (IOException failure) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot read the free space of the pack volume", failure);
        }
        if (usable < REQUIRED_BYTES) {
            throw new CharterRefusal(
                    RefusalCode.PACK_STORAGE_FULL,
                    "The pack volume has " + usable / MIB + " MiB free; a registration needs " + REQUIRED_BYTES / MIB
                            + " MiB, twice the " + ARCHIVE_BOUND_BYTES / MIB + " MiB archive bound.");
        }
    }
}
