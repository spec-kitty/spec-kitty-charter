package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.kernel.transport.RetryLaterException;
import ai.speckitty.charter.packs.application.AuditLog.Credential;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.application.RegistrationJobs.Kind;
import ai.speckitty.charter.packs.application.RegistrationJobs.Origin;
import ai.speckitty.charter.packs.application.RegistrationJobs.Refused;
import ai.speckitty.charter.packs.application.RegistrationJobs.Reservation;
import ai.speckitty.charter.packs.application.RegistrationJobs.Stager;
import ai.speckitty.charter.packs.application.RegistrationJobs.State;
import ai.speckitty.charter.packs.application.RegistrationJobs.Status;
import ai.speckitty.charter.packs.application.RegistrationJobs.Succeeded;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The registration jobs of IC-14 over the real flow and the real stores, with a fake clock for retention and the
 * deadline and a gate that holds a job where a test needs it (T077): the lifecycle, the bounds, the preconditions
 * judged again under the lock, the registry lock's refusal, nothing slow under the lock, the deadline, the crash
 * leftovers a job owns and the one audit entry of every attempt.
 */
class RegistrationJobsTest {

    private static final Instant START = Instant.parse("2026-11-07T10:00:00Z");
    private static final Origin ORIGIN =
            new Origin("worktree-1", Credential.WORKTREE_TOKEN, "registerPackFromArchive", "archive", "");

    private final List<RegistrationJobs> opened = new ArrayList<>();

    @AfterEach
    void closeTheJobs() {
        opened.forEach(RegistrationJobs::close);
    }

    // ---- the limits --------------------------------------------------------------------------------------------

    /** The numbers ADR-0065 D8 and NFR-008 name, by literal: a test that compares a constant with itself pins nothing. */
    @Test
    void theLimitsAreThoseOfTheDecisionAndTheDeadlineIsNinetySeconds() {
        assertThat(RegistrationJobs.RUNNING_LIMIT)
                .as("registrations that run at once")
                .isEqualTo(2);
        assertThat(RegistrationJobs.WAITING_LIMIT).as("registrations that wait").isEqualTo(8);
        assertThat(RegistrationJobs.DEADLINE)
                .as("the source deadline plus 30 s")
                .isEqualTo(Duration.ofSeconds(90));
        assertThat(RegistrationJobs.RETENTION)
                .as("how long an ended status is kept")
                .isEqualTo(Duration.ofHours(1));
        assertThat(RegistrationJobs.MAX_RETAINED).as("the most statuses kept").isEqualTo(1000);
    }

    // ---- the lifecycle -----------------------------------------------------------------------------------------

    @Test
    void aJobIsPendingThenRunningThenSucceeded(@TempDir Path host) {
        Harness h = new Harness(host);
        h.gate.holdBeforeRun();
        Reservation reservation = h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0")));

        assertThat(h.status(reservation).state()).isEqualTo(State.PENDING);
        assertThat(h.status(reservation).finishedAt()).isNull();
        h.gate.releaseBeforeRun();
        h.gate.holdWhileRunning();
        h.await(() -> h.status(reservation).state() == State.RUNNING);
        assertThat(h.status(reservation).ending()).isNull();
        h.gate.releaseWhileRunning();
        Status ended = h.awaitEnd(reservation);

        assertThat(ended.state()).isEqualTo(State.SUCCEEDED);
        assertThat(ended.finishedAt()).isEqualTo(START);
        assertThat(ended.ending()).isInstanceOfSatisfying(Succeeded.class, done -> {
            assertThat(done.outcome().outcome()).isEqualTo(Outcome.CREATED);
            assertThat(done.outcome().revision())
                    .isEqualTo(h.rig.writeStore.currentRevision(new PackIdentifier("pack-x")));
        });
    }

