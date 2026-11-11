package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.RegistryWriteLock.RegistryBusyException;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.Locations;
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The registry write lock (NFR-009, R-lock): a contended caller ends {@code busy} (the registration job's {@code refused}
 * {@code 503}) after the injected timeout, deterministically and without a real 5 s wait, and every pack write the
 * write store runs takes it.
 */
class RegistryWriteLockTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final Duration SHORT = Duration.ofMillis(50);

    @Test
    void aBusyRegistryIsARetryLaterAnswerWhoseRetryAfterIsTheTimeoutRoundedUp() throws InterruptedException {
        RegistryWriteLock lock = new RegistryWriteLock(Duration.ofMillis(5_500));
        CountDownLatch release = holdOnAnotherThread(lock);
        try {
            RegistryWriteLock shortWait = new RegistryWriteLock(SHORT);
            assertThat(new RegistryBusyException("busy", 6))
                    .as("the registry's refusal is the one retry-later answer the transport renders")
                    .isInstanceOf(ai.speckitty.charter.kernel.transport.RetryLaterException.class);
            assertThat(shortWait.retryAfterSeconds())
                    .as("a 50 ms wait still tells the client to wait a second")
                    .isEqualTo(1);
            assertThat(lock.retryAfterSeconds()).isEqualTo(6);
        } finally {
            release.countDown();
        }
    }

    /** Holds {@code lock} on another thread until the returned latch is released. */
    private static CountDownLatch holdOnAnotherThread(RegistryWriteLock lock) throws InterruptedException {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> lock.exclusively(() -> {
            holding.countDown();
            await(release);
            return null;
        }));
        holder.setDaemon(true);
        holder.start();
        assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
        return release;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void aSecondCallerTimesOutBusyWhileTheLockIsHeldAndItsWorkNeverRuns() throws InterruptedException {
        RegistryWriteLock lock = new RegistryWriteLock(SHORT);
        CountDownLatch release = holdOnAnotherThread(lock);
        AtomicBoolean ran = new AtomicBoolean();
        long started = System.nanoTime();

        try {
            assertThatThrownBy(() -> lock.exclusively(() -> ran.getAndSet(true)))
                    .isInstanceOf(RegistryBusyException.class)
                    .hasMessageContaining("registry");
        } finally {
            release.countDown();
        }

        assertThat(ran).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .as("it waited the injected timeout, not the production 5 s")
                .isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void theDefaultTimeoutIsFiveSeconds() {
        assertThat(RegistryWriteLock.DEFAULT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void aCallerHoldingTheLockMayTakeItAgainAndItIsReleasedWhenTheWorkThrows() {
        RegistryWriteLock lock = new RegistryWriteLock(SHORT);

        assertThatThrownBy(() -> lock.exclusively(() -> lock.exclusively(() -> {
                    assertThat(lock.isHeldByCurrentThread()).isTrue();
                    throw new IllegalStateException("the work failed");
                })))
                .isInstanceOf(IllegalStateException.class);

        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void aWriteThroughTheWriteStoreTakesTheRegistryLockAndIsRefusedBusyWhileAnotherWriteHoldsIt(@TempDir Path host)
            throws Exception {
        Wiring wiring = new Wiring(host, new RegistryWriteLock(SHORT));
        PackRevision before = wiring.writeStore.currentRevision(PACK);
        CountDownLatch release = holdOnAnotherThread(wiring.registry);

        try {
            assertThatThrownBy(() -> wiring.directives.registerDirective(PACK, directive("GUARD"), false, null))
                    .as("the per-artifact registration waits for the registry lock first")
                    .isInstanceOf(RegistryBusyException.class);
            assertThatThrownBy(() -> wiring.writeStore.exclusively(PACK, () -> "work"))
                    .isInstanceOf(RegistryBusyException.class);
        } finally {
            release.countDown();
        }

        assertThat(wiring.writeStore.currentRevision(PACK))
                .as("nothing was written")
                .isEqualTo(before);
        assertThat(wiring.directives
                        .registerDirective(PACK, directive("GUARD"), false, null)
                        .created())
                .as("once the lock is free the same write goes through")
                .isTrue();
    }

    @Test
    void aCommitBracketsItsSwapWithTheWriteGeneration(@TempDir Path host) throws Exception {
        Wiring wiring = new Wiring(host, new RegistryWriteLock(SHORT));
        long idle = wiring.generation.registry();

        wiring.directives.registerDirective(PACK, directive("GUARD"), false, null);

        assertThat(PackWriteGeneration.isSwapping(wiring.generation.registry())).isFalse();
        assertThat(wiring.generation.registry())
                .as("the swap moved the generation")
                .isGreaterThan(idle);
        assertThat(wiring.generation.ofPack(PACK.value())).isGreaterThan(0);
    }

    // ---- fixture and wiring --------------------------------------------------------------------------------------

    private static final class Wiring {
        private final RegistryWriteLock registry;
        private final PackWriteGeneration generation = new PackWriteGeneration();
        private final CommitStorePackWriteStore writeStore;
        private final DirectiveRegistrationService directives;

        Wiring(Path host, RegistryWriteLock registry) throws IOException {
            Path packDir = host.resolve(PACK.value());
            Files.createDirectories(packDir);
            Files.writeString(
                    packDir.resolve("pack.yaml"),
                    "pack_id: 01M5DEH8ASSETREG1STRAT10N0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n",
                    StandardCharsets.UTF_8);
            StorageRootLocations locations = new StorageRootLocations(host);
            YamlSerializationStrategy strategy = new YamlSerializationStrategy();
            this.registry = registry;
            Locations asLocations = locations;
            this.writeStore = new CommitStorePackWriteStore(
                    asLocations, strategy, new PackCommitStore(locations, strategy), registry, generation);
            this.directives = new DirectiveRegistrationService(
                    new DomainRepositoryService(locations, strategy), writeStore, new PackMutabilityPolicy());
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
}
