package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * The one rule that resolves a pack identifier to its configured location (#184), shared by the read side
 * ({@link DomainRepositoryService}), the commit store ({@link PackCommitStore}) and the write store
 * ({@link CommitStorePackWriteStore}), so a read and a write of a pack resolve the same configured location.
 *
 * <p>A pack is located by the registered pack id alone, the configured name of its location (#583, D3). The
 * {@code pack_id} a {@code pack.yaml} declares is the descriptor's identity, never a key here: an identifier no
 * location is configured as is refused {@code PACK_NOT_FOUND}, whatever the descriptors declare.
 *
 * <p>It also resolves a location to its directory, two ways. {@link #directoryOf} is the configured entry, which a
 * write addresses under the pack's write lock and which keys that lock. {@link #read} is what a reader that takes no
 * lock reads: the entry, or during a replace's swap window the aside copy that holds the last committed state, inside
 * an identity-checked re-read ({@link PackDirectories}, #490). Every lock-free read of a pack's files goes through it.
 */
final class PackLocator {

    private PackLocator() {}

    /**
     * The configured location of a pack.
     *
     * @param locations the configured pack locations
     * @param pack the registered pack id
     * @return the location configured as {@code pack}
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when no location is configured as the identifier
     */
    static PackLocation locate(Locations locations, PackIdentifier pack) {
        for (PackLocation location : locations.all()) {
            if (location.name().equals(pack.value())) {
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
}