    @Test
    void anIdenticalReSendEndsSucceededUnchanged(@TempDir Path host) {
        Harness h = new Harness(host);
        h.awaitEnd(h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0"))));

        Status again = h.awaitEnd(h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0"))));

        assertThat(again.ending())
                .isInstanceOfSatisfying(
                        Succeeded.class,
                        done -> assertThat(done.outcome().outcome()).isEqualTo(Outcome.UNCHANGED));
    }

    @Test
    void aRefusalOfTheFlowIsTheRefusedStatusWithItsCode(@TempDir Path host) {
        Harness h = new Harness(host);
        h.awaitEnd(h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0"))));

        Status refused = h.awaitEnd(h.start(
                "pack-x",
                Preconditions.none(),
                h.stage(h.pack("pack-x", "1.0.0").directive("CHANGED", "Same version, other content"))));

        assertThat(refused.state()).isEqualTo(State.REFUSED);
        assertThat(refused.ending()).isInstanceOfSatisfying(Refused.class, why -> {
            assertThat(why.kind()).isEqualTo(Kind.CODED);
            assertThat(why.refusal().code()).isEqualTo(RefusalCode.PACK_VERSION_CONFLICT);
        });
    }

    @Test
    void aFailureNoRefusalNamesIsAnInternalErrorThatLeaksNothing(@TempDir Path host) {
        Harness h = new Harness(host);

        Status failed = h.awaitEnd(h.start("pack-x", Preconditions.none(), directory -> {
            throw new IllegalStateException("secret path /etc/shadow");
        }));

        assertThat(failed.state()).isEqualTo(State.REFUSED);
        assertThat(failed.ending()).isInstanceOfSatisfying(Refused.class, why -> {
            assertThat(why.kind()).isEqualTo(Kind.FAILED);
            assertThat(why.detail()).doesNotContain("shadow");
        });
        assertThat(h.audit.entries())
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("failed:internal_error"));
    }

    // ---- the bounds --------------------------------------------------------------------------------------------

    @Test
    void twoRunAndEightWaitAndTheNextIsToldToRetryLater(@TempDir Path host) {
        Harness h = new Harness(host);
        h.gate.holdBeforeRun();
        List<Reservation> accepted = new ArrayList<>();
        for (int i = 1; i <= RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT; i++) {
            accepted.add(h.start("pack-" + i, Preconditions.none(), h.stage(h.pack("pack-" + i, "1.0.0"))));
        }

        assertThatThrownBy(() -> h.jobs.reserve("pack-eleven", Preconditions.none(), ORIGIN))
                .isInstanceOfSatisfying(
                        RetryLaterException.class,
                        busy -> assertThat(busy.retryAfterSeconds()).isGreaterThanOrEqualTo(1));
        assertThat(h.rig.packs.resolve("pack-eleven")).doesNotExist();

        h.gate.releaseBeforeRun();
        accepted.forEach(h::awaitEnd);
        Reservation control = h.start("pack-eleven", Preconditions.none(), h.stage(h.pack("pack-eleven", "1.0.0")));
        assertThat(h.awaitEnd(control).ending())
                .as("a slot is free again once the jobs ended")
                .isInstanceOf(Succeeded.class);
    }

    @Test
    void endedStatusesAreKeptAnHourAndAtMostAThousand(@TempDir Path host) {
        Harness h = new Harness(host);
        Reservation first = h.start("p-0", Preconditions.none(), directory -> {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "refused at once");
        });
        h.awaitEnd(first);
        h.clock.advance(RegistrationJobs.RETENTION.minusSeconds(1));
        assertThat(h.jobs.status(first.id()))
                .as("kept until the hour has passed")
                .isPresent();
        h.clock.advance(Duration.ofSeconds(2));
        assertThat(h.jobs.status(first.id())).as("forgotten once it has").isEmpty();

        List<Reservation> many = new ArrayList<>();
        for (int i = 1; i <= RegistrationJobs.MAX_RETAINED + 5; i++) {
            Reservation next = h.start("p-" + i, Preconditions.none(), directory -> {
                throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "refused at once");
            });
            h.awaitEnd(next);
            many.add(next);
        }

        assertThat(h.jobs.retained()).isLessThanOrEqualTo(RegistrationJobs.MAX_RETAINED);
        assertThat(h.jobs.status(many.getFirst().id()))
                .as("the oldest ended status is evicted")
                .isEmpty();
        assertThat(h.jobs.status(many.getLast().id())).as("the newest is kept").isPresent();
    }

    @Test
    void aStatusIdNobodyIssuedIsUnknown(@TempDir Path host) {
        Harness h = new Harness(host);

        assertThat(h.jobs.status("01M6TQ62HXYF362GAFS2BZ3PGF")).isEmpty();
    }

    // ---- the preconditions ---------------------------------------------------------------------------------------

