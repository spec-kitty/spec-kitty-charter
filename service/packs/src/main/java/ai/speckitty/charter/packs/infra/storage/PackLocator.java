package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * The one rule that resolves a pack identifier to its configured location (#184), shared by the read side
 * ({@link DomainRepositoryService}), the commit store ({@link PackCommitStore}) and the write store
 * ({@link CommitStorePackWriteStore}), so a read and a write of a pack resolve the same configured location.
 *
 * <p>A location whose configured name equals the identifier wins. Otherwise the first location whose
 * {@code pack.yaml} declares that {@code pack_id} is resolved. A directory name is never assumed to be the id. A
 * location that cannot declare it (no parent directory, no directory, no {@code pack.yaml}, a {@code pack.yaml} that
 * is no descriptor) is passed over. A {@code pack.yaml} that is there but cannot be read is refused
 * {@code PACK_UNREADABLE}, so a location that cannot be read never hides behind another that declares the same id.
 * An identifier no location names or declares is refused {@code PACK_NOT_FOUND}.
 *
 * <p>It also resolves a location to its directory, two ways. {@link #directoryOf} is the configured entry, which a
 * write addresses under the pack's write lock and which keys that lock. {@link #read} is what a reader that takes no
 * lock reads: the entry, or during a replace's swap window the aside copy that holds the last committed state, inside
 * an identity-checked re-read ({@link PackDirectories}, #490). Every lock-free read of a pack's files goes through it,
 * the declaration check of {@link #locate} included, so a pack addressed by its id is found during the window too.
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
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when no location names or declares the identifier;
     *     {@code PACK_UNREADABLE} when a location holds a {@code pack.yaml} that cannot be read
     */
    static PackLocation locate(Locations locations, SerializationStrategy strategy, PackIdentifier pack) {
        List<PackLocation> configured = locations.all();
        for (PackLocation location : configured) {
            if (location.name().equals(pack.value())) {
                return location;
            }
        }
        for (PackLocation location : configured) {
            if (declaresId(locations, location, strategy, pack)) {
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

    /**
     * Reads a location's pack without a lock: {@code body} is given the directory to read, the configured entry or,
     * while a replace is between its two renames, its aside copy, and its answer is kept only when no swap moved the
     * directory under it ({@link PackDirectories#read}).
     *
     * @param locations the configured pack locations, which say what the service may write
     * @param location the location
     * @param body the read, given the directory; it must not write
     * @param <T> what the read answers
     * @return what {@code body} answered
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when the entry is a create's empty reservation; {@code STALE_INPUT}
     *     when the pack was replaced again and again while it was read; what {@code body} threw otherwise
     */
    static <T> T read(Locations locations, PackLocation location, Function<Path, T> body) {
        return PackDirectories.read(directoryOf(location), location.name(), locations, body);
    }

    private static boolean declaresId(
            Locations locations, PackLocation location, SerializationStrategy strategy, PackIdentifier pack) {
        try {
            return read(locations, location, directory -> declares(location, directory, strategy, pack));
        } catch (CharterRefusal refusal) {
            if (refusal.code() == RefusalCode.PACK_NOT_FOUND) {
                return false; // a create's reservation declares nothing yet
            }
            throw refusal;
        }
    }

    private static boolean declares(
            PackLocation location, Path directory, SerializationStrategy strategy, PackIdentifier pack) {
        Path parent = directory.getParent();
        Path name = directory.getFileName();
        if (parent == null || name == null) {
            return false;
        }
        Path packYaml = directory.resolve(PackFileSet.PACK_YAML);
        if (!Files.exists(packYaml, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(packYaml) || !Files.isReadable(packYaml)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "pack.yaml of location " + location.name() + " cannot be read");
        }
        try {
            return new FilesystemPackStore(parent, strategy)
                    .readDescriptor(new PackIdentifier(name.toString()))
                    .packId()
                    .equals(pack.value());
        } catch (CharterRefusal malformed) {
            return false;
        }
    }
}
