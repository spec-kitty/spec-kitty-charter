package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A commit that dies between its swap's two renames (#281): the live directory has been moved aside to
 * {@code <name>.pack-old-*} and the staged copy is not yet in place. The next listing of the pack domain restores the
 * last committed state under the pack's write lock, so the next write, and the next read, find the pack again. For a
 * pack linked from inside the configuration root the restore happens at the link's target, and the link stays as it
 * was (ADR-0038 as amended 2026-10-31). Read and written through the production {@link StorageRootLocations}.
 */
class InterruptedCommitRepairTest {

    /**
     * A hang cap far above the work's time, so load cannot trip it; a guard that stops cutting the work off fails at
     * it (#320).
     */
    private static final Duration HANG_CAP = Duration.ofSeconds(30);

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String SHARD =
            "schema_version: '1.0'\ngenerated_at: '2026-01-01T00:00:00Z'\nnodes: []\nedges: []\n";
    private static final String CRASHED = ".pack-old-crashed";

    /** The first rename of the swap, then the process dies: the state a crash between the two renames leaves. */
    private static final PackCommitStore.DirectoryRename CRASH_AFTER_ASIDE = (staging, live) -> {
        PackCommitStore.moveDirectory(live, live.resolveSibling(live.getFileName() + CRASHED));
        throw new IOException("the process died between the swap's two renames");
    };

    @TempDir
    Path host;

    private Path packs;
    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @BeforeEach
    void aPackDomain() throws IOException {
        packs = Files.createDirectories(host.resolve("config/packs"));
    }

    @Test
    void aPlainPackCrashedBetweenTheRenamesIsRestoredOnTheNextWrite() throws IOException {
        Path live = PackFixtures.copyOf("pack-a", packs);
        StorageRootLocations locations = new StorageRootLocations(packs);
        Map<String, String> committed = snapshot(live);
        String lastCommitted = revisionOf(locations).token();
        crash(locations);
        assertThat(live).as("the crash left the pack missing").doesNotExist();

        write(locations, lastCommitted);

        assertThat(snapshot(live))
                .as("the last committed state, plus the write; the interrupted commit is gone")
                .isEqualTo(withWrite(committed));
        assertThat(residue(packs)).as("the aside copy was moved back").isEmpty();
    }

    @Test
    void aLinkedPackCrashedBetweenTheRenamesIsRestoredAtItsTargetAndKeepsItsLink() throws IOException {
        Path vendor = Files.createDirectories(packs.resolve(".vendor"));
        Path target = Files.move(PackFixtures.copyOf("pack-a", vendor), vendor.resolve("real-pack-a"))
                .toRealPath();
        Path link = SymbolicLinks.create(packs.resolve("pack-a"), target);
        StorageRootLocations locations = new StorageRootLocations(packs);
        Map<String, String> committed = snapshot(target);
        String lastCommitted = revisionOf(locations).token();
        crash(locations);
        assertThat(target).as("the crash left the target missing").doesNotExist();
        assertThat(vendor.resolve("real-pack-a" + CRASHED))
                .as("the aside copy sits beside the target, named after it")
                .isDirectory();

        write(locations, lastCommitted);

        assertThat(Files.isSymbolicLink(link)).as("the link is still a link").isTrue();
        assertThat(Files.readSymbolicLink(link)).as("to the same target").isEqualTo(target);
        assertThat(snapshot(target)).isEqualTo(withWrite(committed));
        assertThat(residue(vendor)).as("the aside copy was moved back").isEmpty();
        assertThat(residue(packs)).isEmpty();
    }

    @Test
    void aReadAfterACrashServesTheLastCommittedState() throws IOException {
        Path live = PackFixtures.copyOf("pack-a", packs);
        StorageRootLocations locations = new StorageRootLocations(packs);
        Map<String, String> committed = snapshot(live);
        crash(locations);

        Pack read = new DomainRepositoryService(locations, strategy).load(PACK);

        assertThat(read.tactics()).as("the pack is served again").isNotEmpty();
        assertThat(read.toolguides())
                .extracting(guide -> guide.id().value())
                .as("without the interrupted commit's artefact")
                .doesNotContain("lost");
        assertThat(snapshot(live)).isEqualTo(committed);
        assertThat(residue(packs)).isEmpty();
    }

