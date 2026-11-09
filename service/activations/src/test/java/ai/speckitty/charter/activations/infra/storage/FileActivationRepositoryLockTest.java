package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.Revision;
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
 * The per-id locks are striped: a fixed number of locks, however many ids are written, and two writes to one
 * id are still serialised by the compare-and-set.
 */
class FileActivationRepositoryLockTest {

    @TempDir
    Path root;

    @Test
    void tenThousandWritesToDistinctIdsKeepTheLockStructureAtItsStripeCount() {
        FileActivationRepository store = new FileActivationRepository(root);

        for (int n = 0; n < 10_000; n++) {
            store.create(new ActivationId("id-" + n), ActivationRepositoryContract.document(n % 7));
        }

        assertThat(store.lockCount()).isEqualTo(FileActivationRepository.LOCK_STRIPES);
    }

    @Test
    void caseVariantsAreLockedByTheirEncodedNames() {
        assertThat(FileActivationRepository.stripeOf(new ActivationId("ABC")))
                .isEqualTo(Math.floorMod("abc-7".hashCode(), FileActivationRepository.LOCK_STRIPES));
        assertThat(FileActivationRepository.stripeOf(new ActivationId("abc")))
                .isEqualTo(Math.floorMod("abc-0".hashCode(), FileActivationRepository.LOCK_STRIPES));
    }

    @Test
    void twoWritesWithOneRevisionToOneIdAreSerialisedSoExactlyOneWins() throws Exception {
        FileActivationRepository store = new FileActivationRepository(root);
        ActivationId id = new ActivationId("contended");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int run = 0; run < 100; run++) {
                Revision read = ((CasResult.Written)
                                store.replace(id, Optional.empty(), ActivationRepositoryContract.document(0)))
                        .stored()
                        .revision();
                CountDownLatch gate = new CountDownLatch(1);
                List<Future<CasResult>> writers = new ArrayList<>();
                for (int w = 1; w <= 2; w++) {
                    int n = w;
                    writers.add(pool.submit(() -> {
                        gate.await();
                        return store.replace(id, Optional.of(read), ActivationRepositoryContract.document(n));
                    }));
                }
                gate.countDown();
                int won = 0;
                for (Future<CasResult> writer : writers) {
                    won += writer.get(30, TimeUnit.SECONDS) instanceof CasResult.Written ? 1 : 0;
                }
                assertThat(won).as("run %d", run).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
