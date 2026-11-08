package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.kernel.transport.RetryLaterException;
import ai.speckitty.charter.packs.application.AuditLog.Credential;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.RegistrationJobs.Origin;
import ai.speckitty.charter.packs.application.RegistrationJobs.Reservation;
import ai.speckitty.charter.packs.application.RegistrationJobs.State;
import ai.speckitty.charter.packs.application.RegistrationJobs.Status;
import ai.speckitty.charter.packs.application.RegistrationJobsTest.ManualClock;
import ai.speckitty.charter.packs.application.RegistrationJobsTest.RecordingAudit;
import ai.speckitty.charter.packs.application.RegistrationJobsTest.TestGate;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The upload path of the registration jobs (#418, A4): a body that is still arriving holds a directory in staging and no
 * slot of the queue, at most ten (the queue's size) are open at once, the slot is taken once the body is spooled, and a job the deadline ended before it was started is
 * answered by its own end, never by a failure of the request that was about to start it.
 */
class RegistrationJobsUploadTest {

    private static final Origin ORIGIN =
            new Origin("worktree-1", Credential.WORKTREE_TOKEN, "registerPackFromArchive", "archive", "");
    private static final int SLOTS = RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT;

    @TempDir
    Path host;

    private final ManualClock clock = new ManualClock();
    private final TestGate gate = new TestGate();
    private final RecordingAudit audit = new RecordingAudit();
    private final List<Long> roomAsked = new CopyOnWriteArrayList<>();
    private final List<Long> admissionAsked = new CopyOnWriteArrayList<>();
    private final List<Long> openBytesAsked = new CopyOnWriteArrayList<>();
    private final AtomicReference<CharterRefusal> full = new AtomicReference<>();
    private RegistrationJobs jobs;

    @BeforeEach
    void build() {
        gate.holdBeforeRun();
        jobs = new RegistrationJobs(new RegistrationJobs.Dependencies(
                clock,
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                audit,
                gate,
                new RegistrationJobs.StorageRoom() {
                    @Override
                    public void require() {
                        roomAsked.add(UNKNOWN_BYTES);
                    }

                    @Override
                    public void require(long archiveBytes) {
                        roomAsked.add(archiveBytes);
                    }

                    @Override
                    public void require(long archiveBytes, long openUploadBytes) {
                        CharterRefusal refusal = full.get();
                        if (refusal != null) {
                            throw refusal;
                        }
                        admissionAsked.add(archiveBytes);
                        openBytesAsked.add(openUploadBytes);
                    }
                },
                new FileStagingArea(host.resolve("staging"))));
    }

    @AfterEach
    void close() {
        gate.releaseBeforeRun();
        jobs.close();
    }

    @Test
    void atMostTenUploadsAreOpenAtOnceAndTheEleventhIsRefusedAsAFullQueueIsAndNoneHoldsASlot() {
        List<RegistrationJobs.Upload> uploads = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            uploads.add(jobs.openUpload(1));
        }

        assertThatThrownBy(() -> jobs.openUpload(1))
                .as("the eleventh open upload is refused at admission, with a Retry-After like the full queue's")
                .isInstanceOfSatisfying(
                        RetryLaterException.class, refused -> assertThat(refused.retryAfterSeconds()).isPositive());
        assertThat(admissionAsked).as("the room is not asked for an upload that has no place").hasSize(SLOTS);
        assertThat(jobs.owned()).as("the refused upload left no directory").hasSize(SLOTS);
        assertThatCodeNothing(() -> jobs.precheck("acme", Preconditions.none()));
        assertThat(jobs.retained()).as("open uploads hold no queue slot").isZero();
        Reservation reservation = jobs.reserve("acme", Preconditions.none(), ORIGIN, uploads.get(0), 10);
        assertThat(reservation.id()).isEqualTo(uploads.get(0).id());
    }

    @Test
    void aDiscardedOrReservedUploadFreesItsPlaceForTheNextOne() {
        List<RegistrationJobs.Upload> uploads = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            uploads.add(jobs.openUpload(1));
        }
        assertThatThrownBy(() -> jobs.openUpload(1)).isInstanceOf(RetryLaterException.class);

        uploads.get(0).discard();
        RegistrationJobs.Upload second = jobs.openUpload(1);
        assertThatThrownBy(() -> jobs.openUpload(1))
                .as("the place of the discarded upload was taken, no more")
                .isInstanceOf(RetryLaterException.class);

        Reservation reserved = jobs.reserve("acme", Preconditions.none(), ORIGIN, uploads.get(1), 1);
        RegistrationJobs.Upload third = jobs.openUpload(1);
        assertThatThrownBy(() -> jobs.openUpload(1)).isInstanceOf(RetryLaterException.class);

        reserved.abandon();
        assertThatThrownBy(() -> jobs.openUpload(1))
                .as("the place was freed when the body was spooled, not when the job ended")
                .isInstanceOf(RetryLaterException.class);
        second.discard();
        third.discard();
        assertThatCodeNothing(() -> jobs.openUpload(1));
    }

    @Test
    void anUploadTheRoomRefusedHoldsNeitherAPlaceNorADirectory() {
        full.set(new CharterRefusal(RefusalCode.PACK_STORAGE_FULL, "full"));
        for (int i = 0; i < 3 * SLOTS; i++) {
            assertThatThrownBy(() -> jobs.openUpload(1)).isInstanceOf(CharterRefusal.class);
        }
        full.set(null);

        assertThat(jobs.owned()).isEmpty();
        for (int i = 0; i < SLOTS; i++) {
            jobs.openUpload(1);
        }
        assertThatThrownBy(() -> jobs.openUpload(1)).isInstanceOf(RetryLaterException.class);
    }

    @Test
    void theFreeSpaceCheckAtAdmissionSubtractsTheBytesOfTheUploadsAlreadyOpen() {
        long bound = RegistrationJobs.StorageRoom.ARCHIVE_BOUND_BYTES;
        jobs.openUpload(100);
        RegistrationJobs.Upload second = jobs.openUpload(200);
        jobs.openUpload(RegistrationJobs.StorageRoom.UNKNOWN_BYTES);
        second.discard();
        jobs.openUpload(5);

        assertThat(admissionAsked).containsExactly(100L, 200L, RegistrationJobs.StorageRoom.UNKNOWN_BYTES, 5L);
        assertThat(openBytesAsked)
                .as("the declared bytes of the open uploads, the bound for the one of unknown length")
                .containsExactly(0L, 100L, 300L, 100L + bound);
    }

    @Test
    void theSlotIsTakenWhenTheBodyIsSpooledAndTheLimitsAreTheSame() {
        List<Reservation> held = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            held.add(jobs.reserve("pack-" + i, Preconditions.none(), ORIGIN, jobs.openUpload(1), 1));
        }
        assertThat(held).hasSize(SLOTS);
        RegistrationJobs.Upload late = jobs.openUpload(1);

        assertThatThrownBy(() -> jobs.precheck("acme", Preconditions.none()))
                .as("a full queue is known before the body is read")
                .isInstanceOf(RetryLaterException.class);
        assertThatThrownBy(() -> jobs.reserve("acme", Preconditions.none(), ORIGIN, late, 1))
                .as("and again when the body is spooled")
                .isInstanceOf(RetryLaterException.class);
        assertThat(late.directory())
                .as("the refused upload's spool is the caller's to discard")
                .exists();
        late.discard();
        assertThat(late.directory()).doesNotExist();
    }

    @Test
    void aStaleIfMatchIsRefusedBeforeTheBodyIsReadAndAgainWhenItIsSpooled() {
        Preconditions stale = Preconditions.ifMatch("\"sha256:" + "ab".repeat(32) + "\"");

        assertThatThrownBy(() -> jobs.precheck("acme", stale)).isInstanceOf(PreconditionException.class);
        RegistrationJobs.Upload upload = jobs.openUpload(1);
        assertThatThrownBy(() -> jobs.reserve("acme", stale, ORIGIN, upload, 1))
                .isInstanceOf(PreconditionException.class);
        upload.discard();
    }

    @Test
    void anUploadInProgressIsOwnedSoTheSweepNeverDeletesItAndDiscardingItEndsThat() {
        RegistrationJobs.Upload upload = jobs.openUpload(1);

        assertThat(jobs.owned()).contains(upload.directory());
        upload.discard();
        assertThat(jobs.owned()).doesNotContain(upload.directory());
        upload.discard();
    }

    @Test
    void theReservationAdoptsTheUploadsDirectoryAndDiscardingTheAdoptedUploadKeepsIt() throws IOException {
        RegistrationJobs.Upload upload = jobs.openUpload(1);
        Files.writeString(upload.directory().resolve("archive"), "spooled");

        Reservation reservation = jobs.reserve("acme", Preconditions.none(), ORIGIN, upload, 7);
        upload.discard();

        assertThat(reservation.directory()).isEqualTo(upload.directory());
        assertThat(upload.directory().resolve("archive")).hasContent("spooled");
        assertThat(jobs.owned()).containsExactly(upload.directory());
        reservation.abandon();
        assertThat(upload.directory())
                .as("abandoning the reservation gives the directory back")
                .doesNotExist();
    }

    @Test
    void theJobChecksTheRoomForTheSizeThatWasSpooled() throws Exception {
        gate.releaseBeforeRun();
        RegistrationJobs.Upload upload = jobs.openUpload(1);
        Reservation reservation = jobs.reserve("acme", Preconditions.none(), ORIGIN, upload, 1234);

        reservation.start(directory -> {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "the test ends the job at once");
        });
        awaitEnd(reservation);

        assertThat(roomAsked).containsExactly(1234L);
    }

    @Test
    void aJobTheDeadlineEndedBeforeItWasStartedAnswersItsTimeoutAndIsNotAFailureOfTheRequest() {
        RegistrationJobs.Upload upload = jobs.openUpload(1);
        Reservation reservation = jobs.reserve("acme", Preconditions.none(), ORIGIN, upload, 1);
        clock.advance(RegistrationJobs.DEADLINE.plus(Duration.ofSeconds(1)));
        jobs.expireOverdue();
        AtomicBoolean ran = new AtomicBoolean();

        boolean started = reservation.start(directory -> {
            ran.set(true);
            throw new IllegalStateException("an ended job is never run");
        });

        assertThat(started).as("the job had ended: nothing was started").isFalse();
        Status status = reservation.status();
        assertThat(status.state()).isEqualTo(State.REFUSED);
        assertThat(status.ending()).isInstanceOfSatisfying(RegistrationJobs.Refused.class, refused -> {
            assertThat(refused.kind()).isEqualTo(RegistrationJobs.Kind.CODED);
            assertThat(refused.refusal().code()).isEqualTo(RefusalCode.REGISTRATION_TIMED_OUT);
        });
        assertThat(audit.entries())
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("refused:registration_timed_out"));
        assertThat(ran).isFalse();
        assertThat(upload.directory()).as("its directory went with it").doesNotExist();
        assertThatCodeNothing(() -> jobs.precheck("acme", Preconditions.none()));
    }

    @Test
    void startingAJobThatIsRunningIsStillAnError() throws Exception {
        gate.releaseBeforeRun();
        RegistrationJobs.Upload upload = jobs.openUpload(1);
        Reservation reservation = jobs.reserve("acme", Preconditions.none(), ORIGIN, upload, 1);
        reservation.start(directory -> {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "ends");
        });

        assertThatThrownBy(() -> reservation.start(directory -> {
                    throw new AssertionError("not run twice");
                }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aServiceThatIsStoppingTakesNoUpload() {
        jobs.close();

        assertThatThrownBy(() -> jobs.openUpload(1)).isInstanceOf(RetryLaterException.class);
        assertThatThrownBy(() -> jobs.precheck("acme", Preconditions.none())).isInstanceOf(RetryLaterException.class);
    }

    private static void assertThatCodeNothing(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        org.assertj.core.api.Assertions.assertThatCode(call).doesNotThrowAnyException();
    }

    private static void awaitEnd(Reservation reservation) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            State state = reservation.status().state();
            if (state == State.REFUSED || state == State.SUCCEEDED) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("the job did not end");
    }
}
