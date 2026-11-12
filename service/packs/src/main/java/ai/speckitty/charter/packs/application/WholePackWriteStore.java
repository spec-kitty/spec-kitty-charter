package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * A file of a pack that is not pack content (#451): an exclusion names it, or it is Markdown nothing models.
     *
     * @param file the pack-relative path
     * @param reason the reviewable reason of the rule that excludes it
     */
    record ExcludedFile(String file, String reason) {
        /**
         * Checks the fields.
         *
         * @param file the pack-relative path
         * @param reason the rule's reason
         */
        public ExcludedFile {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * The {@code file_not_held} warning a registration reports for this file (FR-021): the file named and the rule's
         * reason given.
         *
         * @return the warning
         */
        public PackFinding notHeld() {
            return new PackFinding(
                    PackFindingCode.FILE_NOT_HELD, file, List.of(), file + ": not held (" + reason + ")");
        }
    }

    /**
     * A pack directory read as the registration judges it.
     *
     * @param pack the aggregate; its read violations are already refused for a staged tree
     * @param facts what the compose reads of the pack: the artifacts it holds, the typed edges it writes (graph
     *     documents, derived relations and the registered shard) and its presets
     * @param digests a digest of each file-backed artifact as it would be written, companions included, so that two
     *     trees can be told apart artifact by artifact
     * @param warnings the load findings that do not refuse the pack, kept apart from its violations: the
     *     {@code prompt_slot_not_empty} warning of a step prompt in a pack whose descriptor declares no
     *     {@code schema_version}, and for a staged tree one {@code file_not_held} warning per file of {@code notHeld}
     *     ({@link ExcludedFile#notHeld}), in path order after the others. A warning never enters
     *     {@link Pack#violations()}.
     * @param notHeld the files of a staged tree the registration does not hold, in path order: what
     *     {@link Loader#removeNotHeld} strips; empty for a stored pack
     */
    record Contents(
            Pack pack,
            PackCompositionFacts facts,
            Map<Urn, String> digests,
            List<PackFinding> warnings,
            List<ExcludedFile> notHeld) {

        /**
         * Checks the fields and copies the collections.
         *
         * @param pack the aggregate
         * @param facts the composition facts
         * @param digests the digest of each file-backed artifact
         * @param warnings the load warnings
         * @param notHeld the files a registration does not hold
         */
        public Contents {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(facts, "facts");
            digests = Map.copyOf(digests);
            warnings = List.copyOf(warnings);
            notHeld = List.copyOf(notHeld);
        }
    }

    /**
     * What can still be read of a stored pack that cannot be read whole: the artifacts it held, the digest of each one
     * that reads, and the edges of every graph document that parses.
     *
     * @param defined the artifacts held, as bare URNs: those that read, and one per file that does not, by the id the
     *     file's path names
     * @param digests the digest of each artifact that reads
     * @param edges the typed edges that can be read, from every graph file read as plain YAML, and the derived relations
     *     of the artifacts that read
     * @param looseEdges the entries of those graph files that cannot be typed (an unknown relation, an endpoint that is
     *     no reference): each by {@code <source>|<target>} as written, with its source, which is all that is gated
     */
    record Remains(
            Set<String> defined, Map<Urn, String> digests, List<Relationship> edges, Map<String, Urn> looseEdges) {

        /**
         * Copies the collections.
         *
         * @param defined the artifacts held
         * @param digests the digest of each artifact that reads
         * @param edges the typed edges that can be read
         * @param looseEdges the entries that cannot be typed, by key, with their source
         */
        public Remains {
            defined = Set.copyOf(defined);
            digests = Map.copyOf(digests);
            edges = List.copyOf(edges);
            looseEdges = Map.copyOf(looseEdges);
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
         * Removes from the staged tree every file {@code contents} reports as not held ({@link Contents#notHeld}, each
         * also a {@code file_not_held} warning): an excluded file is not pack content, so the revision of the tree and the commit that follow must
         * not see it (FR-005, FR-021). Only the staging directory is touched.
         *
         * @param staged the extracted upload {@link #loadStaged} read
         * @param contents what {@link #loadStaged} answered for it
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when a file cannot be
         *     removed
         */
        void removeNotHeld(StagedPack staged, Contents contents);

        /**
         * Reads a stored pack's directory as it is, refusing what cannot be read as the reads do.
         *
         * @param directory the stored pack's directory
         * @return what the pack holds
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when it cannot be read
         */
        Contents loadStored(Path directory);

        /**
         * Reads what can still be read of a stored pack that {@link #loadStored} refuses: every file on its own, as plain
         * YAML where it does not bind. An artifact file counts by the {@code id} it declares, and by the id its path
         * names when that differs or cannot be read; each entry of a graph file whose source can be read is an edge.
         *
         * @param directory the stored pack's directory
         * @return what the pack still holds
         * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code PACK_UNREADABLE} when the set of artifacts
         *     cannot be determined at all, for example when the directory cannot be listed; it is never taken as empty
         */
        Remains salvageStored(Path directory);
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