    @Test
    void aPackDomainLinkedInsideTheConfigRootIsRepairedAndKeepsItsLink() throws IOException {
        Path config = Files.createDirectories(host.resolve("linked-domain-config"));
        Path shared = Files.createDirectories(config.resolve("shared"));
        Path live = PackFixtures.copyOf("pack-a", shared).toRealPath();
        Path domain = SymbolicLinks.create(config.resolve("packs"), shared);
        StorageRootLocations locations = new StorageRootLocations(domain);
        Map<String, String> committed = snapshot(live);
        String lastCommitted = revisionOf(locations).token();
        crash(locations);

        write(locations, lastCommitted);

        assertThat(Files.isSymbolicLink(domain))
                .as("the domain is still a link")
                .isTrue();
        assertThat(Files.readSymbolicLink(domain)).as("to the same directory").isEqualTo(shared);
        assertThat(snapshot(live)).isEqualTo(withWrite(committed));
        assertThat(residue(shared)).isEmpty();
    }

    @Test
    void aReadThatMeetsASwapInFlightServesTheLastCommittedStateWithoutWaiting() throws Exception {
        Path live = PackFixtures.copyOf("pack-a", packs);
        StorageRootLocations locations = new StorageRootLocations(packs);
        DomainRepositoryService reader = new DomainRepositoryService(locations, strategy);
        CompletableFuture<Pack> read = new CompletableFuture<>();
        AtomicBoolean lockHeldWhenRead = new AtomicBoolean();
        AtomicBoolean queuedOnTheLock = new AtomicBoolean();
        // Between the two renames, a reader in another thread reads the pack, and is joined before the second rename:
        // it answers inside the window, with the write lock held, so it cannot have waited for the swap (#490).
        PackCommitStore.SwapPhases readInTheWindow = new PackCommitStore.SwapPhases() {
            @Override
            public void afterAside(Path liveDir, Path aside) {
                Thread thread = Thread.ofPlatform().start(() -> complete(read, reader));
                ReentrantLock lock = PackWriteLocks.of(liveDir);
                try {
                    while (!thread.join(Duration.ofMillis(1))) {
                        queuedOnTheLock.compareAndSet(false, lock.hasQueuedThread(thread));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                lockHeldWhenRead.set(lock.isLocked());
            }
        };
        PackCommitStore commits = new PackCommitStore(locations, strategy, PackCommitStore.moveAside(readInTheWindow));
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations, strategy, commits);

        assertTimeoutPreemptively(
                HANG_CAP, () -> writeStore.exclusively(PACK, () -> commits.commit(PACK, null, toolguide("fd"), null)));

        assertThat(lockHeldWhenRead)
                .as("the read answered while the swap held the write lock")
                .isTrue();
        assertThat(queuedOnTheLock)
                .as("the read never queued on the write lock")
                .isFalse();
        assertThat(read)
                .as("the read served the last committed state, through the aside copy")
                .isCompleted()
                .satisfies(future -> assertThat(future.join().toolguides())
                        .extracting(guide -> guide.id().value())
                        .doesNotContain("fd"));
        assertThat(reader.load(PACK).toolguides())
                .extracting(guide -> guide.id().value())
                .as("after the commit a read serves the new state")
                .contains("fd");
        assertThat(live.resolve("fd.toolguide.yaml")).isRegularFile();
        assertThat(residue(packs)).isEmpty();
        assertThat(hidden(packs)).isEmpty();
    }

    @Test
    void aListingInsideAnotherPacksWriteLockListsAHeldPackThroughItsAsideRatherThanWaiting() throws Exception {
        PackFixtures.copyOf("pack-descriptor-only", packs);
        Path live = PackFixtures.copyOf("pack-a", packs);
        StorageRootLocations locations = new StorageRootLocations(packs);
        // The registry lock serializes writers, so two writers can no longer overlap on one store. Each side gets a
        // registry lock of its own here, to keep pinning the property under test: a listing never waits on a pack lock.
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(
                locations,
                strategy,
                new PackCommitStore(locations, strategy),
                new RegistryWriteLock(Duration.ofSeconds(5)),
                new PackWriteGeneration());
        CommitStorePackWriteStore listingStore = new CommitStorePackWriteStore(
                locations,
                strategy,
                new PackCommitStore(locations, strategy),
                new RegistryWriteLock(Duration.ofSeconds(5)),
                new PackWriteGeneration());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch halfSwapped = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean waitedOn = new AtomicBoolean();
        // Another thread holds pack-a's lock with pack-a moved aside, as a swap in flight does, until released; it
        // notes whether any thread waits on that lock meanwhile.
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> {
            await(start);
            writeStore.exclusively(PACK, () -> {
                move(live, live.resolveSibling("pack-a.pack-old-held"));
                halfSwapped.countDown();
                noteWaitersUntil(release, PackWriteLocks.of(live), waitedOn);
                move(live.resolveSibling("pack-a.pack-old-held"), live);
                return null;
            });
        });

        // A listing that already holds a pack's lock only tries the half-swapped pack's: two packs each waiting on the
        // other's lock would deadlock.
        List<String> listed;
        try {
            listed = assertTimeoutPreemptively(
                    HANG_CAP,
                    () -> listingStore.exclusively(new PackIdentifier("pack-descriptor-only"), () -> {
                        start.countDown();
                        await(halfSwapped);
                        return locations.all().stream().map(PackLocation::name).toList();
                    }));
        } finally {
            start.countDown();
            release.countDown();
        }
        holder.get(HANG_CAP.toMillis(), TimeUnit.MILLISECONDS);

        assertThat(waitedOn)
                .as("the listing only tried the held lock; it never waited on it")
                .isFalse();
        assertThat(listed)
                .as("the held, half-swapped pack is listed through its aside copy (#490)")
                .containsExactlyInAnyOrder("pack-a", "pack-descriptor-only");
        assertThat(locations.all()).extracting(PackLocation::name).contains("pack-a");
    }

