package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.RetryLaterException;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.fixtures.HeldRoom;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.testfixtures.Await;
import ai.speckitty.charter.web.RefusalHandler;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * A slow upload holds no slot of the queue while its body arrives (#418, A4): as many uploads as the queue has slots may
 * be arriving at once (the next is refused at admission), each is accepted when its body is spooled, and a full queue
 * is still answered before a body is read. The design is ADR-0065 D8's note of 2026-11-08: the slot is taken after the
 * body is spooled.
 *
 * <p>An upload's place and a job's slot are counted apart, so an upload admitted while the queue had room can find it
 * full once its body is spooled, and is refused then (D8: the queue is judged again). Ten uploads make ten jobs and
 * need no wait. The test whose uploads make an eleventh job holds the two jobs that would race it with {@link HeldRoom}
 * and waits for a free slot with {@link Await}, never for a span of time (#677).
 */
class SlowUploadTest {

    private static final int SLOTS = RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT;
    private static final int BODY_BYTES = 64 * 1024;

    @TempDir
    Path staging;

    private final AtomicLong requestBytesRead = new AtomicLong();
    private final AtomicInteger stallsTimedOut = new AtomicInteger();
    private final HeldRoom room = new HeldRoom(RegistrationJobs.StorageRoom.UNBOUNDED);
    private RegistrationJobs jobs;
    private MockMvc mvc;
    private ExecutorService clients;

    @BeforeEach
    void build() {
        PackArchiveReader reader = (body, format, into) -> {
            throw new IllegalStateException("the test does not extract");
        };
        jobs = new RegistrationJobs(new RegistrationJobs.Dependencies(
                Clock.systemUTC(),
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                (AuditLog) entry -> {},
                room,
                new FileStagingArea(staging)));
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-slow", new ConsistentRead(new FileSystemPackSources(List::of)));
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                reader,
                new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                PackSourceIntake.none(),
                mock(PackRemovalController.class),
                mock(PackDocumentController.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(HttpConverters.serverConverters()
                        .toArray(new org.springframework.http.converter.HttpMessageConverter<?>[0]))
                .addInterceptors(new RegistrationAdmission(new RegistrationSwitch(true), jobs, info))
                .setControllerAdvice(new ArchiveBodyAdvice(), new RefusalHandler())
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();
        clients = Executors.newCachedThreadPool();
    }

    @AfterEach
    void close() throws InterruptedException {
        clients.shutdownNow();
        boolean stopped = clients.awaitTermination(Await.LIMIT.toSeconds(), TimeUnit.SECONDS);
        room.releaseAll();
        jobs.close();
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(stopped).as("the client threads stopped").isTrue();
            soft.assertThatCode(room::requireNoHoldTimedOut).doesNotThrowAnyException();
            soft.assertThat(stallsTimedOut.get())
                    .as("a stalled body went on unreleased after %d s", Await.LIMIT.toSeconds())
                    .isZero();
        });
    }

    @Test
    void asManyUploadsAsTheQueueHasSlotsMayBeArrivingAtOnceAndEachIsAcceptedWhenItsBodyIsSpooled() throws Exception {
        int uploads = SLOTS;
        List<CountDownLatch> release = new ArrayList<>();
        List<Future<MockHttpServletResponse>> responses = new ArrayList<>();
        CountDownLatch arriving = new CountDownLatch(uploads);
        for (int i = 0; i < uploads; i++) {
            CountDownLatch gate = new CountDownLatch(1);
            release.add(gate);
            responses.add(clients.submit(() -> mvc.perform(put("/api/v1/packs/acme")
                            .contentType("application/gzip")
                            .content(gzip())
                            .with(request -> stalling(request, arriving, gate)))
                    .andReturn()
                    .getResponse()));
        }
        assertThat(arriving.await(Await.LIMIT.toSeconds(), TimeUnit.SECONDS))
                .as("every upload is part way through its body")
                .isTrue();

        assertThat(responses)
                .as("none was refused for want of a slot while its body was still arriving")
                .noneMatch(Future::isDone);
        assertThat(jobs.retained()).as("no job was taken yet").isZero();
        assertThat(jobs.owned())
                .as("the spools in progress are owned, so the sweep leaves them")
                .hasSize(uploads);

        for (int i = 0; i < uploads; i++) {
            release.get(i).countDown();
            assertThat(statusOf(responses.get(i)))
                    .as("upload %d, accepted when its body was spooled", i)
                    .isEqualTo(202);
        }
    }

    @Test
    void theEleventhUploadIsRefusedAtAdmissionAndAFinishedUploadFreesItsPlace() throws Exception {
        List<CountDownLatch> release = new ArrayList<>();
        List<Future<MockHttpServletResponse>> responses = new ArrayList<>();
        CountDownLatch arriving = new CountDownLatch(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            CountDownLatch gate = new CountDownLatch(1);
            release.add(gate);
            responses.add(clients.submit(() -> mvc.perform(put("/api/v1/packs/acme")
                            .contentType("application/gzip")
                            .content(gzip())
                            .with(request -> stalling(request, arriving, gate)))
                    .andReturn()
                    .getResponse()));
        }
        assertThat(arriving.await(Await.LIMIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        long readBefore = requestBytesRead.get();
        int directoriesBefore = staged();
        byte[] body = gzip();

        MockHttpServletResponse refused = uploadCounting(body);

        assertThat(refused.getStatus())
                .as("ten uploads are open: the eleventh is refused")
                .isEqualTo(503);
        assertThat(refused.getHeader("Retry-After")).isNotNull();
        assertThat(requestBytesRead.get()).as("not a byte of its body was read").isEqualTo(readBefore);
        assertThat(staged()).as("and it staged nothing").isEqualTo(directoriesBefore);

        // The jobs of upload 0 and of the next upload are held running, so no slot frees until
        // releaseAll: the worst case of #677, on every run.
        room.hold();
        release.get(0).countDown();
        assertThat(statusOf(responses.get(0))).isEqualTo(202);
        MockHttpServletResponse next = uploadCounting(body);
        assertThat(next.getStatus())
                .as("the finished upload's place is free again")
                .isEqualTo(202);
        room.awaitHeld(2);
        room.stopHolding();

        for (int i = 1; i < SLOTS - 1; i++) {
            release.get(i).countDown();
            assertThat(statusOf(responses.get(i)))
                    .as("upload %d, accepted when its body was spooled", i)
                    .isEqualTo(202);
        }
        assertThat(queueHasRoom())
                .as("the two held jobs and eight more fill the queue: the last upload would make an eleventh job")
                .isFalse();

        room.releaseAll();
        Await.until("a slot of the queue to be free", this::queueHasRoom);
        release.get(SLOTS - 1).countDown();
        assertThat(statusOf(responses.get(SLOTS - 1)))
                .as("the last upload, accepted once a job gave its slot back")
                .isEqualTo(202);
    }

    /**
     * The controller's half of the spool-time refusal that {@code RegistrationJobsUploadTest} pins in the application:
     * an upload admitted while the queue had room, whose body is spooled into a full queue, is refused then with the
     * {@code 503} of a full queue, makes no job, and the controller discards its spool and gives its place back (#677).
     */
    @Test
    void anUploadSpooledIntoAFullQueueIsRefusedThenAndGivesBackItsSpoolAndItsPlace() throws Exception {
        CountDownLatch arriving = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        Future<MockHttpServletResponse> late = clients.submit(() -> mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(gzip())
                        .with(request -> stalling(request, arriving, gate)))
                .andReturn()
                .getResponse());
        assertThat(arriving.await(Await.LIMIT.toSeconds(), TimeUnit.SECONDS))
                .as("the late upload was admitted and is part way through its body")
                .isTrue();
        room.hold();
        byte[] body = gzip();
        for (int i = 0; i < SLOTS; i++) {
            MockHttpServletResponse accepted = uploadCounting(body);
            assertThat(accepted.getStatus()).as("upload %d fills the queue", i).isEqualTo(202);
        }
        room.awaitHeld(RegistrationJobs.RUNNING_LIMIT);
        assertThat(queueHasRoom()).as("two jobs run and eight wait").isFalse();
        int retainedBefore = jobs.retained();
        int stagedBefore = staged();
        assertThat(jobs.owned())
                .as("the ten jobs and the late upload hold places")
                .hasSize(SLOTS + 1);
        long readBefore = requestBytesRead.get();

        gate.countDown();
        MockHttpServletResponse refused = late.get(Await.LIMIT.toSeconds(), TimeUnit.SECONDS);

        assertThat(refused.getStatus())
                .as("the queue is judged again when the body is spooled")
                .isEqualTo(503);
        assertThat(refused.getHeader("Retry-After")).isNotNull();
        assertThat(requestBytesRead.get() - readBefore)
                .as("the rest of its body was read: the refusal came after the spool, not at admission")
                .isEqualTo(BODY_BYTES - WatchedBody.ANNOUNCE_AFTER);
        assertThat(jobs.retained()).as("it made no job").isEqualTo(retainedBefore);
        assertThat(staged()).as("its spool is gone").isEqualTo(stagedBefore - 1);
        assertThat(jobs.owned())
                .as("its place among the open uploads is given back; the ten jobs keep theirs")
                .hasSize(SLOTS);
    }

    @Test
    void aFullQueueIsRefusedBeforeTheBodyIsReadAndLeavesNothingStaged() throws Exception {
        List<RegistrationJobs.Reservation> held = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            held.add(jobs.reserve("pack-" + i, Preconditions.none(), origin(), jobs.openUpload(1), 1));
        }
        assertThat(held).hasSize(SLOTS);
        int directoriesBefore = staged();
        byte[] body = gzip();

        MockHttpServletResponse response = uploadCounting(body);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(requestBytesRead.get()).as("not a byte of the body was read").isZero();
        assertThat(staged()).isEqualTo(directoriesBefore);
    }

    /** The status a stalled upload was answered, once its body went on. */
    private static int statusOf(Future<MockHttpServletResponse> response) throws Exception {
        return response.get(Await.LIMIT.toSeconds(), TimeUnit.SECONDS).getStatus();
    }

    /** An upload whose whole body arrives at once, its bytes counted. */
    private MockHttpServletResponse uploadCounting(byte[] body) throws Exception {
        return mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(body)
                        .with(request -> counting(request, body)))
                .andReturn()
                .getResponse();
    }

    /** Whether admission would take a new upload now: the queue has a free slot and the service is open. Takes none. */
    private boolean queueHasRoom() {
        try {
            jobs.precheck("acme", Preconditions.none());
            return true;
        } catch (RetryLaterException full) {
            return false;
        }
    }

    private static RegistrationJobs.Origin origin() {
        return new RegistrationJobs.Origin(
                "worktree-slow",
                AuditLog.Credential.NONE,
                "registerPackFromArchive",
                "archive",
                "",
                ai.speckitty.charter.packs.application.PackRegistrationService.Mode.COMMIT);
    }

    private int staged() throws IOException {
        try (Stream<Path> entries = Files.list(staging)) {
            return (int) entries.count();
        }
    }

    private static byte[] gzip() {
        byte[] body = new byte[BODY_BYTES];
        body[0] = 0x1f;
        body[1] = (byte) 0x8b;
        return body;
    }

    private MockHttpServletRequest counting(MockHttpServletRequest from, byte[] body) {
        return replacing(
                from, new WatchedBody(new ByteArrayInputStream(body), requestBytesRead, stallsTimedOut, null, null));
    }

    private MockHttpServletRequest stalling(
            MockHttpServletRequest from, CountDownLatch arriving, CountDownLatch release) {
        return replacing(
                from,
                new WatchedBody(new ByteArrayInputStream(gzip()), requestBytesRead, stallsTimedOut, arriving, release));
    }

    private static MockHttpServletRequest replacing(MockHttpServletRequest from, ServletInputStream stream) {
        long length = from.getContentLengthLong();
        MockHttpServletRequest request =
                new MockHttpServletRequest(from.getServletContext(), from.getMethod(), from.getRequestURI()) {
                    @Override
                    public ServletInputStream getInputStream() {
                        return stream;
                    }

                    @Override
                    public int getContentLength() {
                        return (int) length;
                    }

                    @Override
                    public long getContentLengthLong() {
                        return length;
                    }
                };
        Collections.list(from.getHeaderNames()).forEach(name -> request.addHeader(name, from.getHeader(name)));
        request.setContentType(from.getContentType());
        return request;
    }

    /**
     * A body that counts what is read and, when asked, announces that it is part way and waits to be released. A wait
     * that runs out its {@link Await#LIMIT} unreleased is counted, and the teardown fails on it: the body went on in a
     * state the test never meant.
     */
    private static final class WatchedBody extends ServletInputStream {
        private static final int ANNOUNCE_AFTER = 4096;

        private final InputStream source;
        private final AtomicLong read;
        private final AtomicInteger timedOut;
        private final CountDownLatch announce;
        private final CountDownLatch release;
        private long delivered;
        private boolean announced;

        WatchedBody(
                InputStream source,
                AtomicLong read,
                AtomicInteger timedOut,
                CountDownLatch announce,
                CountDownLatch release) {
            this.source = source;
            this.read = read;
            this.timedOut = timedOut;
            this.announce = announce;
            this.release = release;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] into, int offset, int length) throws IOException {
            if (release != null && delivered >= ANNOUNCE_AFTER && !announced) {
                announced = true;
                announce.countDown();
                try {
                    if (!release.await(Await.LIMIT.toSeconds(), TimeUnit.SECONDS)) {
                        timedOut.incrementAndGet();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", interrupted);
                }
            }
            int n = source.read(
                    into,
                    offset,
                    release != null && !announced ? Math.min(length, ANNOUNCE_AFTER - (int) delivered) : length);
            if (n > 0) {
                delivered += n;
                read.addAndGet(n);
            }
            return n;
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
