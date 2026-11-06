package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Concurrent conditional writes through {@link ActivationService} over the file register (NFR-004): the
 * precondition the service judges first and the compare-and-set the register judges again agree, so of writers that
 * hold one revision exactly one wins, and of two create-only writers exactly one creates. Each race runs 100 times,
 * released by a latch, with a timeout and no sleep.
 */
class ActivationServiceRaceTest {

    private static final int RUNS = 100;
    private static final int WRITERS = 8;
    private static final long TIMEOUT_SECONDS = 30;

    @TempDir
    Path root;

    private final ExecutorService pool = Executors.newFixedThreadPool(WRITERS);

    @AfterEach
    void stopThePool() {
        pool.shutdownNow();
    }

    private ActivationService service() {
        String[] ids = new String[WRITERS + 1];
        for (int n = 0; n <= WRITERS; n++) {
            ids[n] = "D" + n;
        }
        FakeAvailablePacks packs = new FakeAvailablePacks(
                ClosureSnapshots.snapshot().held(ArtifactKind.DIRECTIVE, ids).build());
        return new ActivationService(new FileActivationRepository(root), packs, new ActivationIdMint());
    }

    private static ActivationDocument.Built selecting(int n) {
        return ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D" + n))
                .build();
    }

    /** The outcome of each writer: 1 for a write, 0 for a {@code 412}; anything else fails the race. */
    private List<Integer> race(List<Callable<ActivationService.Written>> writers) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Integer>> outcomes = new ArrayList<>();
        for (Callable<ActivationService.Written> writer : writers) {
            outcomes.add(pool.submit(() -> {
                gate.await();
                try {
                    writer.call();
                    return 1;
                } catch (PreconditionException stale) {
                    assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE);
                    return 0;
                }
            }));
        }
        gate.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> outcome : outcomes) {
            try {
                results.add(outcome.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (ExecutionException failed) {
                throw new AssertionError("a writer failed otherwise than 412", failed.getCause());
            }
        }
        return results;
    }

    @Test
    void ofEightWritersHoldingOneRevisionExactlyOneWinsAndSevenAre412() throws Exception {
        ActivationService service = service();
        for (int run = 0; run < RUNS; run++) {
            ActivationId id = new ActivationId("race-" + run);
            String revision = "\""
                    + service.put(id, selecting(0), null, null)
                            .stored()
                            .revision()
                            .token() + "\"";
            List<Callable<ActivationService.Written>> writers = new ArrayList<>();
            for (int w = 1; w <= WRITERS; w++) {
                ActivationDocument.Built mine = selecting(w);
                writers.add(() -> service.put(id, mine, revision, null));
            }

            List<Integer> outcomes = race(writers);

            assertThat(outcomes.stream().mapToInt(Integer::intValue).sum())
                    .as("run %d: one write, seven 412", run)
                    .isEqualTo(1);
        }
    }

    @Test
    void ofTwoCreateOnlyWritersOfOneNewIdExactlyOneCreatesAndTheOtherIs412() throws Exception {
        ActivationService service = service();
        for (int run = 0; run < RUNS; run++) {
            ActivationId id = new ActivationId("create-" + run);
            List<Boolean> created = Collections.synchronizedList(new ArrayList<>());
            List<Callable<ActivationService.Written>> writers = new ArrayList<>();
            for (int w = 1; w <= 2; w++) {
                ActivationDocument.Built mine = selecting(w);
                writers.add(() -> {
                    ActivationService.Written written = service.put(id, mine, null, "*");
                    created.add(written.created());
                    return written;
                });
            }

            List<Integer> outcomes = race(writers);

            assertThat(outcomes).as("run %d", run).containsExactlyInAnyOrder(1, 0);
            assertThat(created).as("run %d", run).containsExactly(true);
        }
    }

    /** A register in which another deleter removes the document between this deleter's read and its delete. */
    private static final class LosingDeleteRegister implements ActivationRepository {

        private final ActivationRepository real;

        LosingDeleteRegister(ActivationRepository real) {
            this.real = real;
        }

        @Override
        public Optional<StoredActivation> find(ActivationId id) {
            return real.find(id);
        }

        @Override
        public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
            return real.create(id, document);
        }

        @Override
        public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
            return real.replace(id, expected, document);
        }

        @Override
        public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
            real.delete(id, Optional.empty());
            return real.delete(id, expected);
        }
    }

    private ActivationService serviceWhereAnotherDeleterWinsFirst() {
        FakeAvailablePacks packs = new FakeAvailablePacks(
                ClosureSnapshots.snapshot().held(ArtifactKind.DIRECTIVE, "D0").build());
        return new ActivationService(
                new LosingDeleteRegister(new FileActivationRepository(root)), packs, new ActivationIdMint());
    }

    @Test
    void aDeleteWithIfMatchWhoseDocumentAnotherDeleterRemovedFirstIs412NotNotFound() {
        ActivationService service = serviceWhereAnotherDeleterWinsFirst();
        ActivationId id = new ActivationId("vanishes");
        String revision = "\""
                + service.put(id, selecting(0), null, null).stored().revision().token() + "\"";

        assertThatThrownBy(() -> service.delete(id, revision))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
    }

    @Test
    void aDeleteWithoutIfMatchWhoseDocumentAnotherDeleterRemovedFirstIsNotFound() {
        ActivationService service = serviceWhereAnotherDeleterWinsFirst();
        ActivationId id = new ActivationId("vanishes-too");
        service.put(id, selecting(0), null, null);

        assertThatThrownBy(() -> service.delete(id, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
    }

    @Test
    void ofEightDeletersHoldingOneRevisionExactlyOneDeletesAndSevenAre412() throws Exception {
        ActivationService service = service();
        for (int run = 0; run < RUNS; run++) {
            ActivationId id = new ActivationId("delete-race-" + run);
            String revision = "\""
                    + service.put(id, selecting(0), null, null)
                            .stored()
                            .revision()
                            .token() + "\"";
            List<Callable<ActivationService.Written>> deleters = new ArrayList<>();
            for (int w = 1; w <= WRITERS; w++) {
                deleters.add(() -> {
                    service.delete(id, revision);
                    return null;
                });
            }

            List<Integer> outcomes = race(deleters);

            assertThat(outcomes.stream().mapToInt(Integer::intValue).sum())
                    .as("run %d: one delete, seven 412", run)
                    .isEqualTo(1);
        }
    }
}
