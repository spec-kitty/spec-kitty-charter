package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.packs.RegistryWriteCoordination;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.transport.RetryLaterException;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * An activation write excludes a pack registration (WP15's review): it holds the registry write lock from its first read
 * of the packs to its store, so a registration that runs meanwhile either finds the activation stored (its impact check
 * lists it) or is seen by the activation's own judgement. A write that meets a registry busy for longer than the wait is
 * told to retry, with nothing stored.
 */
class ActivationWritesExcludeRegistrationsTest {

    private static final ActivationId ID = new ActivationId("a-1");

    /** A coordination that counts how deep inside a write the caller is. */
    private static final class Recording implements WriteCoordination {
        private int depth;
        private int entered;

        @Override
        public <T> T exclusively(Supplier<T> work) {
            depth++;
            entered++;
            try {
                return work.get();
            } finally {
                depth--;
            }
        }
    }

    /** A register that records whether each write to it happened inside a coordinated write. */
    private static final class Watching implements ActivationRepository {
        private final InMemoryActivationRepository stored = new InMemoryActivationRepository();
        private final Recording coordination;
        private final List<String> writes = new ArrayList<>();

        Watching(Recording coordination) {
            this.coordination = coordination;
        }

        private void written(String what) {
            writes.add(what + (coordination.depth > 0 ? ":inside" : ":outside"));
        }

        @Override
        public List<StoredActivation> list() {
            return stored.list();
        }

        @Override
        public Optional<StoredActivation> find(ActivationId id) {
            return stored.find(id);
        }

        @Override
        public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
            written("create");
            return stored.create(id, document);
        }

        @Override
        public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
            written("replace");
            return stored.replace(id, expected, document);
        }

        @Override
        public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
            written("delete");
            return stored.delete(id, expected);
        }
    }

    private static FakeAvailablePacks packs() {
        return new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D1", "D2")
                .fingerprint("sha256:first")
                .build());
    }

    private static String quoted(Watching repository) {
        return "\"" + repository.find(ID).orElseThrow().revision().token() + "\"";
    }

    @Test
    void everyWriteOfTheRegisterRunsInsideTheCoordinatedWrite() {
        Recording coordination = new Recording();
        Watching repository = new Watching(coordination);
        FakeAvailablePacks packs = packs();
        ActivationService service = new ActivationService(repository, packs, new ActivationIdMint(), coordination);
        ActivationWrites writes = new ActivationWrites(repository, packs, coordination);
        ActivationSelectionService selection = new ActivationSelectionService(writes);
        ActivationExclusionService exclusion = new ActivationExclusionService(writes);

        service.put(ID, ActivationDocument.builder().build(), null, "*");
        service.put(
                ID,
                ActivationDocument.builder()
                        .selected(ArtifactKind.DIRECTIVE, List.of("D1"))
                        .build(),
                null,
                null);
        selection.select(ID, "directive", "D2", Optional.empty(), false, List.of(), quoted(repository));
        selection.unselect(ID, "directive", "D2", quoted(repository));
        exclusion.deactivate(ID, "directive", "D2", Optional.empty(), List.of(), quoted(repository));
        exclusion.reactivate(ID, "directive", "D2", quoted(repository));
        service.delete(ID, quoted(repository));
        service.create(ActivationDocument.builder().build());

        assertThat(repository.writes)
                .as("the store of each write, in order")
                .containsExactly(
                        "create:inside",
                        "replace:inside",
                        "replace:inside",
                        "replace:inside",
                        "replace:inside",
                        "replace:inside",
                        "delete:inside",
                        "create:inside");
        assertThat(coordination.entered).as("one coordinated write each").isGreaterThanOrEqualTo(8);
    }

    @Test
    void aWriteThatMeetsABusyRegistryIsToldToRetryAndStoresNothing() throws InterruptedException {
        RegistryWriteLock lock = new RegistryWriteLock(Duration.ofMillis(50));
        WriteCoordination coordination = new RegistryWriteCoordination(lock);
        Watching repository = new Watching(new Recording());
        FakeAvailablePacks packs = packs();
        ActivationService service = new ActivationService(repository, packs, new ActivationIdMint(), coordination);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread registration = new Thread(() -> lock.exclusively(() -> {
            holding.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        registration.setDaemon(true);
        registration.start();
        assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> service.create(ActivationDocument.builder().build()))
                    .isInstanceOf(RetryLaterException.class);
            assertThat(repository.list()).as("nothing was stored").isEmpty();
        } finally {
            release.countDown();
        }
        assertThat(service.create(ActivationDocument.builder().build()))
                .as("the control: the same write with the registry free")
                .isNotNull();
    }
}
