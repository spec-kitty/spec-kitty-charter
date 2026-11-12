package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ORG_PACK;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whole-pack registration under concurrency (NFR-006, FR-007): eight uploads of one pack with readers running, a
 * hundred runs, where every read sees one complete version; eight creates of one name, where one wins and the rest are
 * refused {@code 409}; and a whole-pack replace racing {@code PUT}s that carry {@code If-Match}, which leave one linear
 * {@code ETag} chain.
 *
 * <p>A version is told apart by a marker written into two files of the pack, the directives {@code D} and {@code E}. A
 * whole-pack swap replaces both at once, so a complete read carries one marker twice; a read that carried two markers
 * touched two versions. A read that hits the instant the live directory is missing is {@code stale_input}, which the
 * readers retry as {@code ClosureQueries} does, and which never counts as a mixed answer.
 */
@Tag("stress")
class RegistrationConcurrencyTest {

    private static final String PACK = "pack-x";
    private static final int UPLOADS = 8;
    private static final int READERS = 4;
    private static final int READS = 5;
    private static final int RUNS = 100;
    private static final int CREATE_RUNS = 50;

    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger staleAfterRetries = new AtomicInteger();
    private final AtomicInteger overlapped = new AtomicInteger();

    @Test
    @Timeout(240)
    void everyReadSeesOneCompleteVersionWhileEightUploadsReplaceThePack(@TempDir Path host) throws Exception {
        Rig rig = new Rig(host);
        rig.midSwap = () -> pause(1);
        assertThat(rig.register(PACK, rig.stage(version(0, 0, "m-initial")), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
        ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations), rig.generation);

        for (int run = 1; run <= RUNS; run++) {
            replaceEightTimesWithReaders(rig, read, run);
        }

        assertThat(failures)
                .as("a mixed answer, a failed upload or a stored pack that is no upload")
                .isEmpty();
        assertThat(reads.get()).as("the readers did get answers").isGreaterThan(RUNS);
        assertThat(overlapped.get())
                .as("reads were taken while the generation was odd, so the readers did overlap a swap")
                .isGreaterThan(0);
        assertThat(staleAfterRetries.get())
                .as(
                        "a read still stale_input after its retries (which passes: Q7), of %d snapshots",
                        reads.get() + staleAfterRetries.get())
                .isLessThanOrEqualTo((reads.get() + staleAfterRetries.get()) / 10);
    }

    private void replaceEightTimesWithReaders(Rig rig, ConsistentRead read, int run) throws Exception {
        List<StagedPack> uploads = new ArrayList<>();
        for (int upload = 0; upload < UPLOADS; upload++) {
            uploads.add(rig.stage(version(run, upload, "m-" + run + "-" + upload)));
        }
        CyclicBarrier start = new CyclicBarrier(UPLOADS + READERS);
        List<Callable<RegistrationOutcome>> tasks = new ArrayList<>();
        for (StagedPack upload : uploads) {
            tasks.add(() -> {
                start.await(30, TimeUnit.SECONDS);
                return rig.register(PACK, upload, Preconditions.none());
            });
        }
        for (int reader = 0; reader < READERS; reader++) {
            tasks.add(() -> {
                start.await(30, TimeUnit.SECONDS);
                for (int i = 0; i < READS; i++) {
                    readOnce(rig, read, run);
                }
                return null;
            });
        }
        List<RegistrationOutcome> outcomes =
                invokeAll(tasks).stream().filter(o -> o != null).toList();
        if (outcomes.size() != UPLOADS || outcomes.stream().anyMatch(o -> o.outcome() != Outcome.REPLACED)) {
            failures.add("run " + run + ": an upload was not a replace: " + outcomes);
        }
        boolean oneOfTheUploads = uploads.stream()
                .anyMatch(upload -> rig.filesOf(upload.directory()).equals(rig.filesOf(rig.packs.resolve(PACK))));
        if (!oneOfTheUploads) {
            failures.add("run " + run + ": the stored pack is none of the uploaded trees");
        }
    }

