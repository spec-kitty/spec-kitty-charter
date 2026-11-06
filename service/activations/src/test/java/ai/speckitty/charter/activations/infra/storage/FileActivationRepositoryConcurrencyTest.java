package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file adapter under concurrency beyond the contract's 8-writer race (NFR-004): writers of different ids do not
 * wait on each other's outcome. That a reader only ever sees a whole document is {@link
 * FileActivationRepositoryAtomicityTest}'s, which reads past the lock.
 */
class FileActivationRepositoryConcurrencyTest {

    private static final int THREADS = 8;

    @TempDir
    Path root;

    @Test
    void writersOfDifferentIdsAllSucceed() throws Exception {
        FileActivationRepository store = new FileActivationRepository(root);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<CasResult>> writers = new ArrayList<>();
            for (int w = 0; w < THREADS; w++) {
                ActivationId id = new ActivationId("own-" + w);
                ActivationDocument mine = ActivationRepositoryContract.document(w);
                writers.add(pool.submit(() -> {
                    gate.await();
                    return store.replace(id, Optional.empty(), mine);
                }));
            }
            gate.countDown();
            for (Future<CasResult> writer : writers) {
                assertThat(writer.get(60, TimeUnit.SECONDS)).isInstanceOf(CasResult.Written.class);
            }
        } finally {
            pool.shutdownNow();
        }
        for (int w = 0; w < THREADS; w++) {
            assertThat(store.find(new ActivationId("own-" + w)))
                    .map(StoredActivation::document)
                    .contains(ActivationRepositoryContract.document(w));
        }
    }
}