    @Test
    void aStaleIfMatchIsRefusedBeforeAnySlotIsTaken(@TempDir Path host) {
        Harness h = new Harness(host);
        h.awaitEnd(h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0"))));

        assertThatThrownBy(() ->
                        h.jobs.reserve("pack-x", Preconditions.ifMatch("\"sha256:" + "0".repeat(64) + "\""), ORIGIN))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThatThrownBy(() -> h.jobs.reserve("pack-x", Preconditions.createOnly(), ORIGIN))
                .isInstanceOf(PreconditionException.class);
        assertThat(h.jobs.owned()).as("no slot, no staging directory").isEmpty();
        assertThat(h.stagingEntries()).isEmpty();
    }

    @Test
    void anIfMatchOfAPackThatIsNotStoredIsRefusedAtAcceptance(@TempDir Path host) {
        Harness h = new Harness(host);

        assertThatThrownBy(() ->
                        h.jobs.reserve("pack-x", Preconditions.ifMatch("\"sha256:" + "0".repeat(64) + "\""), ORIGIN))
                .isInstanceOf(PreconditionException.class);
    }

    @Test
    void theIfMatchIsJudgedAgainUnderTheLockAndARevisionThatMovedEndsTheJobRefused(@TempDir Path host) {
        Harness h = new Harness(host);
        h.awaitEnd(h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0"))));
        String current =
                h.rig.writeStore.currentRevision(new PackIdentifier("pack-x")).token();
        h.gate.holdBeforeRun();
        Reservation held = h.start(
                "pack-x",
                Preconditions.ifMatch(current),
                h.stage(h.pack("pack-x", "1.1.0").directive("D", "The registration that waits")));
        assertThat(h.status(held).state())
                .as("accepted: the revision still matched")
                .isEqualTo(State.PENDING);

        // A competing commit while the job waits.
        h.rig.register(
                "pack-x", h.rig.stage(h.pack("pack-x", "1.0.1").directive("C", "Competing")), Preconditions.none());
        h.gate.releaseBeforeRun();
        Status ended = h.awaitEnd(held);

        assertThat(ended.state()).isEqualTo(State.REFUSED);
        assertThat(ended.ending())
                .isInstanceOfSatisfying(
                        Refused.class, why -> assertThat(why.kind()).isEqualTo(Kind.STALE));
        assertThat(h.rig.filesOf(h.rig.packs.resolve("pack-x")).get("pack.yaml"))
                .as("the competing commit stands")
                .contains("pack_version: 1.0.1");
    }

    // ---- the registry lock
    // -------------------------------------------------------------------------------------------

    @Test
    void aJobThatCannotTakeTheRegistryLockInTimeEndsRefusedBusy(@TempDir Path host) throws Exception {
        Harness h = new Harness(host, Duration.ofMillis(50));
        CountDownLatch release = holdTheRegistryLock(h.rig);
        try {
            Status busy = h.awaitEnd(h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0"))));

            assertThat(busy.state()).isEqualTo(State.REFUSED);
            assertThat(busy.ending())
                    .isInstanceOfSatisfying(
                            Refused.class, why -> assertThat(why.kind()).isEqualTo(Kind.BUSY));
            assertThat(h.rig.packs.resolve("pack-x")).as("nothing was stored").doesNotExist();
        } finally {
            release.countDown();
        }
        Status control = h.awaitEnd(h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0"))));
        assertThat(control.state())
                .as("the control: the same registration with the lock free")
                .isEqualTo(State.SUCCEEDED);
    }

    @Test
    void nothingSlowRunsUnderTheRegistryLock(@TempDir Path host) throws Exception {
        Harness h = new Harness(host);
        List<Boolean> lockedWhileStaging = Collections.synchronizedList(new ArrayList<>());
        Stager watching = directory -> {
            lockedWhileStaging.add(h.rig.lock.isLocked());
            return h.stage(h.pack("pack-x", "1.0.0")).stage(directory);
        };

        h.awaitEnd(h.start("pack-x", Preconditions.none(), watching));

        assertThat(lockedWhileStaging)
                .as("the stager ran, and the registry lock was free")
                .containsExactly(false);

        // The control: the same observation sees the lock when another thread holds it.
        Harness other = new Harness(host.resolve("other"), Duration.ofMillis(50));
        CountDownLatch release = holdTheRegistryLock(other.rig);
        try {
            List<Boolean> seen = Collections.synchronizedList(new ArrayList<>());
            other.awaitEnd(other.start("pack-y", Preconditions.none(), directory -> {
                seen.add(other.rig.lock.isLocked());
                return other.stage(other.pack("pack-y", "1.0.0")).stage(directory);
            }));
            assertThat(seen)
                    .as("the observation can see a held lock, so the check above is not vacuous")
                    .containsExactly(true);
        } finally {
            release.countDown();
        }
    }

    // ---- the free space ------------------------------------------------------------------------------------------

    @Test
    void aVolumeWithoutRoomEndsTheJobRefusedBeforeAnythingIsStaged(@TempDir Path host) {
        Harness h = new Harness(host);
        h.room = () -> {
            throw new CharterRefusal(RefusalCode.PACK_STORAGE_FULL, "less than 64 MiB free");
        };
        AtomicBoolean staged = new AtomicBoolean();

        Status ended = h.awaitEnd(h.start("pack-x", Preconditions.none(), directory -> {
            staged.set(true);
            return h.stage(h.pack("pack-x", "1.0.0")).stage(directory);
        }));

        assertThat(ended.ending())
                .isInstanceOfSatisfying(
                        Refused.class,
                        why -> assertThat(why.refusal().code()).isEqualTo(RefusalCode.PACK_STORAGE_FULL));
        assertThat(staged).as("the stager never ran").isFalse();
        assertThat(h.rig.packs.resolve("pack-x")).doesNotExist();
    }

    // ---- the deadline ------------------------------------------------------------------------------------------

    @Test
    void aJobNotFinishedAtTheDeadlineEndsRefusedTimedOutAndWritesNothingWhenItWakes(@TempDir Path host) {
        Harness h = new Harness(host);
        h.gate.holdBeforeRun();
        Reservation held = h.start("pack-x", Preconditions.none(), h.stage(h.pack("pack-x", "1.0.0")));

        h.clock.advance(RegistrationJobs.DEADLINE.minusSeconds(1));
        h.jobs.expireOverdue();
        assertThat(h.status(held).state()).as("not yet due").isEqualTo(State.PENDING);
        h.clock.advance(Duration.ofSeconds(2));
        h.jobs.expireOverdue();

        assertThat(h.status(held).state()).isEqualTo(State.REFUSED);
        assertThat(h.status(held).ending()).isInstanceOfSatisfying(Refused.class, why -> {
            assertThat(why.kind()).isEqualTo(Kind.CODED);
            assertThat(why.refusal().code()).isEqualTo(RefusalCode.REGISTRATION_TIMED_OUT);
        });
        h.gate.releaseBeforeRun();
        h.jobs.close();
        assertThat(h.rig.packs.resolve("pack-x"))
                .as("the timed-out job wrote nothing when it woke")
                .doesNotExist();
        assertThat(h.audit.entries())
                .as("one entry for the attempt, however it ended")
                .hasSize(1);
        assertThat(h.audit.entries().getFirst().outcome()).isEqualTo("refused:registration_timed_out");
    }

    @Test
    void aJobStuckStagingTimesOutAndItsLateTreeIsNeverCommitted(@TempDir Path host) {
        Harness h = new Harness(host);
        CountDownLatch stuck = new CountDownLatch(1);
        CountDownLatch inside = new CountDownLatch(1);
        Reservation held = h.start("pack-x", Preconditions.none(), directory -> {
            inside.countDown();
            try {
                stuck.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return h.stage(h.pack("pack-x", "1.0.0")).stage(directory);
        });
        h.await(() -> inside.getCount() == 0);

        h.clock.advance(RegistrationJobs.DEADLINE.plusSeconds(1));
        h.jobs.expireOverdue();
        stuck.countDown();
        h.jobs.close();

        assertThat(h.status(held).state()).isEqualTo(State.REFUSED);
        assertThat(h.rig.packs.resolve("pack-x"))
                .as("the job told the client it timed out, so it must not store the pack afterwards")
                .doesNotExist();
    }

    @Test
    void aJobThatBeganItsCommitIsNotTimedOutAndEndsAsTheCommitDid(@TempDir Path host) throws Exception {
        CountDownLatch swapping = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Harness slow = new Harness(host.resolve("slow"), Duration.ofSeconds(30), () -> {
            swapping.countDown();
            await(release);
        });
        slow.awaitEnd(slow.start("pack-x", Preconditions.none(), slow.stage(slow.pack("pack-x", "1.0.0"))));
        Reservation replacing = slow.start(
                "pack-x",
                Preconditions.none(),
                slow.stage(slow.pack("pack-x", "1.1.0").directive("N", "New")));
        assertThat(swapping.await(10, TimeUnit.SECONDS))
                .as("the replace is inside its swap")
                .isTrue();

        slow.clock.advance(RegistrationJobs.DEADLINE.plusSeconds(60));
        slow.jobs.expireOverdue();
        assertThat(slow.status(replacing).state())
                .as("past the point of no return the job is not timed out")
                .isEqualTo(State.RUNNING);
        release.countDown();
        Status ended = slow.awaitEnd(replacing);

        assertThat(ended.state()).isEqualTo(State.SUCCEEDED);
        assertThat(slow.audit.entries().getLast().outcome()).isEqualTo("succeeded:replaced");
    }

    @Test
    void aDeadlineThatReachesTheJobAsItsCommitBeginsDoesNotEndIt(@TempDir Path host) throws Exception {
        CountDownLatch swapping = new CountDownLatch(1);
        CountDownLatch finishSwap = new CountDownLatch(1);
        Harness h = new Harness(host, Duration.ofSeconds(30), () -> {
            swapping.countDown();
            await(finishSwap);
        });
        h.rig.install("pack-x", h.pack("pack-x", "1.0.0"));
        h.gate.holdWhileRunning();
        Reservation replacing = h.start(
                "pack-x",
                Preconditions.none(),
                h.stage(h.pack("pack-x", "1.1.0").directive("N", "New")));
        h.await(() -> h.status(replacing).state() == State.RUNNING);
        h.clock.advance(RegistrationJobs.DEADLINE.plusSeconds(1));
        CountDownLatch inTheEnd = new CountDownLatch(1);
        CountDownLatch goOn = new CountDownLatch(1);
        Thread watchdog = new Thread(() -> {
            // Reading 1 is the sweep's own; reading 2 is the job's, taken as it ends the job.
            h.clock.pauseThisThreadAtCall(2, inTheEnd, goOn);
            h.jobs.expireOverdue();
        });
        watchdog.setDaemon(true);
        watchdog.start();
        assertThat(inTheEnd.await(10, TimeUnit.SECONDS))
                .as("the watchdog is about to end the job")
                .isTrue();

        h.gate.releaseWhileRunning();
        assertThat(swapping.await(10, TimeUnit.SECONDS))
                .as("the job began its commit")
                .isTrue();
        goOn.countDown();
        watchdog.join(TimeUnit.SECONDS.toMillis(10));
        finishSwap.countDown();
        Status ended = h.awaitEnd(replacing);

        assertThat(ended.state())
                .as("a job that began its commit is never reported refused, whoever reaches it")
                .isEqualTo(State.SUCCEEDED);
        assertThat(h.audit.entries()).hasSize(1);
        assertThat(h.audit.entries().getFirst().outcome()).isEqualTo("succeeded:replaced");
    }

    @Test
    void atMostTheRunningLimitOfJobsRunAtOnce(@TempDir Path host) throws Exception {
        Harness h = new Harness(host);
        int admitted = RegistrationJobs.RUNNING_LIMIT + 3;
        CountDownLatch entered = new CountDownLatch(RegistrationJobs.RUNNING_LIMIT);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger mostInside = new AtomicInteger();
        AtomicInteger entries = new AtomicInteger();
        List<Reservation> accepted = new ArrayList<>();
        for (int i = 0; i < admitted; i++) {
            String id = "run-" + i;
            Stager counting = directory -> {
                entries.incrementAndGet();
                mostInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                entered.countDown();
                await(release);
                inside.decrementAndGet();
                return h.stage(h.pack(id, "1.0.0")).stage(directory);
            };
            accepted.add(h.start(id, Preconditions.none(), counting));
        }

        assertThat(entered.await(10, TimeUnit.SECONDS))
                .as("exactly the running limit of stagers is entered")
                .isTrue();
        // A job over the limit would enter soon after the first two; give it the time to, and watch the count.
        long watchUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
        while (System.nanoTime() < watchUntil) {
            assertThat(inside.get()).isLessThanOrEqualTo(RegistrationJobs.RUNNING_LIMIT);
            Thread.sleep(5);
        }
        assertThat(entries.get())
                .as("the jobs over the limit wait for a worker and are not inside a stager")
                .isEqualTo(RegistrationJobs.RUNNING_LIMIT);

        release.countDown();
        accepted.forEach(h::awaitEnd);
        assertThat(entries.get()).as("every job ran in the end").isEqualTo(admitted);
        assertThat(mostInside.get())
                .as("never more than the running limit inside a stager at once")
                .isEqualTo(RegistrationJobs.RUNNING_LIMIT);
    }

    @Test
    void aJobEndedByTheDeadlineKeepsItsSlotUntilItsWorkerIsFree(@TempDir Path host) throws Exception {
        Harness h = new Harness(host);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(RegistrationJobs.RUNNING_LIMIT);
        Stager stuck = directory -> {
            entered.countDown();
            // A stager that ignores interrupts, as a read of a slow disk does.
            boolean interrupted = false;
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return h.stage(h.pack("late", "1.0.0")).stage(directory);
        };
        int admitted = RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT;
        for (int i = 0; i < admitted; i++) {
            h.start("held-" + i, Preconditions.none(), stuck);
        }
        // owned() counts reserved jobs too, so it is true at once: wait for the workers to be inside their stagers.
        assertThat(entered.await(10, TimeUnit.SECONDS))
                .as("both workers are inside a stager that ignores interrupts")
                .isTrue();
        h.clock.advance(RegistrationJobs.DEADLINE.plusSeconds(1));
        h.jobs.expireOverdue();

        // Two workers are still held by their stagers, so only eight more may wait, never more.
        for (int i = 0; i < RegistrationJobs.WAITING_LIMIT; i++) {
            h.start("next-" + i, Preconditions.none(), h.stage(h.pack("next-" + i, "1.0.0")));
        }
        assertThatThrownBy(() -> h.jobs.reserve("one-too-many", Preconditions.none(), ORIGIN_FOR_ORDER))
                .as("two workers still run past the deadline, so the ninth waiting job is refused")
                .isInstanceOf(RetryLaterException.class);
        release.countDown();
    }

    @Test
    void aReservationThatIsNeverStartedExpiresAndFreesItsSlot(@TempDir Path host) {
        Harness h = new Harness(host);
        Reservation leaked = h.jobs.reserve("pack-x", Preconditions.none(), ORIGIN);
        assertThat(h.jobs.owned()).containsExactly(leaked.directory());

        h.clock.advance(RegistrationJobs.DEADLINE.plusSeconds(1));
        h.jobs.expireOverdue();

        assertThat(h.status(leaked).state()).isEqualTo(State.REFUSED);
        assertThat(h.jobs.owned()).isEmpty();
        assertThat(leaked.directory()).doesNotExist();
    }

    // ---- what a job leaves on disk -------------------------------------------------------------------------------

    @Test
    void theStagingDirectoryIsOwnedWhileTheJobLivesAndGoneWhenItEnds(@TempDir Path host) {
        Harness h = new Harness(host);
        h.gate.holdBeforeRun();
        Reservation reservation = h.jobs.reserve("pack-x", Preconditions.none(), ORIGIN);
        assertThat(reservation.directory()).isDirectory().startsWith(h.staging);
        assertThat(h.jobs.owned()).containsExactly(reservation.directory());
        reservation.start(h.stage(h.pack("pack-x", "1.0.0")));

        h.gate.releaseBeforeRun();
        h.awaitEnd(reservation);

        assertThat(reservation.directory()).doesNotExist();
        assertThat(h.jobs.owned()).isEmpty();
        assertThat(h.stagingEntries()).isEmpty();
    }

    @Test
    void aRefusedJobLeavesNoStagingDirectoryEither(@TempDir Path host) {
        Harness h = new Harness(host);

        h.awaitEnd(h.start("pack-x", Preconditions.none(), directory -> {
            Files.writeString(directory.resolve("half-extracted"), "x");
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "refused");
        }));

        assertThat(h.stagingEntries()).isEmpty();
    }

    @Test
    void anAbandonedReservationFreesItsSlotAndItsDirectory(@TempDir Path host) {
        Harness h = new Harness(host);
        h.gate.holdBeforeRun();
        for (int i = 0; i < RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT; i++) {
            Reservation reservation = h.jobs.reserve("pack-" + i, Preconditions.none(), ORIGIN);
            reservation.abandon();
            reservation.abandon();
        }

        Reservation next = h.jobs.reserve("pack-next", Preconditions.none(), ORIGIN);

        assertThat(next.directory()).isDirectory();
        assertThat(h.stagingEntries())
                .containsExactly(next.directory().getFileName().toString());
        assertThat(h.audit.entries())
                .as("an abandoned reservation is a request that failed; the web edge audits it")
                .isEmpty();
    }

    @Test
    void closingTheJobsEndsWhatWaitsRefusedBusyAndAuditsEachOnce(@TempDir Path host) {
        Harness h = new Harness(host);
        h.gate.holdBeforeRun();
        List<Reservation> waiting = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            waiting.add(h.start("pack-" + i, Preconditions.none(), h.stage(h.pack("pack-" + i, "1.0.0"))));
        }
        // The two workers stay held until close() has refused all four jobs, then wake inside close()'s grace and must
        // write nothing. Left held, they would keep close() waiting out its whole 30 s grace on every run, and under PIT
        // on every mutant this test covers (#461).
        Thread releaser = new Thread(() -> {
            h.await(() -> waiting.stream()
                    .allMatch(reservation -> h.jobs.status(reservation.id())
                            .map(Status::state)
                            .filter(state -> state == State.REFUSED)
                            .isPresent()));
            h.gate.releaseBeforeRun();
        });
        releaser.setDaemon(true);
        releaser.start();

        h.jobs.close();

        for (Reservation reservation : waiting) {
            Status status = h.status(reservation);
            assertThat(status.state()).isEqualTo(State.REFUSED);
            assertThat(status.ending())
                    .isInstanceOfSatisfying(
                            Refused.class, why -> assertThat(why.kind()).isEqualTo(Kind.BUSY));
        }
        assertThat(h.audit.entries()).hasSize(4);
        assertThat(h.stagingEntries()).isEmpty();
        assertThat(h.rig.packs).isEmptyDirectory();
    }

    @Test
    void aJobIsNeverSeenEndedBeforeItsAuditEntryIsWritten(@TempDir Path host) throws InterruptedException {
        Harness h = new Harness(host);
        CountDownLatch release = new CountDownLatch(1);
        h.audit.holdWrites(release);
        java.util.concurrent.atomic.AtomicInteger entriesWhenSeenEnded =
                new java.util.concurrent.atomic.AtomicInteger(-1);
        CountDownLatch observed = new CountDownLatch(1);
        Reservation job = h.jobs.reserve("pack-x", Preconditions.none(), ORIGIN_FOR_ORDER);
        Thread reader = new Thread(() -> {
            h.await(() -> h.jobs.status(job.id())
                    .map(Status::state)
                    .filter(s -> s == State.SUCCEEDED || s == State.REFUSED)
                    .isPresent());
            entriesWhenSeenEnded.set(h.audit.entries().size());
            observed.countDown();
        });
        reader.setDaemon(true);
        reader.start();
        job.start(h.stage(h.pack("pack-x", "1.0.0")));

        boolean seenWhileWriteHeld = observed.await(1, TimeUnit.SECONDS);
        release.countDown();
        reader.join(TimeUnit.SECONDS.toMillis(20));

        assertThat(seenWhileWriteHeld)
                .as("the job was reported ended while its audit entry was still being written")
                .isFalse();
        assertThat(entriesWhenSeenEnded.get())
                .as("entries written when the end first became visible")
                .isEqualTo(1);
    }

    // ---- the audit trail -------------------------------------------------------------------------------------------

    @Test
    void everyAttemptWritesOneEntryWithTheNfr010Fields(@TempDir Path host) {
        Harness h = new Harness(host);
        Origin origin = new Origin(
                "worktree-7",
                Credential.NONE,
                "registerPackFromSource",
                "https_archive",
                "https://packs.example.org/a.tgz");

        Reservation created = h.jobs.reserve("pack-x", Preconditions.none(), origin);
        created.start(h.stage(h.pack("pack-x", "1.0.0")));
        h.awaitEnd(created);
        Reservation replaced = h.jobs.reserve("pack-x", Preconditions.none(), origin);
        replaced.start(h.stage(h.pack("pack-x", "1.1.0").directive("D", "Two")));
        h.awaitEnd(replaced);
        Reservation refused = h.jobs.reserve("pack-x", Preconditions.none(), origin);
        refused.start(h.stage(h.pack("pack-x", "1.1.0").directive("D", "Other content, same version")));
        h.awaitEnd(refused);

        List<AuditLog.Entry> entries = h.audit.entries();
        assertThat(entries).hasSize(3);
        String first =
                h.rig.writeStore.currentRevision(new PackIdentifier("pack-x")).token();
        assertThat(entries.get(0).outcome()).isEqualTo("succeeded:created");
        assertThat(entries.get(0).revisionBefore()).isEmpty();
        assertThat(entries.get(1).outcome()).isEqualTo("succeeded:replaced");
        assertThat(entries.get(1).revisionBefore()).isEqualTo(entries.get(0).revisionAfter());
        assertThat(entries.get(1).revisionAfter()).isEqualTo(first);
        assertThat(entries.get(2).outcome()).isEqualTo("refused:pack_version_conflict");
        assertThat(entries.get(2).revisionBefore()).isEqualTo(first);
        assertThat(entries.get(2).revisionAfter())
                .as("a refusal changed nothing")
                .isEqualTo(first);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.worktree()).isEqualTo("worktree-7");
            assertThat(entry.credential()).isEqualTo(Credential.NONE);
            assertThat(entry.packId()).isEqualTo("pack-x");
            assertThat(entry.operation()).isEqualTo("registerPackFromSource");
            assertThat(entry.sourceKind()).isEqualTo("https_archive");
            assertThat(entry.locator()).isEqualTo("https://packs.example.org/a.tgz");
            assertThat(entry.at()).isEqualTo(START);
        });
    }