    private void readOnce(Rig rig, ConsistentRead read, int run) {
        PackIdentifier pack = new PackIdentifier(PACK);
        for (int retry = 0; ; retry++) {
            if (PackWriteGeneration.isSwapping(rig.generation.registry())) {
                overlapped.incrementAndGet();
            }
            try {
                String markers = read.read("stale", () -> markersOf(rig, pack)).value();
                reads.incrementAndGet();
                int slash = markers.indexOf('/');
                if (!markers.substring(0, slash).equals(markers.substring(slash + 1))) {
                    failures.add("run " + run + ": a mixed read " + markers);
                }
                return;
            } catch (CharterRefusal refused) {
                if (refused.code() != RefusalCode.STALE_INPUT) {
                    failures.add("run " + run + ": a read was refused " + refused.code());
                    return;
                }
                if (retry >= 3) {
                    staleAfterRetries.incrementAndGet();
                    return;
                }
                read.awaitWrites(Duration.ofMillis(250));
            }
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String markersOf(Rig rig, PackIdentifier pack) {
        String d = marker(rig, pack, "DIRECTIVE_D");
        Thread.onSpinWait();
        return d + "/" + marker(rig, pack, "DIRECTIVE_E");
    }

    private static String marker(Rig rig, PackIdentifier pack, String id) {
        return rig.repository
                .load(pack, new DirectiveIdentifier(id))
                .orElseThrow(() -> new IllegalStateException("no directive " + id))
                .title();
    }

    // ---- eight creates of one name -------------------------------------------------------------------------------

    @Test
    @Timeout(240)
    void ofEightCreatesOfOneNameOneWinsAndTheRestAreRefusedAsAConflict(@TempDir Path host) throws Exception {
        Rig rig = new Rig(host);

        for (int run = 0; run < CREATE_RUNS; run++) {
            raceCreates(rig, "race-" + run, run);
        }

        assertThat(failures)
                .as("a create that was not exactly one winner and seven conflicts")
                .isEmpty();
    }

    private void raceCreates(Rig rig, String name, int run) throws Exception {
        List<StagedPack> uploads = new ArrayList<>();
        for (int upload = 0; upload < UPLOADS; upload++) {
            // one version, eight contents: the second to get the lock sees the same version with another revision
            uploads.add(rig.stage(Packs.of("01M6R0WP14" + "%016d".formatted(run), "raced-" + run, "1.0.0", "org")
                    .directive("DIRECTIVE_D", "m-" + upload)
                    .directive("DIRECTIVE_E", "m-" + upload)));
        }
        CyclicBarrier start = new CyclicBarrier(UPLOADS);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (StagedPack upload : uploads) {
            tasks.add(() -> {
                start.await(30, TimeUnit.SECONDS);
                try {
                    return rig.register(name, upload, Preconditions.none());
                } catch (CharterRefusal refused) {
                    return refused.code();
                }
            });
        }
        List<Object> answers = invokeAll(tasks);
        List<RegistrationOutcome> created = answers.stream()
                .filter(RegistrationOutcome.class::isInstance)
                .map(RegistrationOutcome.class::cast)
                .toList();
        long conflicts = answers.stream()
                .filter(answer -> answer == RefusalCode.PACK_VERSION_CONFLICT)
                .count();
        if (created.size() != 1 || created.getFirst().outcome() != Outcome.CREATED || conflicts != UPLOADS - 1) {
            failures.add(name + ": answers " + answers);
            return;
        }
        boolean winnerStored = uploads.stream()
                .anyMatch(upload -> rig.filesOf(upload.directory()).equals(rig.filesOf(rig.packs.resolve(name))));
        if (!winnerStored) {
            failures.add(name + ": the stored pack is none of the uploaded trees");
        }
    }

    @Test
    @Timeout(240)
    void ofEightCreateOnlyRegistrationsOfOneNameOneIsCreatedAndTheRestFailThePrecondition(@TempDir Path host)
            throws Exception {
        Rig rig = new Rig(host);

        for (int run = 0; run < CREATE_RUNS; run++) {
            raceCreateOnly(rig, "only-" + run, run);
        }

        assertThat(failures)
                .as("a create-only race that was not one creation and seven 412s")
                .isEmpty();
    }

    private void raceCreateOnly(Rig rig, String name, int run) throws Exception {
        List<StagedPack> uploads = new ArrayList<>();
        for (int upload = 0; upload < UPLOADS; upload++) {
            // distinct versions: only If-None-Match: * can tell a loser from a replace
            uploads.add(rig.stage(Packs.of("01M6R0WP14" + "%016d".formatted(run), "only-" + run, "1.0." + upload, "org")
                    .directive("DIRECTIVE_D", "m-" + upload)));
        }
        CyclicBarrier start = new CyclicBarrier(UPLOADS);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (StagedPack upload : uploads) {
            tasks.add(() -> {
                start.await(30, TimeUnit.SECONDS);
                try {
                    return rig.register(name, upload, Preconditions.createOnly());
                } catch (PreconditionException stale) {
                    return stale.kind();
                }
            });
        }
        List<Object> answers = invokeAll(tasks);
        long created = answers.stream()
                .filter(RegistrationOutcome.class::isInstance)
                .map(RegistrationOutcome.class::cast)
                .filter(outcome -> outcome.outcome() == Outcome.CREATED)
                .count();
        long failed = answers.stream()
                .filter(answer -> answer == PreconditionException.Kind.STALE)
                .count();
        if (created != 1 || failed != UPLOADS - 1) {
            failures.add(name + ": answers " + answers);
        }
    }

    // ---- a replace racing PUTs with If-Match -----------------------------------------------------------------------

    @Test
    @Timeout(240)
    void aReplaceRacingPutsWithIfMatchLeavesOneLinearETagChain(@TempDir Path host) throws Exception {
        Rig rig = new Rig(host);
        PackIdentifier pack = new PackIdentifier(PACK);
        assertThat(rig.register(PACK, rig.stage(version(0, 0, "m-initial")), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
        DirectiveRegistrationService puts =
                new DirectiveRegistrationService(rig.repository, rig.writeStore, new PackMutabilityPolicy());
        PackRevision initial = rig.writeStore.currentRevision(pack);
        Map<PackRevision, PackRevision> chain = new HashMap<>();
        List<Integer> successes = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int writer = 0; writer < 4; writer++) {
                int index = writer;
                done.add(pool.submit(() -> putUntilCommitted(rig, puts, pack, index, chain, successes)));
            }
            done.add(pool.submit(() -> replaceUntilCommitted(rig, pack, chain, successes)));
            for (Future<?> one : done) {
                one.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(successes).as("every writer committed once per round").hasSize(4 * 8 + 4);
        assertThat(chain)
                .as("no two commits were made on the same revision: no lost update")
                .hasSize(successes.size());
        Set<PackRevision> walked = new HashSet<>();
        PackRevision at = initial;
        while (chain.containsKey(at)) {
            assertThat(walked.add(at))
                    .as("the chain never returns to a revision")
                    .isTrue();
            at = chain.get(at);
        }
        assertThat(walked).as("the chain is one path through every commit").hasSize(successes.size());
        assertThat(at).as("it ends at the revision the pack has now").isEqualTo(rig.writeStore.currentRevision(pack));
    }

    private void putUntilCommitted(
            Rig rig,
            DirectiveRegistrationService puts,
            PackIdentifier pack,
            int writer,
            Map<PackRevision, PackRevision> chain,
            List<Integer> successes) {
        for (int round = 0; round < 8; round++) {
            for (; ; ) {
                PackRevision current = rig.writeStore.currentRevision(pack);
                try {
                    PackRevision next = puts.registerDirective(
                                    pack, directive("PUT_" + writer + "_" + round), true, quoted(current))
                            .revision();
                    record(chain, successes, current, next);
                    break;
                } catch (PreconditionException stale) {
                    Thread.onSpinWait();
                } catch (RuntimeException failed) {
                    failures.add("put " + writer + "/" + round + ": " + failed);
                    return;
                }
            }
        }
    }

    private void replaceUntilCommitted(
            Rig rig, PackIdentifier pack, Map<PackRevision, PackRevision> chain, List<Integer> successes) {
        for (int round = 1; round <= 4; round++) {
            StagedPack upload = rig.stage(version(round, 0, "m-replace-" + round));
            for (; ; ) {
                PackRevision current = rig.writeStore.currentRevision(pack);
                try {
                    PackRevision next = rig.register(PACK, upload, Preconditions.ifMatch(quoted(current)))
                            .revision();
                    record(chain, successes, current, next);
                    break;
                } catch (PreconditionException stale) {
                    Thread.onSpinWait();
                } catch (RuntimeException failed) {
                    failures.add("replace " + round + ": " + failed);
                    return;
                }
            }
        }
    }

    private static synchronized void record(
            Map<PackRevision, PackRevision> chain, List<Integer> successes, PackRevision from, PackRevision to) {
        if (chain.putIfAbsent(from, to) != null) {
            successes.add(-1); // a second commit on one revision is a lost update; the sizes below catch it
        } else {
            successes.add(successes.size());
        }
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------

    private static Packs version(int run, int upload, String marker) {
        return Packs.of(ORG_PACK, "raced", "1." + run + "." + upload, "org")
                .directive("DIRECTIVE_D", marker)
                .directive("DIRECTIVE_E", marker);
    }

    private static String quoted(PackRevision revision) {
        return "\"" + revision.token() + "\"";
    }

    private static Directive directive(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(new PackIdentifier(PACK))
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static <T> List<T> invokeAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<T> answers = new ArrayList<>();
            for (Future<T> future : pool.invokeAll(tasks, 120, TimeUnit.SECONDS)) {
                answers.add(future.get());
            }
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }
}
