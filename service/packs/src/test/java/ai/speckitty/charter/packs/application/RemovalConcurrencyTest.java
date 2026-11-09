package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code DELETE} of {@code a::X} against a relationship {@code PUT} of {@code b}'s {@code Y} onto it (IC-06, FR-007,
 * FR-036), a hundred runs: both take the registry lock, so one runs after the other and sees its result, and the two
 * never both succeed to leave a registered edge that points at an artifact the other removed. Every run proves that the
 * two writers overlapped: the swap of the writer that got the lock pauses until the other writer's thread is seen
 * queued for the lock ({@code RegistryWriteLock.hasQueuedThread}, which a thread in a sleep does not satisfy), so a
 * registry lock that let both through at once fails the run instead of passing it by luck of timing.
 */
@Tag("stress")
class RemovalConcurrencyTest {

    private static final String A_ID = PackRegistrationServiceTest.ulid('Y');
    private static final String B_ID = PackRegistrationServiceTest.ulid('Z');
    private static final int RUNS = 100;

    @Test
    @Timeout(240)
    void aDeleteAndARelationshipPutOntoItNeverBothSucceed(@TempDir Path host) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int run = 1; run <= RUNS; run++) {
                race(host.resolve("run-" + run), pool);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** One race on fresh packs. Either order is legal; what is asserted is the overlap and that both never succeed. */
    private static void race(Path host, ExecutorService pool) throws Exception {
        Rig rig = new Rig(host);
        rig.register("a", rig.stage(Packs.of(A_ID, "a", "1.0.0", "org").directive("X", "X")), Preconditions.none());
        rig.register(
                "b",
                rig.stage(Packs.of(B_ID, "b", "1.0.0", "org", dependsOnA()).directive("Y", "Y")),
                Preconditions.none());
        ArtifactRemovalService deletes = new ArtifactRemovalService(
                rig.repository,
                rig.writeStore,
                new PermissiveWritePolicyGate(),
                new InboundReferences(rig.repository, rig.repository, rig.repository),
                new StagedPackViewFactory(rig.locations, new YamlSerializationStrategy()),
                rig.impact);
        RelationshipRegistrationService puts = new RelationshipRegistrationService(
                rig.repository, rig.writeStore, new PermissiveWritePolicyGate(), (source, target) -> {});
        String revisionOfB =
                rig.writeStore.currentRevision(new PackIdentifier("b")).token();
        CyclicBarrier start = new CyclicBarrier(2);
        AtomicReferenceArray<Thread> writers = new AtomicReferenceArray<>(2);
        AtomicBoolean overlapped = new AtomicBoolean();
        AtomicBoolean first = new AtomicBoolean(true);
        // Inside the swap of the writer that got the lock first: wait until the other is seen blocked on it. The other
        // writer's own swap, later, has nobody to wait for.
        rig.midSwap = () -> {
            if (first.getAndSet(false)) {
                overlapped.set(awaitTheOtherWriterWaiting(rig.lock, writers));
            }
        };

        Future<Boolean> delete = pool.submit(attempt(start, writers, 0, () -> {
            deletes.remove(new PackIdentifier("a"), "directive", "X", "*");
        }));
        Future<Boolean> put = pool.submit(attempt(start, writers, 1, () -> {
            puts.registerRelationship(new PackIdentifier("b"), edgeOntoX(), revisionOfB);
        }));
        boolean deleted = delete.get(60, TimeUnit.SECONDS);
        boolean registered = put.get(60, TimeUnit.SECONDS);

        assertThat(overlapped)
                .as("the writer that held the lock saw the other waiting for it: the two overlapped")
                .isTrue();
        assertThat(deleted && registered)
                .as("the delete and the edge onto the artifact it removes cannot both be accepted")
                .isFalse();
        assertThat(deleted || registered)
                .as("and one of them is: the loser is refused, not lost")
                .isTrue();
        boolean xHeld = Files.exists(rig.packs.resolve("a").resolve("directives/X.directive.yaml"));
        boolean edgeHeld = Files.exists(rig.packs.resolve("b").resolve("registered.graph.yaml"));
        assertThat(edgeHeld && !xHeld)
                .as("no registered edge is left pointing at a removed artifact")
                .isFalse();
    }

    /** Polls the lock until the other writer's thread is queued for it; false when it never is. */
    private static boolean awaitTheOtherWriterWaiting(RegistryWriteLock lock, AtomicReferenceArray<Thread> writers) {
        Thread self = Thread.currentThread();
        Thread other = self.equals(writers.get(0)) ? writers.get(1) : writers.get(0);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (other != null && lock.hasQueuedThread(other)) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static String dependsOnA() {
        return "depends_on:\n  - pack_id: " + A_ID + "\n    range: '>=1.0'\n";
    }

    private static Relationship edgeOntoX() {
        return new Relationship(
                Urn.parse("directive:Y").orElseThrow(),
                Optional.empty(),
                Urn.parse("directive:X").orElseThrow(),
                Optional.of("a"),
                RelationType.REQUIRES,
                Optional.empty(),
                Optional.empty());
    }

    /** Runs the write once both sides are ready; true when it was accepted, false when it was refused. */
    private static Callable<Boolean> attempt(
            CyclicBarrier start, AtomicReferenceArray<Thread> writers, int index, Runnable write) {
        return () -> {
            writers.set(index, Thread.currentThread());
            start.await(30, TimeUnit.SECONDS);
            try {
                write.run();
                return true;
            } catch (CharterRefusal refused) {
                assertThat(refused.code())
                        .as("the loser is refused for what the winner did")
                        .isIn(RefusalCode.ARTIFACT_REFERENCED, RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                return false;
            }
        };
    }
}
