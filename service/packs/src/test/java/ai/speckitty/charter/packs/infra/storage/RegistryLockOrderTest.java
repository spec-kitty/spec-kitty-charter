package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.DirectiveRegistrationService;
import ai.speckitty.charter.packs.application.PackMutabilityPolicy;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The lock order of every pack write (NFR-009, R-lock): the registry lock first, then the pack's own lock. */
class RegistryLockOrderTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    @Test
    void aWriterWaitingForThePacksLockAlreadyHoldsTheRegistryLock(@TempDir Path host) throws Exception {
        Path packDir = host.resolve("pack-a");
        Files.createDirectories(packDir);
        Files.writeString(
                packDir.resolve("pack.yaml"),
                "pack_id: 01M5DEH8ASSETREG1STRAT10N0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n",
                StandardCharsets.UTF_8);
        StorageRootLocations locations = new StorageRootLocations(host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        RegistryWriteLock registry = new RegistryWriteLock(Duration.ofSeconds(5));
        CommitStorePackWriteStore store = new CommitStorePackWriteStore(
                locations, strategy, new PackCommitStore(locations, strategy), registry, new PackWriteGeneration());
        ReentrantLock packLock = PackWriteLocks.of(packDir.toAbsolutePath().normalize());
        CountDownLatch entered = new CountDownLatch(1);

        Thread writer = new Thread(() -> store.exclusively(PACK, () -> {
            entered.countDown();
            return null;
        }));
        packLock.lock();
        try {
            writer.start();
            awaitQueued(packLock, writer);

            assertThat(registry.isLocked())
                    .as("the writer holds the registry lock while it waits for the pack's")
                    .isTrue();
            assertThat(entered.getCount()).as("and has not entered its work").isEqualTo(1);
        } finally {
            packLock.unlock();
        }
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        writer.join(10_000);
        assertThat(registry.isLocked()).as("both are released afterwards").isFalse();
        assertThat(packLock.isLocked()).isFalse();
    }

    /**
     * The race the registry lock closes for writers: a writer resolves its pack by listing the pack domain, and a listing
     * during another writer's swap can miss the directory being renamed ("pack not found"). So a writer resolves only
     * after it holds the registry lock, when no swap runs.
     */
    @Test
    void aWriterResolvesItsPackOnlyAfterAnotherWritersSwapEnded(@TempDir Path host) throws Exception {
        Path packDir = host.resolve("pack-a");
        Files.createDirectories(packDir);
        Files.writeString(
                packDir.resolve("pack.yaml"),
                "pack_id: 01M5DEH8ASSETREG1STRAT10N0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n",
                StandardCharsets.UTF_8);
        StorageRootLocations real = new StorageRootLocations(host);
        List<Boolean> listedMidSwap = new CopyOnWriteArrayList<>();
        Locations watching = () -> {
            listedMidSwap.add(!Files.exists(packDir));
            return real.all();
        };
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        CountDownLatch swapping = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PackCommitStore slow = SlowSwapCommitStores.pausingMidSwap(real, strategy, live -> {
            swapping.countDown();
            awaitQuietly(release);
        });
        RegistryWriteLock registry = new RegistryWriteLock(Duration.ofSeconds(20));
        PackWriteGeneration generation = new PackWriteGeneration();
        CommitStorePackWriteStore swapper = new CommitStorePackWriteStore(real, strategy, slow, registry, generation);
        CommitStorePackWriteStore second = new CommitStorePackWriteStore(
                watching, strategy, new PackCommitStore(real, strategy), registry, generation);
        DirectiveRegistrationService slowWrites = new DirectiveRegistrationService(
                new DomainRepositoryService(real, strategy), swapper, new PackMutabilityPolicy());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> slowWrites.registerDirective(PACK, directive("ONE"), true, null));
            assertThat(swapping.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> other = pool.submit(() -> second.exclusively(PACK, () -> "done"));
            Thread.sleep(300);
            release.countDown();
            first.get(20, TimeUnit.SECONDS);

            assertThat(other.get(20, TimeUnit.SECONDS)).isEqualTo("done");
            assertThat(listedMidSwap)
                    .as("the second writer never listed the pack domain mid-swap")
                    .doesNotContain(true);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Directive directive(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static void awaitQueued(ReentrantLock lock, Thread thread) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!lock.hasQueuedThread(thread)) {
            if (System.nanoTime() > deadline) {
                throw new IOException("the writer never queued on the pack lock");
            }
            Thread.onSpinWait();
        }
    }
}