    // ---- the harness -----------------------------------------------------------------------------------------------

    private static CountDownLatch holdTheRegistryLock(Rig rig) throws InterruptedException {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> rig.lock.exclusively(() -> {
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

    /** A clock a test moves. */
    static final class ManualClock extends Clock {
        private volatile Instant now = START;
        private volatile long pausingThread = -1;
        private volatile int pauseAtCall;
        private volatile CountDownLatch pausedEntered = new CountDownLatch(0);
        private volatile CountDownLatch pausedRelease = new CountDownLatch(0);
        private int callsOfPausing;

        /** The calling thread stops at its {@code call}-th reading of the clock until {@code release}. */
        void pauseThisThreadAtCall(int call, CountDownLatch entered, CountDownLatch release) {
            pauseAtCall = call;
            pausedEntered = entered;
            pausedRelease = release;
            callsOfPausing = 0;
            pausingThread = Thread.currentThread().threadId();
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            if (Thread.currentThread().threadId() == pausingThread && ++callsOfPausing == pauseAtCall) {
                pausedEntered.countDown();
                await(pausedRelease);
            }
            return now;
        }
    }

    /** A gate that holds the workers where a test asks. */
    static final class TestGate implements RegistrationJobs.JobGate {
        private volatile CountDownLatch before = new CountDownLatch(0);
        private volatile CountDownLatch running = new CountDownLatch(0);

        void holdBeforeRun() {
            before = new CountDownLatch(1);
        }

        void releaseBeforeRun() {
            before.countDown();
        }

        void holdWhileRunning() {
            running = new CountDownLatch(1);
        }

        void releaseWhileRunning() {
            running.countDown();
        }

        @Override
        public void beforeRun(String id) {
            await(before);
        }

        @Override
        public void whileRunning(String id) {
            await(running);
        }
    }

    private static final Origin ORIGIN_FOR_ORDER = new Origin(
            "worktree-7",
            Credential.NONE,
            "registerPackFromSource",
            "https_archive",
            "https://packs.example.org/a.tgz");

    /** An audit log that keeps what it is given. */
    static final class RecordingAudit implements AuditLog {
        private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
        private volatile CountDownLatch hold = new CountDownLatch(0);

        @Override
        public void record(Entry entry) {
            await(hold);
            entries.add(entry);
        }

        /** Makes every write wait for {@code release}, to pin what a reader may see in the meantime. */
        void holdWrites(CountDownLatch release) {
            hold = release;
        }

        List<Entry> entries() {
            return List.copyOf(entries);
        }
    }

    /** The jobs over a rig, the manual clock, the test gate and the recording audit. */
    final class Harness {
        final Rig rig;
        final ManualClock clock = new ManualClock();
        final TestGate gate = new TestGate();
        final RecordingAudit audit = new RecordingAudit();
        final Path staging;
        final RegistrationJobs jobs;
        volatile RegistrationJobs.StorageRoom room = RegistrationJobs.StorageRoom.UNBOUNDED;

        Harness(Path host) {
            this(host, Duration.ofSeconds(30));
        }

        Harness(Path host, Duration lockTimeout) {
            this(host, lockTimeout, () -> {});
        }

        Harness(Path host, Duration lockTimeout, Runnable midSwap) {
            this.rig = new Rig(host, lockTimeout);
            this.rig.midSwap = midSwap;
            this.staging = host.resolve("job-staging");
            this.jobs = new RegistrationJobs(new RegistrationJobs.Dependencies(
                    clock,
                    rig.service,
                    rig.writeStore,
                    audit,
                    gate,
                    () -> room.require(),
                    new FileStagingArea(staging)));
            opened.add(jobs);
        }

        Packs pack(String name, String version) {
            // One pack_id per name: two packs may not hold the same one.
            String id = "01M6R0WP14" + String.format("%016d", Math.abs((long) name.hashCode()));
            return Packs.of(id, name, version, "org").directive("ONE", "One");
        }

        /** A stager that writes the pack into the job's directory, as an extractor does. */
        Stager stage(Packs pack) {
            return directory -> {
                Path tree = directory.resolve("tree");
                pack.files().forEach((file, text) -> Rig.write(tree.resolve(file), text));
                return new RegistrationJobs.Staged(new StagedPack(tree), Map.of());
            };
        }

        Reservation start(String packId, Preconditions preconditions, Stager stager) {
            Reservation reservation = jobs.reserve(packId, preconditions, ORIGIN);
            reservation.start(stager);
            return reservation;
        }

        Status status(Reservation reservation) {
            return jobs.status(reservation.id()).orElseThrow();
        }

        Status awaitEnd(Reservation reservation) {
            await(() -> {
                State state = jobs.status(reservation.id()).map(Status::state).orElse(State.PENDING);
                return state == State.SUCCEEDED || state == State.REFUSED;
            });
            return status(reservation);
        }

        void await(BooleanSupplier condition) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!condition.getAsBoolean()) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("the condition did not hold within 20 s");
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted while waiting", interrupted);
                }
            }
        }

        List<String> stagingEntries() {
            try (var children = Files.exists(staging) ? Files.list(staging) : java.util.stream.Stream.<Path>empty()) {
                return children.map(child -> child.getFileName().toString()).toList();
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
    }
}
