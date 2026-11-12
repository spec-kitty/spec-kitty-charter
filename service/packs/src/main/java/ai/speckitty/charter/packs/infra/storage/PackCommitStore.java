package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackVersionHistory;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The aggregate-commit store (ADR-0026 Decision 3, NFR-002): it commits a registration against a pack atomically, so a
 * refused or failed registration changes <strong>zero</strong> stored files.
 *
 * <p>A commit stages a complete copy of the live pack directory into a sibling temporary directory, writes only the
 * one artefact the registration added or replaced and (when it changed) the registered-relationship shard into the
 * staging copy through the {@link SerializationStrategy}, so the copied descriptor and siblings stay byte-identical,
 * then <em>atomically swaps</em> the staging directory into place. The swap is the
 * {@link DirectoryRename} seam: its default moves the live directory aside with an atomic rename, moves the staging
 * directory into place, and deletes the aside copy; a failure at the second step restores the live directory and
 * rethrows. On any failure <em>before</em> the swap, the live directory is byte-for-byte unchanged and the staging
 * directory is cleaned up.
 *
 * <p><strong>Filesystem assumption.</strong> The live directory, the staging directory and the aside directory are
 * all created under the same parent, hence on one filesystem, where a directory rename is atomic (POSIX
 * {@code rename(2)} within a mount). Where the platform cannot rename atomically the swap falls back to a plain move.
 * The live directory is the pack's real path: for a pack whose directory is a symbolic link, the staging and aside
 * copies sit beside the link's target and the target is swapped, so the link stays a link (#174, ADR-0038 decision 3
 * as amended 2026-10-31).
 *
 * <p><strong>Crash window.</strong> The swap is two renames, so a process that dies between them leaves no live
 * directory, only the {@code <name>.pack-old-*} aside copy (and the staging copy); for a linked pack the target is
 * missing, its aside copy sits beside it and is named after it, and the link dangles. The next listing of the pack
 * domain repairs it under the pack's write lock ({@link StorageRootLocations}, {@link PackWriteLocks},
 * <a href="https://github.com/spec-kitty/spec-kitty-charter/issues/281">#281</a>): {@link #missingLiveDirectory(Path)}
 * finds the missing directory, following a dangling link to its target, and {@link #restoreInterruptedSwap(Path)} moves
 * a sole aside copy back, which restores the last committed state; the interrupted commit was never acknowledged. A
 * successful swap hides its aside copy ({@code .pack-discard-*}) before deleting it, and an aside copy found beside a
 * live directory (a crash after the second rename) is hidden by the next listing or commit
 * ({@link #hideStaleAsides(Path)}), so a stale copy is not restored over a pack that is later removed on purpose. A
 * crash in the instant between the second rename and that hiding, followed by the pack's removal before any listing,
 * is the one case left.
 *
 * <p>This store never performs the per-child {@code save()} upsert of {@link DomainRepositoryService}; registration
 * goes load &rarr; mutate &rarr; revalidate the whole aggregate in memory &rarr; commit the delta here.
 */
@Service
public final class PackCommitStore implements WholePackWriteStore {

    /**
     * The failure-injection seam: the atomic swap of the fully staged directory into the live directory's place. The
     * default moves the live directory aside and the staging directory into place; a test supplies one that throws
     * to prove that a pre-swap failure changes no live file.
     */
    @FunctionalInterface
    interface DirectoryRename {
        /**
         * Swaps {@code staging} into the place of {@code live}.
         *
         * @param staging the fully staged directory
         * @param live the live pack directory to replace
         * @throws IOException when the swap fails; the live directory must be left intact
         */
        void swap(Path staging, Path live) throws IOException;
    }

    private static final DirectoryRename MOVE_ASIDE = (staging, live) -> {
        Path aside = live.resolveSibling(live.getFileName() + ".pack-old-" + unique());
        moveDirectory(live, aside);
        try {
            moveDirectory(staging, live);
        } catch (IOException swapFailed) {
            // The staging move failed with the live directory already moved aside: restore it, leave staging to the
            // caller's cleanup, and rethrow. The live directory is whole again, byte for byte.
            try {
                moveDirectory(aside, live);
            } catch (IOException restoreFailed) {
                swapFailed.addSuppressed(restoreFailed);
            }
            throw swapFailed;
        }
        deleteTree(hidden(aside));
    };

    /** The most links a dangling chain is followed through, as the usual {@code ELOOP} bound. */
    private static final int MAX_LINK_HOPS = 40;

    private static final Logger LOG = LoggerFactory.getLogger(PackCommitStore.class);

    private final Locations locations;
    private final SerializationStrategy strategy;
    private final DirectoryRename rename;
    private final RegistryWriteLock registryLock;
    private final PackWriteGeneration generation;

    /**
     * A commit store that swaps directories atomically.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     */
    @Autowired
    public PackCommitStore(Locations locations, SerializationStrategy strategy) {
        this(locations, strategy, MOVE_ASIDE, RegistryWriteLock.shared(), PackWriteGeneration.shared());
    }

    /**
     * A commit store over an explicit registry lock and write generation; a test injects a short lock timeout and a
     * generation of its own.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param registryLock the lock a whole-pack registration takes before the pack's own
     * @param generation the write generation a whole-pack swap is bracketed with
     */
    public PackCommitStore(
            Locations locations,
            SerializationStrategy strategy,
            RegistryWriteLock registryLock,
            PackWriteGeneration generation) {
        this(locations, strategy, MOVE_ASIDE, registryLock, generation);
    }

    /**
     * A commit store with an injected swap step; the seam for the NFR-002 atomicity test.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param rename the directory-swap step
     */
    PackCommitStore(Locations locations, SerializationStrategy strategy, DirectoryRename rename) {
        this(locations, strategy, rename, RegistryWriteLock.shared(), PackWriteGeneration.shared());
    }

    /**
     * A commit store with an injected swap step, lock and generation.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param rename the directory-swap step
     * @param registryLock the lock a whole-pack registration takes before the pack's own
     * @param generation the write generation a whole-pack swap is bracketed with
     */
    PackCommitStore(
            Locations locations,
            SerializationStrategy strategy,
            DirectoryRename rename,
            RegistryWriteLock registryLock,
            PackWriteGeneration generation) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.rename = Objects.requireNonNull(rename, "rename");
        this.registryLock = Objects.requireNonNull(registryLock, "registryLock");
        this.generation = Objects.requireNonNull(generation, "generation");
    }

    /**
     * Commits a mutated pack as one atomic directory swap and returns its new revision.
     *
     * @param pack the pack to commit
     * @param ifMatch the {@code If-Match} the registration presented, compared with the live revision before anything
     *     is staged, or null when it presented none; the caller holds the pack's write lock across the comparison and
     *     the swap
     * @param artifact the added or replaced artefact of an already revalidated aggregate, or null when only the shard
     *     changes; nothing else of the pack is rewritten
     * @param relationshipShardText the registered-relationship shard text to persist, or null to leave it as staged
     * @return the revision of the committed state
     * @throws PreconditionException {@link PreconditionException.Kind#STALE} when {@code ifMatch} does not match the
     *     live revision; nothing is staged or written
     * @throws CharterRefusal with {@code PACK_NOT_FOUND} when no configured pack has this identifier, or with
     *     {@code PACK_UNREADABLE} when the pack is not a directory, is read-only through the service (a pack linked
     *     from outside the configuration root), or the commit fails; in every failure case the live pack directory is
     *     unchanged
     */
    public PackRevision commit(
            PackIdentifier pack,
            @Nullable String ifMatch,
            @Nullable CharterArtifact artifact,
            @Nullable String relationshipShardText) {
        return commitEdited(pack, ifMatch, (staging, parent) -> {
            if (artifact != null) {
                PackIdentifier stagingKey = new PackIdentifier(String.valueOf(staging.getFileName()));
                DomainRepositoryService.writeArtifact(new FilesystemPackStore(parent, strategy), stagingKey, artifact);
            }
            if (relationshipShardText != null) {
                Files.writeString(
                        staging.resolve(RelationshipShard.SHARD_PATH), relationshipShardText, StandardCharsets.UTF_8);
            }
        });
    }

    /**
     * Commits a removal as one atomic directory swap and returns its new revision: the files go, the registered shard and
     * the presets given are written whole, and nothing else of the pack is rewritten. The version history is not touched:
     * a removal registers no version (IC-16). The caller holds the registry lock and the pack's lock, and has checked the
     * {@code If-Match} under them.
     *
     * @param pack the pack to commit
     * @param files the pack-relative files to remove: an artifact's own and its companions
     * @param relationshipShardText the registered-relationship shard text to persist, or null to leave it as staged
     * @param presets the text of each preset file to write, by its pack-relative path
     * @return the revision of the committed state
     * @throws CharterRefusal with {@code PACK_NOT_FOUND} when no configured pack has this identifier, or with
     *     {@code PACK_UNREADABLE} when the pack is read-only through the service or the commit fails; in every failure
     *     case the live pack directory is unchanged
     */
    PackRevision remove(
            PackIdentifier pack,
            List<String> files,
            @Nullable String relationshipShardText,
            Map<String, String> presets) {
        return commitEdited(pack, null, (staging, parent) -> {
            for (String file : files) {
                Files.deleteIfExists(staging.resolve(file));
                pruneEmptied(staging, staging.resolve(file).getParent());
            }
            if (relationshipShardText != null) {
                Files.writeString(
                        staging.resolve(RelationshipShard.SHARD_PATH), relationshipShardText, StandardCharsets.UTF_8);
            }
            for (Map.Entry<String, String> preset : presets.entrySet()) {
                Files.writeString(staging.resolve(preset.getKey()), preset.getValue(), StandardCharsets.UTF_8);
            }
        });
    }

    /**
     * Removes the directories a removal left empty, from the removed file's own up to the pack's root, which stays: a
     * mission type's {@code steps/} and {@code templates/} go with its last file (D10 of #99).
     */
    private static void pruneEmptied(Path staging, @Nullable Path directory) throws IOException {
        Path at = directory;
        while (at != null && !at.equals(staging) && at.startsWith(staging) && isEmptyDirectory(at)) {
            Files.delete(at);
            at = at.getParent();
        }
    }

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    /** What a commit does to its staged copy of the pack before the swap. */
    @FunctionalInterface
    private interface StagingEdit {
        /**
         * Edits the staged copy.
         *
         * @param staging the staged copy of the live directory
         * @param parent the directory that holds the live directory and the staged copy
         * @throws IOException when a write fails; the commit then changes nothing
         */
        void apply(Path staging, Path parent) throws IOException;
    }

    private PackRevision commitEdited(PackIdentifier pack, @Nullable String ifMatch, StagingEdit edit) {
        Objects.requireNonNull(pack, "pack");
        // The commit stages beside the live directory and swaps it, so a configured link keeps the link and its target
        // takes the new content (#174); the real path is captured once and reused below so a retargeted link cannot
        // redirect the swap, and the same gate refuses a pack linked from outside the configuration root (#287).
        Path liveDir = DomainRepositoryService.writableRealDirectory(locations, resolve(pack).directory, pack);
        Path parent = requireParent(liveDir);
        @Nullable Path staging = null;
        try {
            if (ifMatch != null && !PackRevision.matches(ifMatch, revisionOf(liveDir, pack))) {
                throw new PreconditionException(
                        PreconditionException.Kind.STALE, "The If-Match precondition is stale; read the pack again.");
            }
            hideStaleAsides(liveDir); // so a stale aside copy is never restored over this pack later (#281)
            staging = Files.createTempDirectory(parent, ".pack-commit-");
            copyTree(liveDir, staging);
            edit.apply(staging, parent);
            PackRevision revision = revisionOf(staging, pack);
            rename.swap(staging, liveDir);
            staging = null; // swapped into place; there is nothing left to clean up
            return revision;
        } catch (CharterRefusal | PreconditionException refusal) {
            deleteTree(staging);
            throw refusal;
        } catch (IOException | RuntimeException failure) {
            deleteTree(staging);
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot commit pack " + pack.value(), failure);
        }
    }

    // ---- whole-pack registration (ADR-0065) ------------------------------------------------------------------------

    @Override
    public PackRevision revisionOf(StagedPack staged) {
        try {
            return revisionOf(
                    staged.directory(),
                    new PackIdentifier(String.valueOf(staged.directory().getFileName())));
        } catch (IOException failure) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot read the staged pack", failure);
        }
    }

    @Override
    public Path liveDirectory(PackIdentifier pack) {
        return resolve(pack).directory;
    }

    @Override
    public void requireWritableLocation(String packId) {
        Path root = writableRoot();
        Path entry = root.resolve(packId);
        if (!Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(entry)) {
                throw invalidLocation(packId, "is a link that does not resolve");
            }
            return; // nothing stored there: a create takes the entry
        }
        if (Files.isSymbolicLink(entry) || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
            throw invalidLocation(packId, "is not a plain directory of the pack domain");
        }
        try {
            if (!entry.toRealPath().equals(root.toRealPath().resolve(packId))) {
                throw invalidLocation(packId, "does not resolve to the entry of that name in the pack domain");
            }
        } catch (IOException unresolved) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot resolve the pack directory " + packId, unresolved);
        }
    }

    private static CharterRefusal invalidLocation(String packId, String why) {
        return new CharterRefusal(
                RefusalCode.VALIDATION_FAILED,
                "The pack directory " + packId + " " + why + "; a pack is stored at <config-root>/packs/<packId>.");
    }

    /** The one directory packs are written into; a fixed set of locations has none, and cannot take a registration. */
    private Path writableRoot() {
        return locations
                .writeRoot()
                .orElseThrow(
                        () -> new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the pack domain has no writable root"));
    }

    @Override
    public <T> T exclusively(String packId, Supplier<T> work) {
        // The registry lock first, then the pack's own, as every pack write does (R-lock); the pack's lock is that of
        // the
        // entry whether or not a pack is stored there yet, so a create and a replace of one name exclude each other.
        return registryLock.exclusively(() -> underPackLock(writableRoot().resolve(packId), work));
    }

    private static <T> T underPackLock(Path entry, Supplier<T> work) {
        ReentrantLock lock = PackWriteLocks.of(entry);
        lock.lock();
        try {
            // A swap a crash interrupted is repaired before the work reads the pack (#281), as on a listing.
            missingLiveDirectory(entry).ifPresent(PackCommitStore::restoreInterruptedSwap);
            reclaimInterruptedCreate(entry);
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes an empty directory at the entry: the reserved directory of a create that a crash interrupted between its
     * reservation and its move. The reservation exists only while the registry lock and the pack's lock are held, which
     * the caller does, so an empty entry is never a create in flight; left alone it would be served as a pack with no
     * {@code pack.yaml} and wedge the name.
     */
    private static void reclaimInterruptedCreate(Path entry) {
        if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> children = Files.list(entry)) {
            if (children.findAny().isEmpty()) {
                Files.delete(entry);
            }
        } catch (IOException failure) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot inspect the pack directory " + entry.getFileName(), failure);
        }
    }

    @Override
    public List<PackVersionHistory.Entry> versionHistory(String packId) {
        return new VersionHistoryStore(writableRoot()).read(packId);
    }

    /**
     * The version history's next state for a tree about to be stored, written beside its file before the swap and
     * published right after it (IC-16): a write that fails does so before the pack changes, and the rename that
     * publishes it cannot meet a full disk.
     */
    private record HistoryWrite(VersionHistoryStore store, String packId, Path staged) {

        /**
         * Stages the history after the tree is stored. The pair the stored pack has now (the live directory, when there is
         * one) goes in first when the history knows nothing of its {@code pack_version}, so that a publish lost after an
         * earlier swap heals here and a version label the stored pack holds is never free to reuse (IC-16). A version the
         * history already holds is left with the revision it was registered with: a per-artifact write moved the stored
         * revision, which registers nothing. A stored pack that cannot be read has no pair to give.
         */
        static HistoryWrite stage(
                Path writableRoot, String packId, Path tree, PackRevision revision, @Nullable Path live)
                throws IOException {
            VersionHistoryStore store = new VersionHistoryStore(writableRoot);
            List<PackVersionHistory.Entry> history = store.read(packId);
            if (live != null) {
                Optional<PackVersionHistory.Entry> stored = storedPair(live, packId);
                if (stored.isPresent()) {
                    history = PackVersionHistory.healed(history, stored.get());
                }
            }
            PackVersionHistory.Entry entry =
                    new PackVersionHistory.Entry(packVersionOf(tree), revision.token(), Instant.now());
            return new HistoryWrite(store, packId, store.stage(packId, PackVersionHistory.appended(history, entry)));
        }

        private static Optional<PackVersionHistory.Entry> storedPair(Path live, String packId) {
            try {
                return Optional.of(new PackVersionHistory.Entry(
                        packVersionOf(live),
                        revisionOf(live, new PackIdentifier(packId)).token(),
                        Instant.now()));
            } catch (IOException | RuntimeException unreadable) {
                return Optional.empty();
            }
        }

        /** Publishes the history; a failure now is a warning, because the pack has been stored and the next write heals it. */
        void publishAfterSwap() {
            try {
                store.publish(packId, staged);
            } catch (CharterRefusal lost) {
                LOG.warn(
                        "The pack {} was stored, but its version history could not be published ({}); the next"
                                + " registration puts the entry back.",
                        packId,
                        lost.getMessage());
            }
        }

        void discard() {
            store.discard(staged);
        }

        private static String packVersionOf(Path tree) throws IOException {
            String text = Files.readString(tree.resolve("pack.yaml"), StandardCharsets.UTF_8);
            if (PackDescriptorReader.read(text) instanceof FileRead.Read<PackDescriptor> read) {
                return read.value().packVersion();
            }
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the tree to store has no readable pack.yaml");
        }
    }

    private static void discard(@Nullable HistoryWrite history) {
        if (history != null) {
            history.discard();
        }
    }

    @Override
    public PackRevision replaceWhole(PackIdentifier pack, StagedPack staged) {
        Resolved resolved = resolve(pack);
        Path liveDir = DomainRepositoryService.writableRealDirectory(locations, resolved.directory, pack);
        Path parent = requireParent(liveDir);
        hideStaleAsides(liveDir); // so a stale aside copy is never restored over this pack later (#281)
        @Nullable Path copy = null;
        @Nullable HistoryWrite history = null;
        try {
            // The tree is copied beside the pack, on its filesystem, so that the swap is a rename; the staging root may
            // be on another one.
            copy = Files.createTempDirectory(parent, ".pack-commit-");
            copyTree(staged.directory(), copy);
            PackRevision revision = revisionOf(copy, pack);
            history = HistoryWrite.stage(writableRoot(), pack.value(), copy, revision, liveDir);
            swapBracketed(resolved.name, copy, liveDir); // keyed by the configured name, as every write is
            copy = null; // swapped into place; there is nothing left to clean up
            history.publishAfterSwap();
            return revision;
        } catch (CharterRefusal refusal) {
            deleteTree(copy);
            discard(history);
            throw refusal;
        } catch (IOException | RuntimeException failure) {
            deleteTree(copy);
            discard(history);
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot replace pack " + pack.value(), failure);
        }
    }

    /** The write generation is odd for the swap alone: the copy and the revision, which are slow, run outside it. */
    private void swapBracketed(String name, Path staging, Path live) throws IOException {
        generation.begin(name);
        try {
            rename.swap(staging, live);
        } finally {
            generation.end(name);
        }
    }

    @Override
    public PackRevision create(String packId, StagedPack staged) {
        Path root = writableRoot();
        Path destination = root.resolve(packId);
        @Nullable Path staging = null;
        @Nullable HistoryWrite history = null;
        try {
            Files.createDirectories(root);
            staging = Files.createTempDirectory(root, ".pack-write-");
            copyTree(staged.directory(), staging);
            PackRevision revision = revisionOf(staging, new PackIdentifier(packId));
            history = HistoryWrite.stage(root, packId, staging, revision, null);
            placeBracketed(packId, staging, destination);
            staging = null; // moved into place; nothing left to clean up
            history.publishAfterSwap();
            return revision;
        } catch (CharterRefusal refusal) {
            deleteTree(staging);
            discard(history);
            throw refusal;
        } catch (IOException | RuntimeException failure) {
            deleteTree(staging);
            discard(history);
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot create pack " + packId, failure);
        }
    }

    /**
     * Reserves the destination with one atomic step, which fails when anything is there, and renames the staged tree
     * over it, with the write generation odd for that alone: the reserved directory is empty, and a read in that
     * instant must be told to retry.
     */
    private void placeBracketed(String name, Path staging, Path destination) throws IOException {
        generation.begin(name);
        try {
            Path reserved = reserve(destination);
            try {
                moveOver(staging, reserved);
            } catch (IOException | RuntimeException failed) {
                release(reserved);
                throw failed;
            }
        } finally {
            generation.end(name);
        }
    }

    private static Path reserve(Path destination) throws IOException {
        try {
            return Files.createDirectory(destination);
        } catch (FileAlreadyExistsException taken) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT, "The pack " + destination.getFileName() + " already exists.");
        }
    }

    /** Renames a staged tree over the empty directory a write reserved. */
    static void moveOver(Path staging, Path reserved) throws IOException {
        try {
            Files.move(staging, reserved, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | java.nio.file.AccessDeniedException notAtomic) {
            // Windows refuses MOVEFILE_REPLACE_EXISTING onto a directory with ACCESS_DENIED (probe for #510).
            Files.delete(reserved);
            Files.move(staging, reserved);
        }
    }

    /** Removes a reserved target if it is still the empty directory the write created. */
    static void release(@Nullable Path reserved) {
        if (reserved == null) {
            return;
        }
        try {
            Files.deleteIfExists(reserved);
        } catch (IOException ignored) {
            // the original failure is the one worth reporting
        }
    }

    /**
     * The directory a crash between the swap's two renames may have left missing for a pack entry: the entry itself
     * for a plain pack, the final target of its link chain for a linked one. It is written as the real path of its
     * parent plus its name, which is where the swap left the aside copy, since a commit swaps the real path. Each link
     * is read with {@link Files#readSymbolicLink} and resolved against the real path of the directory that holds it,
     * since {@code toRealPath} fails on a dangling link and a lexical {@code ..} under a linked directory goes wrong.
     *
     * @param entry the pack's entry under the pack domain
     * @return the missing directory, or empty when it exists or the chain cannot be followed
     */
    static Optional<Path> missingLiveDirectory(Path entry) {
        Path current = entry;
        try {
            for (int hop = 0; hop <= MAX_LINK_HOPS; hop++) {
                Path parent = current.getParent();
                Path name = current.getFileName();
                if (parent == null || name == null) {
                    return Optional.empty();
                }
                if (!Files.isSymbolicLink(current)) {
                    boolean missing = !Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                            && !".".equals(name.toString())
                            && !"..".equals(name.toString());
                    return missing ? Optional.of(parent.toRealPath().resolve(name)) : Optional.empty();
                }
                current = parent.toRealPath().resolve(Files.readSymbolicLink(current));
            }
        } catch (IOException unresolved) {
            // A parent that does not resolve holds no aside copy the service left.
        }
        return Optional.empty();
    }

    /**
     * Repairs a swap a crash interrupted between its two renames: when {@code live} is missing and exactly one
     * {@code <name>.pack-old-*} directory sits beside it, that aside copy (the last committed state) is moved back into
     * place. With zero or several candidates nothing is guessed. The caller must hold the pack's write lock, since a
     * commit in flight is in that same state for an instant.
     *
     * @param live the pack's live directory, not a link: for a linked pack, its target
     * @return whether an aside copy was moved back
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the restore fails
     */
    static boolean restoreInterruptedSwap(Path live) {
        Path parent = live.getParent();
        if (parent == null || Files.exists(live, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(parent)) {
            return false;
        }
        List<Path> asides = asidesOf(live);
        if (asides.size() != 1) {
            return false;
        }
        try {
            moveDirectory(asides.getFirst(), live);
            return true;
        } catch (IOException failure) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot restore " + live + " after an interrupted commit", failure);
        }
    }

    /**
     * The {@code <name>.pack-old-*} aside copies beside a live directory.
     *
     * @param live the pack's live directory, not a link: for a linked pack, its target
     * @return the aside copies, in no particular order
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the parent cannot be listed
     */
    static List<Path> asidesOf(Path live) {
        Path parent = live.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return List.of();
        }
        String prefix = live.getFileName() + ".pack-old-";
        try (Stream<Path> siblings = Files.list(parent)) {
            return siblings.filter(
                            sibling -> String.valueOf(sibling.getFileName()).startsWith(prefix))
                    .filter(sibling -> Files.isDirectory(sibling, LinkOption.NOFOLLOW_LINKS))
                    .toList();
        } catch (IOException failure) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot inspect the parent of " + live, failure);
        }
    }

    /**
     * Hides the aside copies beside a live directory that exists: they are residue of a swap that finished (a crash
     * after its second rename, or a delete that failed), never a state to restore, and left under their
     * {@code .pack-old-*} name they would be restored if the pack were later removed on purpose. The caller must hold
     * the pack's write lock, since a commit in flight has its aside copy beside the new live directory for an instant.
     *
     * @param live the pack's live directory, not a link: for a linked pack, its target
     * @return whether any aside copy was hidden
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the parent cannot be listed
     */
    static boolean hideStaleAsides(Path live) {
        if (!Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        boolean hidden = false;
        for (Path aside : asidesOf(live)) {
            hidden |= hidden(aside) != null;
        }
        return hidden;
    }

    // ---- revision ------------------------------------------------------------------------------------------------

    /**
     * The revision of a staged directory: the digest over the bytes of every file the fingerprint would cover, so a
     * file that is not UTF-8 text (a binary payload) is digested, not refused.
     */
    private static PackRevision revisionOf(Path packDir, PackIdentifier pack) throws IOException {
        List<byte[]> contents = new ArrayList<>();
        // Named after the pack, not the directory: the live directory may be a link's target, the staging copy a temp.
        for (String relative : PackFileSet.digested(packDir, pack.value())) {
            contents.add(Files.readAllBytes(packDir.resolve(relative)));
        }
        return PackRevision.computeOverBytes(contents);
    }

    // ---- id to directory -----------------------------------------------------------------------------------------

    /** A pack's live directory, its store and the directory-name key, at the location {@link PackLocator} resolves. */
    private record Resolved(String name, FilesystemPackStore store, PackIdentifier key, Path directory) {}

    /** The pack's store and directory, at the location {@link PackLocator} resolves. */
    private Resolved resolve(PackIdentifier pack) {
        return over(PackLocator.locate(locations, strategy, pack));
    }

    private Resolved over(PackLocation location) {
        Path directory = PackLocator.directoryOf(location);
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack location has no parent directory");
        }
        return new Resolved(
                location.name(),
                new FilesystemPackStore(parent, strategy),
                new PackIdentifier(directory.getFileName().toString()),
                directory);
    }

    // ---- filesystem helpers --------------------------------------------------------------------------------------

    private static Path requireParent(Path directory) {
        Path parent = directory.getParent();
        if (parent == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack directory has no parent");
        }
        return parent;
    }

    /** Copies the whole tree of {@code source} into the existing directory {@code target}, following file links. */
    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(
                        file,
                        target.resolve(source.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void moveDirectory(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(from, to);
        }
    }

    static void deleteTree(@Nullable Path root) {
        if (root == null) {
            return;
        }
        try {
            if (!Files.exists(root)) {
                return;
            }
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException failure) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // A failure to clean the scratch directory never masks the real outcome of the commit.
        }
    }

    /**
     * Hides an aside copy as {@code .pack-discard-*}, so a delete that fails half-way leaves inert hidden residue,
     * never a {@code .pack-old-*} copy the listing's repair would bring back. Where it cannot be renamed it is left as
     * it is, whole, rather than half-deleted under a name the repair trusts; the next listing or commit hides it.
     *
     * @return the hidden copy, or null when it could not be hidden
     */
    private static @Nullable Path hidden(Path aside) {
        Path discard = aside.resolveSibling(".pack-discard-" + unique());
        try {
            moveDirectory(aside, discard);
            return discard;
        } catch (IOException notHidden) {
            return null;
        }
    }

    private static String unique() {
        return Long.toUnsignedString(System.nanoTime(), 36) + "-"
                + Long.toUnsignedString(COUNTER.incrementAndGet(), 36);
    }

    private static final java.util.concurrent.atomic.AtomicLong COUNTER = new java.util.concurrent.atomic.AtomicLong();
}
