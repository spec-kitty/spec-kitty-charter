package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Readers during a replace's swap window (#490, FR-013, FR-014, NFR-001): the real swap ({@link PackCommitStore#moveAside})
 * is paused at a phase, and every lock-free read of the pack answers the last committed state, or the new one once the
 * staged copy is in place, while the swap is still paused and without queueing on the pack's write lock. Read through the production
 * {@link StorageRootLocations}.
 */
class SwapWindowReadTest {

    /** Only a hang bound: a read in the window takes no lock, so it ends while the swap is paused; how fast is never asserted. */
    private static final Duration HANG_CAP = Duration.ofSeconds(30);

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private static final String SHARD =
            "schema_version: '1.0'\ngenerated_at: '2026-01-01T00:00:00Z'\nnodes: []\nedges: []\n";

    @TempDir
    Path host;

    private Path packs;
    private Path live;
    private StorageRootLocations locations;
    private DomainRepositoryService repository;
    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void aPackDomain() throws IOException {
        packs = Files.createDirectories(host.resolve("config/packs"));
    }

    @AfterEach
    void releaseTheSwap() {
        release.countDown();
    }

    private void plainPack() throws IOException {
        live = PackFixtures.copyOf("pack-a", packs);
        locations = new StorageRootLocations(packs);
        repository = new DomainRepositoryService(locations, strategy);
    }

    /** What the reads answer for one state of the pack. */
    private record Answers(
            PackRevision revision,
            String digest,
            List<String> toolguides,
            List<PackLocation> listed,
            String descriptorById,
            String fingerprint) {}

    private Answers answers() {
        CommitStorePackWriteStore reader =
                new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
        String packId = repository.get(PACK).packId();
        return new Answers(
                reader.currentRevision(PACK),
                reader.digestOf(PACK),
                repository.load(PACK).toolguides().stream()
                        .map(guide -> guide.id().value())
                        .sorted()
                        .toList(),
                locations.all(),
                repository.get(new PackIdentifier(packId)).name(),
                new FileSystemPackSources(locations).fingerprint());
    }

