package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The one rule that resolves a pack identifier to its configured location (#184), shared by the read side
 * ({@link DomainRepositoryService}), the commit store ({@link PackCommitStore}) and the write store
 * ({@link CommitStorePackWriteStore}), so a read and a write of a pack resolve the same configured location.
 *
 * <p>A location whose configured name equals the identifier wins. Otherwise the first location whose
 * {@code pack.yaml} declares that {@code pack_id} is resolved. A directory name is never assumed to be the id. A
 * location that cannot declare it (no parent directory, no directory, an unreadable {@code pack.yaml}) is passed over,
 * and an identifier no location names or declares is refused {@code PACK_NOT_FOUND}.
 */
final class PackLocator {

    private PackLocator() {}

    /**
     * The configured location of a pack.
     *
     * @param locations the configured pack locations
     * @param strategy the format a {@code pack.yaml} is read in
     * @param pack the pack identifier, a configured name or a declared {@code pack_id}
     * @return the location
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when no location names or declares the identifier
     */
    static PackLocation locate(
            DomainRepositoryService.Locations locations, SerializationStrategy strategy, PackIdentifier pack) {
        List<PackLocation> configured = locations.all();
        for (PackLocation location : configured) {
            if (location.name().equals(pack.value())) {
                return location;
            }
        }
        for (PackLocation location : configured) {
            if (declaresId(location, strategy, pack)) {
                return location;
            }
        }
        // The identifier matches no configured pack: the caller asked wrongly (CharterRefusalCode pack_not_found).
        throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
    }

    /**
     * A location's pack directory, absolute and normalized: the directory every store of the pack addresses.
     *
     * @param location the location
     * @return the directory
     */
    static Path directoryOf(PackLocation location) {
        return location.root().toAbsolutePath().normalize();
    }

    private static boolean declaresId(PackLocation location, SerializationStrategy strategy, PackIdentifier pack) {
        Path directory = directoryOf(location);
        Path parent = directory.getParent();
        Path name = directory.getFileName();
        if (parent == null || name == null) {
            return false;
        }
        try {
            return new FilesystemPackStore(parent, strategy)
                    .readDescriptor(new PackIdentifier(name.toString()))
                    .packId()
                    .equals(pack.value());
        } catch (CharterRefusal | UncheckedIOException unreadable) {
            return false;
        }
    }
}