    @Test
    void twoAsideCopiesAreNotGuessedBetween() throws IOException {
        Path live = PackFixtures.copyOf("pack-a", packs);
        StorageRootLocations locations = new StorageRootLocations(packs);
        crash(locations);
        Path other = Files.move(
                PackFixtures.copyOf("pack-a", Files.createDirectories(host.resolve("elsewhere"))),
                packs.resolve("pack-a.pack-old-other"));
        Map<String, String> crashedCopy = snapshot(packs.resolve("pack-a" + CRASHED));
        Map<String, String> otherCopy = snapshot(other);

        assertThatThrownBy(() -> write(locations, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));

        assertThat(live).doesNotExist();
        assertThat(snapshot(packs.resolve("pack-a" + CRASHED))).isEqualTo(crashedCopy);
        assertThat(snapshot(other)).isEqualTo(otherCopy);
    }

    @Test
    void anAsideCopyBesideALivePackIsHiddenSoItIsNeverRestoredOverAPackRemovedOnPurpose() throws IOException {
        Path live = PackFixtures.copyOf("pack-a", packs);
        // The residue of a swap that finished: a crash after the second rename, before the aside copy was deleted.
        Path stale = Files.move(
                PackFixtures.copyOf("pack-a", Files.createDirectories(host.resolve("elsewhere"))),
                packs.resolve("pack-a.pack-old-stale"));
        Files.writeString(stale.resolve("stale.txt"), "an older state");
        StorageRootLocations locations = new StorageRootLocations(packs);

        assertThat(locations.all()).extracting(PackLocation::name).containsExactly("pack-a");
        assertThat(live.resolve("stale.txt"))
                .as("the live pack was not replaced")
                .doesNotExist();
        assertThat(stale).as("the stale copy is no longer a restore candidate").doesNotExist();
        assertThat(hidden(packs)).as("it is hidden, not deleted").hasSize(1);

        PackCommitStore.deleteTree(live); // the operator removes the pack on purpose
        assertThat(locations.all()).as("the removed pack stays removed").isEmpty();
    }