    /** A commit of the toolguide {@code fd}, run in its own thread through the real swap paused by {@code phases}. */
    private CompletableFuture<PackRevision> commitPausedBy(PackCommitStore.SwapPhases phases) {
        PackCommitStore commits = new PackCommitStore(locations, strategy, PackCommitStore.moveAside(phases));
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations, strategy, commits);
        CompletableFuture<PackRevision> done = new CompletableFuture<>();
        Thread.ofPlatform().start(() -> {
            try {
                done.complete(writeStore.exclusively(PACK, () -> commits.commit(PACK, null, toolguide("fd"), null)));
            } catch (RuntimeException failed) {
                done.completeExceptionally(failed);
            }
        });
        return done;
    }

    private PackCommitStore.SwapPhases pauseAfterAside() {
        return new PackCommitStore.SwapPhases() {
            @Override
            public void afterAside(Path liveDir, Path aside) {
                pause();
            }
        };
    }

    private void pause() {
        paused.countDown();
        await(release);
    }

    @Test
    void t1AReadBetweenTheRenamesAnswersTheLastCommittedState() throws Exception {
        plainPack();
        Answers before = answers();

        CompletableFuture<PackRevision> commit = commitPausedBy(pauseAfterAside());
        await(paused);
        assertThat(live).as("the swap is between its renames").doesNotExist();

        Answers inTheWindow = whilePausedAndUnqueued(this::answers);

        assertThat(inTheWindow)
                .as("every read answers the last committed state")
                .isEqualTo(before);
        release.countDown();
        PackRevision committed = commit.get(HANG_CAP.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(answers().revision())
                .as("after the swap, the commit's revision")
                .isEqualTo(committed);
        assertThat(answers().toolguides()).contains("fd");
    }

    @Test
    void t2AReadBetweenTheSecondRenameAndTheHidingAnswersTheNewState() throws Exception {
        plainPack();
        Answers before = answers();
        AtomicReference<Path> asideSeen = new AtomicReference<>();
        CompletableFuture<PackRevision> commit = commitPausedBy(new PackCommitStore.SwapPhases() {
            @Override
            public void afterLive(Path liveDir, Path aside) {
                asideSeen.set(aside);
                pause();
            }
        });
        await(paused);
        assertThat(asideSeen.get())
                .as("the aside copy sits beside the new live directory")
                .isDirectory();

        Answers inTheWindow = whilePausedAndUnqueued(this::answers);

        assertThat(inTheWindow.revision()).isNotEqualTo(before.revision());
        assertThat(inTheWindow.toolguides()).contains("fd");
        assertThat(inTheWindow.listed()).isEqualTo(before.listed());
        release.countDown();
        assertThat(commit.get(HANG_CAP.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(inTheWindow.revision());
        assertThat(asideSeen.get())
                .as("the swap hid and deleted its own aside copy")
                .doesNotExist();
        assertThat(residue(packs)).isEmpty();
    }

    @Test
    void t3AReadThatAWholeSwapRunsUnderIsNeverTorn() throws Exception {
        plainPack();
        PackLocation location = locations.all().getFirst();
        AtomicBoolean swapped = new AtomicBoolean();
        PackCommitStore commits = new PackCommitStore(locations, strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations, strategy, commits);
        writeStore.exclusively(PACK, () -> commits.commit(PACK, null, toolguide("fd", "Before the swap."), null));
        String r0 = FileSystemPackSources.digest(List.of(location), PackFileSet::digested);

        // The reader-side seam: the read lists the pack, then a whole swap runs (it rewrites a listed file and adds one
        // the listing lacks), then the read reads the listed files' bytes.
        String read = FileSystemPackSources.digest(List.of(location), directory -> {
            List<String> listed = PackFileSet.digested(directory);
            if (swapped.compareAndSet(false, true)) {
                writeStore.exclusively(
                        PACK, () -> commits.commit(PACK, null, toolguide("fd", "After the swap."), SHARD));
            }
            return listed;
        });

        String r1 = FileSystemPackSources.digest(List.of(location), PackFileSet::digested);
        assertThat(swapped).isTrue();
        assertThat(r1).isNotEqualTo(r0);
        assertThat(read).as("exactly the old or the new state, never a mix").isIn(r0, r1);
    }

    @Test
    void t4ASwapWhoseSecondRenameFailsLeavesReadsOnTheLastCommittedState() throws Exception {
        plainPack();
        Answers before = answers();
        CompletableFuture<PackRevision> commit = commitPausedBy(new PackCommitStore.SwapPhases() {
            @Override
            public void afterAside(Path liveDir, Path aside) {
                pause();
                removeStaging(liveDir.getParent()); // so the second rename fails
            }
        });
        await(paused);

        assertThat(whilePausedAndUnqueued(this::answers)).isEqualTo(before);
        release.countDown();
        assertThat(commit)
                .failsWithin(HANG_CAP)
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .havingCause()
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        assertThat(answers()).as("the aside copy was moved back").isEqualTo(before);
        assertThat(residue(packs)).isEmpty();
    }

    @Test
    void t5ALinkedPackBetweenTheRenamesIsReadThroughTheAsideCopyBesideItsTarget() throws Exception {
        Path vendor = Files.createDirectories(packs.resolve(".vendor"));
        Path target = Files.move(PackFixtures.copyOf("pack-a", vendor), vendor.resolve("real-pack-a"))
                .toRealPath();
        live = SymbolicLinks.create(packs.resolve("pack-a"), target);
        locations = new StorageRootLocations(packs);
        repository = new DomainRepositoryService(locations, strategy);
        Answers before = answers();

        CompletableFuture<PackRevision> commit = commitPausedBy(pauseAfterAside());
        await(paused);
        assertThat(target).as("the link's target is between its renames").doesNotExist();

        assertThat(whilePausedAndUnqueued(this::answers)).isEqualTo(before);
        release.countDown();
        PackRevision committed = commit.get(HANG_CAP.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(answers().revision()).isEqualTo(committed);
        assertThat(Files.isSymbolicLink(live)).isTrue();
    }

    @Test
    void t6ACreatesEmptyReservationReadsAsNoPack() throws Exception {
        plainPack();
        // The state between a create's reservation and its move: an empty directory under the write lock.
        Path reserved = packs.resolve("pack-new");
        ReentrantLock lock = PackWriteLocks.of(reserved);
        CompletableFuture<Void> create = CompletableFuture.runAsync(
                () -> {
                    lock.lock();
                    try {
                        Files.createDirectory(reserved);
                        pause();
                        Files.delete(reserved);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        lock.unlock();
                    }
                },
                runnable -> Thread.ofPlatform().start(runnable));
        await(paused);

        whilePausedAndUnqueued(() -> {
            assertThat(locations.all()).extracting(PackLocation::name).containsExactly("pack-a");
            assertThatThrownBy(() -> repository.get(new PackIdentifier("pack-new")))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
            return null;
        });
        release.countDown();
        create.get(HANG_CAP.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void t8AReadThatASwapRolledBackUnderIsReadAgainNeverAnsweredTorn() throws Exception {
        // Squad D-1: the reader resolves the live directory, the swap moves it aside, the read finds no pack.yaml, the
        // second rename fails and the aside copy is moved back. The directory is the same one again (same inode, same
        // birth time), so only the pack's count of moves tells the reader that it moved under it.
        plainPack();
        CountDownLatch resolved = new CountDownLatch(1);
        CountDownLatch asideDone = new CountDownLatch(1);
        CountDownLatch bodyRead = new CountDownLatch(1);
        CountDownLatch restored = new CountDownLatch(1);
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        PackWriteGeneration generation = PackWriteGeneration.shared();
        ConsistentRead verified = new ConsistentRead(new FileSystemPackSources(locations), generation);
        generation.begin(PACK.value()); // the bracket the write store holds around a commit: the read starts in it
        try {
            CompletableFuture<ConsistentRead.Verified<String>> read = new CompletableFuture<>();
            Thread.ofPlatform().start(() -> {
                try {
                    read.complete(verified.read(
                            "stale",
                            () -> PackDirectories.read(live, PACK.value(), locations, dir -> {
                                if (!firstAttempt.getAndSet(false)) {
                                    return heldOrAbsent(dir);
                                }
                                resolved.countDown(); // the live directory was resolved before the first rename
                                await(asideDone);
                                String answer = heldOrAbsent(dir); // between the renames: absent
                                bodyRead.countDown();
                                await(restored);
                                return answer;
                            })));
                } catch (RuntimeException failed) {
                    read.completeExceptionally(failed);
                }
            });
            await(resolved);
            CompletableFuture<PackRevision> commit = commitPausedBy(new PackCommitStore.SwapPhases() {
                @Override
                public void afterAside(Path liveDir, Path aside) {
                    asideDone.countDown();
                    await(bodyRead);
                    removeStaging(liveDir.getParent()); // so the second rename fails and the aside copy goes back
                }
            });
            assertThat(commit).failsWithin(HANG_CAP);
            assertThat(live).as("the aside copy was moved back").isDirectory();
            restored.countDown();

            assertThat(read.get(HANG_CAP.toMillis(), TimeUnit.MILLISECONDS).value())
                    .as("the read ran again over the restored pack; it never answers the torn state")
                    .isEqualTo("held");
        } finally {
            restored.countDown();
            generation.end(PACK.value());
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /**
     * Runs {@code read} in its own thread and answers it, asserting it ended while the swap is still paused (before
     * {@code release}) and never queued on the pack's write lock (NFR-001). Only {@link #HANG_CAP} bounds the wait, so
     * a slow runner never fails it: a read that waited for the swap is the failure, however long it took.
     */
    private <T> T whilePausedAndUnqueued(Supplier<T> read) throws InterruptedException {
        return unqueued(read, HANG_CAP);
    }

    /** Runs {@code read} in its own thread, asserting it never queued on the pack's write lock and ended within {@code bound}. */
    private <T> T unqueued(Supplier<T> read, Duration bound) throws InterruptedException {
        ReentrantLock lock = PackWriteLocks.of(packs.resolve("pack-a"));
        AtomicReference<T> answer = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicReference<AssertionError> failed = new AtomicReference<>();
        Thread reader = Thread.ofPlatform().start(() -> {
            try {
                answer.set(read.get());
            } catch (RuntimeException thrown) {
                failure.set(thrown);
            } catch (AssertionError thrown) {
                failed.set(thrown);
            }
        });
        boolean queued = false;
        long deadline = System.nanoTime() + bound.toNanos();
        while (reader.isAlive() && System.nanoTime() < deadline) {
            queued |= lock.hasQueuedThread(reader);
            reader.join(Duration.ofMillis(1));
        }
        boolean finished = !reader.isAlive();
        if (!finished) {
            release.countDown(); // let the swap go so the reader can end
            reader.join(HANG_CAP);
        }
        assertThat(queued).as("the read never queued on the pack's write lock").isFalse();
        assertThat(finished)
                .as("the read ended while the swap was paused (hang cap %s)", bound)
                .isTrue();
        if (failed.get() != null) {
            throw failed.get();
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return answer.get();
    }

    private static String heldOrAbsent(Path directory) {
        return Files.isRegularFile(directory.resolve(PackFileSet.PACK_YAML)) ? "held" : "absent";
    }

    private static void removeStaging(Path parent) {
        try (Stream<Path> siblings = Files.list(parent)) {
            for (Path staging : siblings.filter(
                            path -> String.valueOf(path.getFileName()).startsWith(".pack-commit-"))
                    .toList()) {
                PackCommitStore.deleteTree(staging);
            }
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

    private static Set<String> residue(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName()))
                    .filter(name -> name.startsWith(".pack-") || name.contains(".pack-old-"))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static Toolguide toolguide(String id) {
        return toolguide(id, "How to search.");
    }

    private static Toolguide toolguide(String id, String summary) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guide(Guide.of("How to use the tool."))
                .summary(summary)
                .build();
    }
}
