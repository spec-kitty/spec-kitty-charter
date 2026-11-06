package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file adapter under concurrency beyond the contract's 8-writer race (NFR-004): readers racing writers only ever
 * see a whole document, and writers of different ids do not wait on each other's outcome.
 */
class FileActivationRepositoryConcurrencyTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 100;

    @TempDir
    Path root;

    @Test
    void readersRacingWritersOnlyEverSeeAWholeWrittenDocument() throws Exception {
        FileActivationRepository store = new FileActivationRepository(root);
        ActivationId id = new ActivationId("read-while-written");
        store.create(id, ActivationRepositoryContract.document(0));
        Set<Revision> written = IntStream.rangeClosed(0, ROUNDS)
                .mapToObj(n -> ActivationRepositoryContract.document(n).revision())
                .collect(Collectors.toSet());
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<List<Revision>>> readers = new ArrayList<>();
            for (int r = 0; r < THREADS - 1; r++) {
                readers.add(pool.submit(() -> readRepeatedly(store, id, gate)));
            }
            Future<?> writer = pool.submit(() -> writeEveryRound(store, id, gate));
            gate.countDown();
            writer.get(60, TimeUnit.SECONDS);
            for (Future<List<Revision>> reader : readers) {
                assertThat(reader.get(60, TimeUnit.SECONDS)).isNotEmpty().allMatch(written::contains);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(store.find(id))
                .map(StoredActivation::document)
                .contains(ActivationRepositoryContract.document(ROUNDS));
    }

    private static List<Revision> readRepeatedly(FileActivationRepository store, ActivationId id, CountDownLatch gate)
            throws InterruptedException {
        gate.await();
        List<Revision> seen = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            seen.add(store.find(id).orElseThrow().revision());
        }
        return seen;
    }

    private static Boolean writeEveryRound(FileActivationRepository store, ActivationId id, CountDownLatch gate)
            throws InterruptedException {
        gate.await();
        for (int n = 1; n <= ROUNDS; n++) {
            store.replace(id, Optional.empty(), ActivationRepositoryContract.document(n));
        }
        return Boolean.TRUE;
    }

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