    @Test
    void aCommitHidesAStaleAsideCopyBesideALinkedPacksTarget() throws IOException {
        Path vendor = Files.createDirectories(packs.resolve(".vendor"));
        Path target = Files.move(PackFixtures.copyOf("pack-a", vendor), vendor.resolve("real-pack-a"))
                .toRealPath();
        SymbolicLinks.create(packs.resolve("pack-a"), target);
        Files.move(
                PackFixtures.copyOf("pack-a", Files.createDirectories(host.resolve("elsewhere"))),
                vendor.resolve("real-pack-a.pack-old-stale"));
        StorageRootLocations locations = new StorageRootLocations(packs);

        write(locations, null);

        assertThat(PackCommitStore.asidesOf(target))
                .as("no restore candidate is left beside the target")
                .isEmpty();
        assertThat(hidden(vendor)).as("it is hidden, not deleted").hasSize(1);
        PackCommitStore.deleteTree(target); // the operator removes the pack's content on purpose
        assertThat(locations.all()).as("nothing is restored over it").isEmpty();
        assertThat(target).doesNotExist();
    }

    @Test
    void aDanglingLinkIntoAnotherPackIsNotRestoredThere() throws IOException {
        Path packA = PackFixtures.copyOf("pack-a", packs);
        // An aside copy inside pack-a, and a dangling link whose target it would restore: the move would change
        // pack-a's files under another pack's lock.
        Path inside = Files.move(
                PackFixtures.copyOf("pack-a", Files.createDirectories(host.resolve("elsewhere"))),
                packA.resolve("sub.pack-old-x"));
        SymbolicLinks.create(packs.resolve("evil"), packA.resolve("sub"));
        Map<String, String> packABefore = snapshot(packA);
        StorageRootLocations locations = new StorageRootLocations(packs);

        assertThat(locations.all()).extracting(PackLocation::name).containsExactly("pack-a");

        assertThat(packA.resolve("sub")).doesNotExist();
        assertThat(inside).isDirectory();
        assertThat(snapshot(packA)).as("pack-a is unchanged").isEqualTo(packABefore);
    }

    @Test
    void aRelativeLinkTargetIsFollowedToItsRealPath() throws IOException {
        Path vendor = Files.createDirectories(packs.resolve(".vendor"));
        Path target = Files.move(PackFixtures.copyOf("pack-a", vendor), vendor.resolve("real-pack-a"))
                .toRealPath();
        Path link = SymbolicLinks.create(packs.resolve("pack-a"), Path.of(".vendor/real-pack-a"));
        assertRepairedThroughTheChain(List.of(link), target, vendor);
    }

    @Test
    void aChainOfTwoLinksIsFollowedToItsFinalTarget() throws IOException {
        Path vendor = Files.createDirectories(packs.resolve(".vendor"));
        Path target = Files.move(PackFixtures.copyOf("pack-a", vendor), vendor.resolve("real-pack-a"))
                .toRealPath();
        Path hop = SymbolicLinks.create(vendor.resolve("hop"), Path.of("real-pack-a"));
        Path link = SymbolicLinks.create(packs.resolve("pack-a"), Path.of(".vendor/hop"));
        assertRepairedThroughTheChain(List.of(link, hop), target, vendor);
    }

    @Test
    void aSuccessfulCommitLeavesNoAsideOrDiscardedCopy() throws IOException {
        PackFixtures.copyOf("pack-a", packs);
        write(new StorageRootLocations(packs), null);
        assertThat(residue(packs)).isEmpty();
        assertThat(hidden(packs)).isEmpty();
    }

