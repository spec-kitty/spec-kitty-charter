package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.SlowSwapCommitStores;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mixed-read window of whole-pack writes (research R-lock, NFR-006, FR-007): 8 writers cycling a pack through the
 * versions A, B, C (A again included) against 8 readers, each case 100 runs started from a barrier. Every closure read
 * answers one complete version, or {@code stale_input}; never a mixed one and never {@code pack_unreadable}.
 *
 * <p>The oracle cannot pass for a reader that always answers {@code stale_input}: the reads taken while the generation
 * was odd are counted and must be more than none (the readers did overlap a swap), at most one read in ten may end
 * stale after the retries, and the retries of a snapshot started inside a swap end in a version in every run.
 */
@Tag("stress")
class MixedReadConcurrencyTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final int RUNS = 100;
    private static final int WRITERS = 8;
    private static final int READERS = 8;
    private static final int READS_PER_READER = 6;
    private static final List<String> VERSIONS = List.of("A", "B", "C");

    /** What the slow rename does inside the window where the live directory is missing; tests set it. */
    private volatile Runnable midSwap = () -> {};

    private final PackWriteGeneration generation = new PackWriteGeneration();
    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    private final AtomicInteger snapshots = new AtomicInteger();
    private final AtomicInteger stale = new AtomicInteger();
    private final AtomicInteger overlapped = new AtomicInteger();
    private final AtomicLong versionsSeen = new AtomicLong();

    private Rig rig(Path host) throws IOException {
        return new Rig(host);
    }

    @Test
    @Timeout(30)
    void everyReadSeesOneCompleteVersionOrStaleInputWhileEightWritersCycleThePack(@TempDir Path host) throws Exception {
        Rig rig = rig(host);
        Set<String> complete = rig.prime();
        midSwap = () -> pause(1);

        for (int run = 0; run < RUNS; run++) {
            rig.runConcurrently(run, complete);
        }

        assertThat(failures).as("a mixed or unreadable answer").isEmpty();
        assertThat(overlapped.get())
                .as("reads were taken while the generation was odd, so the readers did overlap a swap")
                .isGreaterThan(0);
        assertThat(snapshots.get()).isEqualTo(RUNS * READERS * READS_PER_READER);
        assertThat(versionsSeen.get()).as("the readers did get answers").isGreaterThan(0);
        assertThat(stale.get())
                .as("stale_input after the retries, of %d snapshots", snapshots.get())
                .isLessThanOrEqualTo(snapshots.get() / 10);
    }

    @Test
    @Timeout(30)
    void sixteenPutsAndOneReplaceAllCompleteUnderTheFairLock(@TempDir Path host) throws Exception {
        Rig rig = rig(host);
        rig.prime();

        for (int run = 0; run < RUNS; run++) {
            rig.sixteenPutsAndAReplace(run);
        }

        assertThat(failures).as("a write that starved or failed").isEmpty();
    }

    @Test
    @Timeout(30)
    void aSnapshotStartedInsideASwapRetriesIntoAVersionOnceTheSwapEnds(@TempDir Path host) throws Exception {
        Rig rig = rig(host);
        Set<String> complete = rig.prime();

        for (int run = 0; run < RUNS; run++) {
            rig.snapshotInsideASwap(run, complete);
        }

        assertThat(failures).as("a retry that did not end in a version").isEmpty();
        assertThat(overlapped.get())
                .as("every run started its read inside a swap")
                .isEqualTo(RUNS);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- the rig -------------------------------------------------------------------------------------------------

    private final class Rig {
        private final FileSystemPackSources sources;
        private final CommitStorePackWriteStore writeStore;
        private final DirectiveRegistrationService directives;
        private final ClosureQueries queries;

        Rig(Path host) throws IOException {
            Path packDir = host.resolve(PACK.value());
            Files.createDirectories(packDir);
            Files.writeString(
                    packDir.resolve("pack.yaml"),
                    "pack_id: 01M5DEH8ASSETREG1STRAT10N0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n",
                    StandardCharsets.UTF_8);
            StorageRootLocations locations = new StorageRootLocations(host);
            YamlSerializationStrategy strategy = new YamlSerializationStrategy();
            DomainRepositoryService repository = new DomainRepositoryService(locations, strategy);
            PackCommitStore commits = SlowSwapCommitStores.pausingMidSwap(locations, strategy, live -> midSwap.run());
            this.writeStore = new CommitStorePackWriteStore(
                    locations, strategy, commits, new RegistryWriteLock(Duration.ofSeconds(5)), generation);
            this.directives = new DirectiveRegistrationService(repository, writeStore, new PackMutabilityPolicy());
            this.sources = new FileSystemPackSources(locations::all);
            ConsistentRead read = new ConsistentRead(sources, generation);
            this.queries = new ClosureQueries(
                    new OfferingReadService(repository, repository, repository, read), repository, read);
        }

        /** Writes A, B and C in turn and answers the fingerprint of each complete version. */
        Set<String> prime() {
            Set<String> complete = new java.util.HashSet<>();
            for (String version : VERSIONS) {
                write("D", version);
                complete.add(sources.fingerprint());
            }
            return complete;
        }

        void write(String id, String version) {
            directives.registerDirective(PACK, directive(id, version), true, null);
        }

        void runConcurrently(int run, Set<String> complete) throws Exception {
            CyclicBarrier start = new CyclicBarrier(WRITERS + READERS);
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int writer = 0; writer < WRITERS; writer++) {
                int index = writer;
                tasks.add(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    pause(index * 8L);
                    write("D", VERSIONS.get((run + index) % VERSIONS.size()));
                    return null;
                });
            }
            for (int reader = 0; reader < READERS; reader++) {
                int index = reader;
                tasks.add(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    for (int read = 0; read < READS_PER_READER; read++) {
                        pause(index + 1L);
                        readOnce(complete);
                    }
                    return null;
                });
            }
            invokeAll(tasks);
        }

        void sixteenPutsAndAReplace(int run) throws Exception {
            CyclicBarrier start = new CyclicBarrier(17);
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int put = 0; put < 16; put++) {
                String id = "P" + put;
                tasks.add(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    write(id, VERSIONS.get(run % VERSIONS.size()));
                    return null;
                });
            }
            tasks.add(() -> {
                start.await(30, TimeUnit.SECONDS);
                write("D", VERSIONS.get((run + 1) % VERSIONS.size()));
                return null;
            });
            invokeAll(tasks);
        }

        void snapshotInsideASwap(int run, Set<String> complete) throws Exception {
            CountDownLatch inSwap = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            midSwap = () -> {
                inSwap.countDown();
                awaitQuietly(release);
            };
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> writer = pool.submit(() -> write("D", VERSIONS.get(run % VERSIONS.size())));
                if (!inSwap.await(10, TimeUnit.SECONDS)) {
                    failures.add("run " + run + ": the swap never started");
                    return;
                }
                Future<?> reader = pool.submit(() -> readOnce(complete));
                pause(15);
                release.countDown();
                reader.get(10, TimeUnit.SECONDS);
                writer.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                pool.shutdownNow();
                midSwap = () -> {};
            }
        }

        private void readOnce(Set<String> complete) {
            snapshots.incrementAndGet();
            if (PackWriteGeneration.isSwapping(generation.registry())) {
                overlapped.incrementAndGet();
            }
            try {
                ClosureView view = queries.snapshot(Optional.empty());
                versionsSeen.incrementAndGet();
                if (!complete.contains(view.fingerprint())) {
                    failures.add("a view of no complete version: " + view.fingerprint());
                }
            } catch (CharterRefusal refused) {
                if (refused.code() == RefusalCode.STALE_INPUT) {
                    stale.incrementAndGet();
                } else {
                    failures.add("a read refused " + refused.code() + ": " + refused.getMessage());
                }
            } catch (RuntimeException broken) {
                failures.add("a read broke: " + broken);
            }
        }

        private void invokeAll(List<Callable<Void>> tasks) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
            try {
                for (Future<Void> done : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                    try {
                        done.get();
                    } catch (java.util.concurrent.ExecutionException failed) {
                        failures.add("a task failed: " + failed.getCause());
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Directive directive(String id, String version) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Version " + version)
                .intent("Intent of version " + version)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }
}
