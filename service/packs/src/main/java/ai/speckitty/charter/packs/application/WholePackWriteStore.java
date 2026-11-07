package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The persistence seam of a whole-pack registration (ADR-0065 D1, D12): it reads a staged tree, takes the registry lock
 * and the pack's lock in that order, and swaps a whole staged tree in place of the stored pack, or creates the pack.
 * It is an application-ring <em>driven port</em>: {@code PackCommitStore} implements the write side and
 * {@code WholePackStore} the {@link Loader}, so the service depends on neither.
 */
public interface WholePackWriteStore {

    /**
     * A pack directory read as the registration judges it.
     *
     * @param pack the aggregate; its read violations are already refused for a staged tree
     * @param facts what the compose reads of the pack: the artifacts it holds, the typed edges it writes (graph
     *     documents, derived relations and the registered shard) and its presets
     * @param digests a digest of each file-backed artifact as it would be written, companions included, so that two
     *     trees can be told apart artifact by artifact
     */
    record Contents(Pack pack, PackCompositionFacts facts, Map<Urn, String> digests) {

        /**
         * Checks the fields and copies the map.
         *
         * @param pack the aggregate
         * @param facts the composition facts
         * @param digests the digest of each file-backed artifact
         */
        public Contents {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(facts, "facts");
            digests = Map.copyOf(digests);
        }
    }

    /** Reads a whole pack directory. {@code WholePackStore} is the one implementation. */
    interface Loader {

        /**
         * Reads a staged tree, refusing everything that makes the upload wrong with {@code validation_failed} (422):
         * a read violation, an unmodelled file, an unreadable graph or descriptor, a registered shard without the
         * registration stamp.
         *
         * @param staged the extracted upload
         * @return what the tree holds
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code VALIDATION_FAILED}, with a violation per
         *     fault
         */
        Contents loadStaged(StagedPack staged);

        /**
         * Reads a stored pack's directory as it is, refusing what cannot be read as the reads do.
         *
         * @param directory the stored pack's directory
         * @return what the pack holds
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when it cannot be read
         */
        Contents loadStored(Path directory);
    }

    /**
     * The revision of a staged tree (research R4): the digest over the bytes of every file the stored pack's revision
     * covers, so a tree and the pack stored from it have one revision.
     *
     * @param staged the extracted upload
     * @return the revision
     */
    PackRevision revisionOf(StagedPack staged);

    /**
     * The directory a stored pack lives in.
     *
     * @param pack the stored pack
     * @return its live directory
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_NOT_FOUND} when no configured pack has
     *     this name
     */
    Path liveDirectory(PackIdentifier pack);

    /**
     * Checks that a pack named {@code packId} would be stored where the service writes: the entry
     * {@code <writable root>/<packId>} is no link, and its real path, taken without following links, is that path.
     *
     * @param packId the name the pack is stored under
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code VALIDATION_FAILED} (422) when it would not
     */
    void requireWritableLocation(String packId);

    /**
     * Runs a registration's decisive steps holding the registry write lock and then the pack's own lock, in that order.
     * The pack need not exist: its lock is that of the entry {@code <writable root>/<packId>}.
     *
     * @param packId the name the pack is stored under
     * @param work the decisive steps
     * @param <T> what the work answers
     * @return what the work answers
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time; the work did not run
     */
    <T> T exclusively(String packId, Supplier<T> work);

    /**
     * Replaces the stored pack with the staged tree whole: what the tree does not hold is gone. The tree is copied
     * beside the pack and swapped in by the two-rename swap, with the write generation odd for the swap alone.
     *
     * @param pack the stored pack
     * @param staged the extracted upload
     * @return the revision of the stored state
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when the swap fails; the pack
     *     is unchanged
     */
    PackRevision replaceWhole(PackIdentifier pack, StagedPack staged);

    /**
     * Creates the pack from the staged tree: the directory is reserved with one atomic step and the tree moved in.
     *
     * @param packId the name the pack is stored under
     * @param staged the extracted upload
     * @return the revision of the stored state
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code REGISTRATION_CONFLICT} when the directory is
     *     taken, {@code PACK_UNREADABLE} when the write fails; nothing is left behind
     */
    PackRevision create(String packId, StagedPack staged);
}
