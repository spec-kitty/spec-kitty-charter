package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A verified read that meets a pack write between the two renames of its swap (FR-023, #490): the real swap
 * ({@link PackCommitStore#moveAside}, through {@link SlowSwapCommitStores}) is paused at its {@code afterAside} phase,
 * with the write generation odd, and a directive read through {@link DirectiveReadService} answers the last committed
 * state (R0) with its revision and fingerprint, not {@code stale_input}. Once the swap ends, the same read answers the
 * new state (R1). The test synchronizes on latches only.
 */
class VerifiedReadDuringSwapTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final DirectiveIdentifier DIRECTIVE = new DirectiveIdentifier("D");
    private static final long WAIT_SECONDS = 30;

    @TempDir
    Path host;

    private final PackWriteGeneration generation = new PackWriteGeneration();
    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    /** What the swap does between its renames; the write under test sets it, the first write leaves it a no-op. */
    private volatile Runnable midSwap = () -> {};

    private Path live;
    private FileSystemPackSources sources;
    private CommitStorePackWriteStore writeStore;
    private DirectiveRegistrationService registrations;
    private DirectiveReadService reads;

    @BeforeEach
    void aPackHoldingTheDirectiveAtR0() throws IOException {
        live = Files.createDirectories(host.resolve(PACK.value()));
        Files.writeString(
                live.resolve("pack.yaml"),
                "pack_id: 01M5DEH8ASSETREG1STRAT10N0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n",
                StandardCharsets.UTF_8);
        StorageRootLocations locations = new StorageRootLocations(host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService repository = new DomainRepositoryService(locations, strategy);
        RegistryWriteLock lock = new RegistryWriteLock(Duration.ofSeconds(WAIT_SECONDS));
        PackCommitStore commits =
                SlowSwapCommitStores.pausingMidSwap(locations, strategy, liveDir -> midSwap.run(), lock, generation);
        writeStore = new CommitStorePackWriteStore(locations, strategy, commits, lock, generation);
        registrations = new DirectiveRegistrationService(repository, writeStore, new PackMutabilityPolicy());
        sources = new FileSystemPackSources(locations);
        reads = new DirectiveReadService(repository, repository, new ConsistentRead(sources, generation), writeStore);
        write("R0");
    }

    @AfterEach
    void releaseTheSwap() {
        release.countDown();
    }

    @Test
    @Timeout(WAIT_SECONDS)
    void aDirectiveReadBetweenTheRenamesAnswersTheLastCommittedState() throws Exception {
        PackRevision before = writeStore.currentRevision(PACK);
        String fingerprintBefore = sources.fingerprint();

        CompletableFuture<Void> writing = writeR1PausedBetweenTheRenames();

        assertThat(live).as("the swap is between its renames").doesNotExist();
        assertThat(PackWriteGeneration.isSwapping(generation.registry()))
                .as("the write generation is odd")
                .isTrue();
        ConsistentRead.Verified<Revised<Directive>> during = reads.directiveWithRevision(PACK, DIRECTIVE);
        assertThat(during.value().value().title()).isEqualTo("R0");
        assertThat(during.value().revision()).isEqualTo(before);
        assertThat(during.fingerprint()).isEqualTo(fingerprintBefore);

        release.countDown();
        writing.get(WAIT_SECONDS, TimeUnit.SECONDS);

        ConsistentRead.Verified<Revised<Directive>> after = reads.directiveWithRevision(PACK, DIRECTIVE);
        assertThat(after.value().value().title()).isEqualTo("R1");
        assertThat(after.value().revision()).isNotEqualTo(before);
        assertThat(after.fingerprint()).isNotEqualTo(fingerprintBefore);
    }

    @Test
    @Timeout(WAIT_SECONDS)
    void aDirectiveListBetweenTheRenamesAnswersTheLastCommittedState() throws Exception {
        String fingerprintBefore = sources.fingerprint();

        CompletableFuture<Void> writing = writeR1PausedBetweenTheRenames();

        ConsistentRead.Verified<List<Directive>> during = reads.directivesOf(PACK);
        assertThat(during.value()).extracting(Directive::title).containsExactly("R0");
        assertThat(during.fingerprint()).isEqualTo(fingerprintBefore);

        release.countDown();
        writing.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(reads.directivesOf(PACK).value())
                .extracting(Directive::title)
                .containsExactly("R1");
    }

    /** Starts the write of R1 in its own thread and returns once its swap is paused between the two renames. */
    private CompletableFuture<Void> writeR1PausedBetweenTheRenames() throws InterruptedException {
        midSwap = () -> {
            paused.countDown();
            awaitRelease();
        };
        CompletableFuture<Void> done = new CompletableFuture<>();
        Thread.ofPlatform().start(() -> {
            try {
                write("R1");
                done.complete(null);
            } catch (RuntimeException failed) {
                done.completeExceptionally(failed);
            }
        });
        assertThat(paused.await(WAIT_SECONDS, TimeUnit.SECONDS))
                .as("the swap reached its pause")
                .isTrue();
        return done;
    }

    private void awaitRelease() {
        try {
            if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the swap");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted inside the swap", interrupted);
        }
    }

    private void write(String version) {
        registrations.registerDirective(PACK, directive(version), true, null);
    }

    private static Directive directive(String version) {
        return Directive.builder()
                .id(DIRECTIVE)
                .packId(PACK)
                .schemaVersion("1.0")
                .title(version)
                .intent("Intent of " + version)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }
}
