package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
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
