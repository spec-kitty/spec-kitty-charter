package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackReadout;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The persistence seam of a whole-pack registration (ADR-0065 D1, D12): it reads a staged tree, takes the registry lock
 * and the pack's lock in that order, and swaps a whole staged tree in place of the stored pack, or creates the pack.
 * It is an application-ring <em>driven port</em>: {@code PackCommitStore} implements the write side and
 * {@code WholePackStore} the {@link Loader}, so the service depends on neither.
 */
public interface WholePackWriteStore {

    /**
     * What a pack directory holds, assembled from one read.
     *
     * @param pack the aggregate; for a staged tree, built from a read the registration judged and found clean
     * @param facts what the compose reads of the pack: the artifacts it holds, the typed edges it writes (graph
     *     documents, derived relations and the registered shard) and its presets
     * @param digests a digest of each file-backed artifact as it would be written, companions included, so that two
     *     trees can be told apart artifact by artifact
     */
    record Contents(Pack pack, PackCompositionFacts facts, Map<Urn, String> digests) {

        /**
         * Checks the fields and copies the digests.
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

    /**
     * What can still be read of a stored pack that cannot be read whole: the artifacts it held, the digest of each one
     * that reads, and the edges of every graph document that parses.
     *
     * @param held the artifacts its files hold, as bare URNs: those that read, and one per file that does not, by the id the
     *     file's path names
     * @param digests the digest of each artifact that reads
     * @param edges the typed edges that can be read, from every graph file read as plain YAML, and the derived relations
     *     of the artifacts that read
     * @param looseEdges the entries of those graph files that cannot be typed (an unknown relation, an endpoint that is
     *     no reference): each by {@code <source>|<target>} as written, with its source, which is all that is gated
     */
    record Remains(Set<String> held, Map<Urn, String> digests, List<Relationship> edges, Map<String, Urn> looseEdges) {

        /**
         * Copies the collections.
         *
         * @param held the artifacts its files hold
         * @param digests the digest of each artifact that reads
         * @param edges the typed edges that can be read
         * @param looseEdges the entries that cannot be typed, by key, with their source
         */
        public Remains {
            held = Set.copyOf(held);
            digests = Map.copyOf(digests);
            edges = List.copyOf(edges);
            looseEdges = Map.copyOf(looseEdges);
        }
    }

    /** Reads a whole pack directory. {@code WholePackStore} is the one implementation. */
    interface Loader {

        /**
         * Assembles the aggregate of a staged tree from the one read the registration judged (#554, S1, FR-006): from the
         * readout's {@link PackReadout#contents() contents} alone, through the store's assembly. It reads nothing and
         * judges nothing: the registration refused the upload on the readout's rationale before it asks.
         *
         * @param read the one read of the staged tree, whose rationale passed
         * @return what the tree holds
         * @throws IllegalArgumentException when the read holds no assembly input, which a passing rationale never leaves
         */
        Contents loadStaged(PackReadout read);

        /**
         * Removes from the staged tree every file the one read lists as not held ({@link PackReadout#notHeld()}, each
         * also a {@code file_not_held} warning): a file that is not held is not pack content, so the revision of the tree
         * and the commit that follow must not see it (FR-005, FR-021). Only the staging directory is touched.
         *
         * @param staged the extracted upload
         * @param notHeld the files of it the store does not hold
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when a file cannot be
         *     removed
         */
        void removeNotHeld(StagedPack staged, List<NotHeldFile> notHeld);

        /**
         * Writes the staged tree's {@code pack-manifest.yaml} with the membership derived from what the one read holds
         * and every derived field stamped from the tree's bytes, creating it when the upload held none (#110, ADR-0042
         * amendment of 2026-11-12), so the revision of the tree and the commit that follow see the manifest the service
         * will hold. The membership comes from the read the registration judged, so the tree is not read again (#554,
         * FR-016). Only the staging directory is touched; it runs after {@link #removeNotHeld}.
         *
         * @param staged the extracted upload {@link #loadStaged} accepted
         * @param read the one read of it the registration judged
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when the manifest cannot be
         *     written
         */
        void stampManifest(StagedPack staged, PackReadout read);

        /**
         * Reads a stored pack's directory as it is, refusing what cannot be read as the reads do.
         *
         * @param pack the registered id the pack is stored under: what it holds is assembled under it
         * @param directory the stored pack's directory
         * @return what the pack holds
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when it cannot be read
         */
        Contents loadStored(PackIdentifier pack, Path directory);

        /**
         * Reads what can still be read of a stored pack that {@link #loadStored} refuses: every file on its own, as plain
         * YAML where it does not bind. An artifact file counts by the {@code id} it declares, and by the id its path
         * names when that differs or cannot be read; each entry of a graph file whose source can be read is an edge.
         *
         * @param pack the registered id the pack is stored under
         * @param directory the stored pack's directory
         * @return what the pack still holds
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when the set of artifacts
         *     cannot be determined at all, for example when the directory cannot be listed; it is never taken as empty
         */
        Remains salvageStored(PackIdentifier pack, Path directory);
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
     * The versions a pack name has been registered at, oldest first (IC-16): kept when the pack is deleted.
     *
     * @param packId the name the pack is stored under
     * @return the history; empty when the name was never registered
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when the history cannot be read
     */
    List<PackVersionHistory.Entry> versionHistory(String packId);

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
     * The directory entry under the writable root whose name is {@code packId} once case is folded and is not
     * {@code packId} itself: on a file system that folds case the two are one directory. Every entry counts, a directory
     * the listing does not serve as a pack (a name that is no pack id) included.
     *
     * @param packId the name the pack is stored under
     * @return the entry's name; empty when no entry folds to {@code packId}
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when the root cannot be listed
     */
    Optional<String> caseTwinOf(String packId);

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
     * A failure leaves nothing behind; a crash between the reservation and the move leaves an empty directory, which the
     * next registration of the name reclaims under the locks ({@link #exclusively}).
     *
     * @param packId the name the pack is stored under
     * @param staged the extracted upload
     * @return the revision of the stored state
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code REGISTRATION_CONFLICT} when the directory is
     *     taken, {@code PACK_UNREADABLE} when the write fails; nothing is left behind
     */
    PackRevision create(String packId, StagedPack staged);
}
