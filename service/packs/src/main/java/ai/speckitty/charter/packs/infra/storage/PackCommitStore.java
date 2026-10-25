package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PreconditionException;
import ai.speckitty.charter.packs.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
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
 *
 * <p><strong>Crash window.</strong> The swap is two renames, so a process that dies between them leaves no live
 * directory, only the {@code <name>.pack-old-*} aside copy (and the staging copy). That state is repaired on the next
 * write: {@link #restoreInterruptedSwap(Path)} moves a sole aside copy back into place before the pack is resolved,
 * which restores the last committed state; the interrupted commit was never acknowledged.
 *
 * <p>This store never performs the per-child {@code save()} upsert of {@link DomainRepositoryService}; registration
 * goes load &rarr; mutate &rarr; revalidate the whole aggregate in memory &rarr; commit the delta here.
 */
@Service
public final class PackCommitStore {

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
        deleteTree(aside);
    };

    private final DomainRepositoryService.Locations locations;
    private final SerializationStrategy strategy;
    private final DirectoryRename rename;

    /**
     * A commit store that swaps directories atomically.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     */
    @Autowired
    public PackCommitStore(DomainRepositoryService.Locations locations, SerializationStrategy strategy) {
        this(locations, strategy, MOVE_ASIDE);
    }

    /**
     * A commit store with an injected swap step; the seam for the NFR-002 atomicity test.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param rename the directory-swap step
     */
    PackCommitStore(
            DomainRepositoryService.Locations locations, SerializationStrategy strategy, DirectoryRename rename) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.rename = Objects.requireNonNull(rename, "rename");
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
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the pack is not configured, is not a directory, or the
     *     commit fails; in every failure case the live pack directory is unchanged
     */
    public PackRevision commit(
            PackIdentifier pack,
            @Nullable String ifMatch,
            @Nullable CharterArtifact artifact,
            @Nullable String relationshipShardText) {
        Objects.requireNonNull(pack, "pack");
        Resolved resolved = resolve(pack);
        Path liveDir = resolved.directory;
        if (!Files.isDirectory(liveDir)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " is not a directory");
        }
        Path parent = requireParent(liveDir);
        @Nullable Path staging = null;
        try {
            if (ifMatch != null && !PackRevision.matches(ifMatch, revisionOf(liveDir))) {
                throw new PreconditionException(
                        PreconditionException.Kind.STALE, "The If-Match precondition is stale; read the pack again.");
            }
            staging = Files.createTempDirectory(parent, ".pack-commit-");
            copyTree(liveDir, staging);
            if (artifact != null) {
                PackIdentifier stagingKey = new PackIdentifier(String.valueOf(staging.getFileName()));
                DomainRepositoryService.writeArtifact(new FilesystemPackStore(parent, strategy), stagingKey, artifact);
            }
            if (relationshipShardText != null) {
                Files.writeString(
                        staging.resolve(RelationshipShard.SHARD_PATH), relationshipShardText, StandardCharsets.UTF_8);
            }
            PackRevision revision = revisionOf(staging);
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

    /**
     * Repairs a swap a crash interrupted between its two renames: when {@code live} is missing and exactly one
     * {@code <name>.pack-old-*} directory sits beside it, that aside copy (the last committed state) is moved back into
     * place. With zero or several candidates nothing is guessed. The caller must hold the pack's write lock, since a
     * commit in flight is in that same state for an instant.
     *
     * @param live the pack's live directory
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the restore fails
     */
    static void restoreInterruptedSwap(Path live) {
        Path parent = live.getParent();
        if (parent == null || Files.exists(live, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(parent)) {
            return;
        }
        String prefix = live.getFileName() + ".pack-old-";
        List<Path> asides;
        try (Stream<Path> siblings = Files.list(parent)) {
            asides = siblings.filter(
                            sibling -> String.valueOf(sibling.getFileName()).startsWith(prefix))
                    .filter(sibling -> Files.isDirectory(sibling, LinkOption.NOFOLLOW_LINKS))
                    .toList();
        } catch (IOException failure) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot inspect the parent of " + live, failure);
        }
        if (asides.size() != 1) {
            return;
        }
        try {
            moveDirectory(asides.getFirst(), live);
        } catch (IOException failure) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot restore " + live + " after an interrupted commit", failure);
        }
    }

    // ---- revision ------------------------------------------------------------------------------------------------

    /** The revision of a staged directory: the digest over the texts of every file the fingerprint would cover. */
    private static PackRevision revisionOf(Path packDir) throws IOException {
        List<String> contents = new ArrayList<>();
        for (String relative : PackFileSet.digested(packDir)) {
            contents.add(Files.readString(packDir.resolve(relative), StandardCharsets.UTF_8));
        }
        return PackRevision.compute(contents);
    }

    // ---- id to directory -----------------------------------------------------------------------------------------

    /** A pack's live directory, its store and the directory-name key, resolved as {@link DomainRepositoryService} does. */
    private record Resolved(FilesystemPackStore store, PackIdentifier key, Path directory) {}

    private Resolved resolve(PackIdentifier pack) {
        List<PackLocation> configured = locations.all();
        for (PackLocation location : configured) {
            if (location.name().equals(pack.value())) {
                return over(location);
            }
        }
        for (PackLocation location : configured) {
            Resolved candidate = over(location);
            if (declaresId(candidate, pack)) {
                return candidate;
            }
        }
        throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " is not a configured pack");
    }

    private Resolved over(PackLocation location) {
        Path directory = location.root().toAbsolutePath().normalize();
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack location has no parent directory");
        }
        return new Resolved(
                new FilesystemPackStore(parent, strategy),
                new PackIdentifier(directory.getFileName().toString()),
                directory);
    }

    private static boolean declaresId(Resolved candidate, PackIdentifier pack) {
        try {
            return candidate.store.readDescriptor(candidate.key).packId().equals(pack.value());
        } catch (CharterRefusal unreadable) {
            return false;
        }
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

    private static void moveDirectory(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(from, to);
        }
    }

    private static void deleteTree(@Nullable Path root) {
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

    private static String unique() {
        return Long.toUnsignedString(System.nanoTime(), 36) + "-"
                + Long.toUnsignedString(COUNTER.incrementAndGet(), 36);
    }

    private static final java.util.concurrent.atomic.AtomicLong COUNTER = new java.util.concurrent.atomic.AtomicLong();
}
