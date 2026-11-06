package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Concurrent selects through {@link ActivationSelectionService} over the file register (NFR-004): eight writers hold
 * one revision and each selects another directive. The precondition {@link ActivationWrites#loaded} judges and the
 * compare-and-set the register judges again agree, so exactly one write commits and the seven others are {@code 412};
 * the stored document holds the winner's directive alone. The race runs 100 times, released by a latch, with a timeout
 * and no sleep, as {@link ActivationServiceRaceTest} runs the document writes.
 */
class ActivationSelectionRaceTest {

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

    /** The directive each writer that committed selected; a {@code 412} adds nothing, anything else fails the race. */
    private List<String> race(List<Callable<String>> writers) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Optional<String>>> outcomes = new ArrayList<>();
        for (Callable<String> writer : writers) {
            outcomes.add(pool.submit(() -> {
                gate.await();
                try {
                    return Optional.of(writer.call());
                } catch (PreconditionException stale) {
                    assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE);
                    return Optional.empty();
                }
            }));
        }
        gate.countDown();
        List<String> committed = new ArrayList<>();
        for (Future<Optional<String>> outcome : outcomes) {
            try {
                outcome.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).ifPresent(committed::add);
            } catch (ExecutionException failed) {
                throw new AssertionError("a writer failed otherwise than 412", failed.getCause());
            }
        }
        return committed;
    }

    @Test
    void ofEightSelectsHoldingOneRevisionExactlyOneCommitsAndSevenAre412() throws Exception {
        String[] ids = new String[WRITERS];
        for (int w = 0; w < WRITERS; w++) {
            ids[w] = "D" + w;
        }
        FileActivationRepository repository = new FileActivationRepository(root);
        FakeAvailablePacks packs = new FakeAvailablePacks(
                ClosureSnapshots.snapshot().held(ArtifactKind.DIRECTIVE, ids).build());
        ActivationSelectionService service = new ActivationSelectionService(new ActivationWrites(repository, packs));
        for (int run = 0; run < RUNS; run++) {
            ActivationId id = new ActivationId("race-" + run);
            StoredActivation stored = repository
                    .create(id, ActivationDocument.builder().build().document())
                    .orElseThrow();
            String revision = "\"" + stored.revision().token() + "\"";
            List<Callable<String>> writers = new ArrayList<>();
            for (String directive : ids) {
                writers.add(() -> {
                    service.select(id, "directive", directive, Optional.empty(), false, List.of(), revision);
                    return directive;
                });
            }

            List<String> committed = race(writers);

            assertThat(committed).as("run %d: one select, seven 412", run).hasSize(1);
            assertThat(repository.find(id).orElseThrow().document().selected(ArtifactKind.DIRECTIVE))
                    .as("run %d: the winner's directive alone is stored", run)
                    .hasValueSatisfying(selected -> assertThat(selected).containsExactlyElementsOf(committed));
        }
    }
}