    @Test
    void anAsideCopyBesideAPackLinkedFromOutsideTheConfigRootIsLeftUntouched() throws IOException {
        Path checkout = Files.createDirectories(host.resolve("checkout"));
        Path target = Files.move(PackFixtures.copyOf("pack-a", checkout), checkout.resolve("real-pack-a"))
                .toRealPath();
        Path link = SymbolicLinks.create(packs.resolve("pack-a"), target);
        // The service never commits outside the configuration root, so an aside copy there is not its own.
        Path aside = Files.move(target, checkout.resolve("real-pack-a" + CRASHED));
        Map<String, String> asideCopy = snapshot(aside);
        StorageRootLocations locations = new StorageRootLocations(packs);

        assertThat(locations.all()).as("the dangling link is not served").isEmpty();
        assertThatThrownBy(() -> write(locations, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));

        assertThat(target)
                .as("nothing was restored outside the configuration root")
                .doesNotExist();
        assertThat(snapshot(aside)).isEqualTo(asideCopy);
        assertThat(Files.isSymbolicLink(link)).isTrue();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static List<String> hidden(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName()))
                    .filter(name -> name.startsWith(".pack-discard-"))
                    .toList();
        }
    }

    /** Crashes a commit through the links, writes, and asserts the restore left every link as it was. */
    private void assertRepairedThroughTheChain(List<Path> links, Path target, Path vendor) throws IOException {
        Map<Path, Path> linked = new TreeMap<>();
        for (Path link : links) {
            linked.put(link, Files.readSymbolicLink(link));
        }
        StorageRootLocations locations = new StorageRootLocations(packs);
        Map<String, String> committed = snapshot(target);
        String lastCommitted = revisionOf(locations).token();
        crash(locations);
        assertThat(target).as("the crash left the final target missing").doesNotExist();

        write(locations, lastCommitted);

        for (Map.Entry<Path, Path> link : linked.entrySet()) {
            assertThat(Files.isSymbolicLink(link.getKey())).isTrue();
            assertThat(Files.readSymbolicLink(link.getKey())).isEqualTo(link.getValue());
        }
        assertThat(snapshot(target)).isEqualTo(withWrite(committed));
        assertThat(residue(vendor)).isEmpty();
    }

    /** A commit of {@code lost} that dies between the swap's two renames. */
    private void crash(StorageRootLocations locations) {
        assertThatThrownBy(() -> new PackCommitStore(locations, strategy, CRASH_AFTER_ASIDE)
                        .commit(PACK, null, toolguide("lost"), SHARD))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    /** A write of {@code fd} through the write store's lock, as a registration does. */
    private PackRevision write(StorageRootLocations locations, String ifMatch) {
        PackCommitStore commits = new PackCommitStore(locations, strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations, strategy, commits);
        return writeStore.exclusively(PACK, () -> commits.commit(PACK, ifMatch, toolguide("fd"), null));
    }

    private PackRevision revisionOf(StorageRootLocations locations) {
        return new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy))
                .currentRevision(PACK);
    }

    private static Map<String, String> withWrite(Map<String, String> committed) {
        Map<String, String> expected = new TreeMap<>(committed);
        expected.put("fd.toolguide.yaml", "");
        expected.put("fd.guide.md", "");
        expected.put(PackFileSet.MANIFEST, ""); // #110: every commit restamps the manifest, generating it here
        return expected;
    }

    private static void complete(CompletableFuture<Pack> read, DomainRepositoryService reader) {
        try {
            read.complete(reader.load(PACK));
        } catch (RuntimeException failure) {
            read.completeExceptionally(failure);
        }
    }

    /** Waits for {@code release}, noting every ten milliseconds whether a thread is waiting on {@code lock}. */
    private static void noteWaitersUntil(CountDownLatch release, ReentrantLock lock, AtomicBoolean waitedOn) {
        try {
            while (!release.await(10, TimeUnit.MILLISECONDS)) {
                if (lock.hasQueuedThreads()) {
                    waitedOn.set(true);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void move(Path from, Path to) {
        try {
            PackCommitStore.moveDirectory(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(HANG_CAP.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Toolguide toolguide(String id) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guide(Guide.of("How to use the tool."))
                .summary("How to search.")
                .build();
    }

    private static List<String> residue(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName()))
                    .filter(name -> name.startsWith(".pack-commit-")
                            || name.startsWith(".pack-discard-")
                            || name.contains(".pack-old-"))
                    .toList();
        }
    }

    /** Every regular file under {@code directory}, relative path to content; written files' content is blanked. */
    private static Map<String, String> snapshot(Path directory) {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = directory.relativize(file).toString();
                // The written toolguide and the restamped manifest (#110) are compared by presence: their bytes follow
                // the write, and the manifest's freshness is ManifestMembershipTest's.
                out.put(
                        relative,
                        relative.startsWith("fd.") || relative.equals(PackFileSet.MANIFEST)
                                ? ""
                                : Files.readString(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
