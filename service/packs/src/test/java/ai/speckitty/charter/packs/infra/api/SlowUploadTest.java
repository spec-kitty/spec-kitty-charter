package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
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
 * be arriving at once (the next is refused at admission), each is accepted when its body is spooled, and a full queue is still answered before a body is
 * read. The design is ADR-0065 D8's note of 2026-11-08: the slot is taken after the body is spooled.
 */
class SlowUploadTest {

    private static final int SLOTS = RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT;
    private static final int BODY_BYTES = 64 * 1024;

    @TempDir
    Path staging;

    private final AtomicLong requestBytesRead = new AtomicLong();
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
                RegistrationJobs.JobGate.OPEN,
                RegistrationJobs.StorageRoom.UNBOUNDED,
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
    void close() {
        clients.shutdownNow();
        jobs.close();
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
        assertThat(arriving.await(20, TimeUnit.SECONDS))
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
            assertThat(responses.get(i).get(20, TimeUnit.SECONDS).getStatus())
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
        assertThat(arriving.await(20, TimeUnit.SECONDS)).isTrue();
        long readBefore = requestBytesRead.get();
        int directoriesBefore = staged();
        byte[] body = gzip();

        MockHttpServletResponse refused = mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(body)
                        .with(request -> counting(request, body)))
                .andReturn()
                .getResponse();

        assertThat(refused.getStatus())
                .as("ten uploads are open: the eleventh is refused")
                .isEqualTo(503);
        assertThat(refused.getHeader("Retry-After")).isNotNull();
        assertThat(requestBytesRead.get()).as("not a byte of its body was read").isEqualTo(readBefore);
        assertThat(staged()).as("and it staged nothing").isEqualTo(directoriesBefore);

        release.get(0).countDown();
        assertThat(responses.get(0).get(20, TimeUnit.SECONDS).getStatus()).isEqualTo(202);
        MockHttpServletResponse next = mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(body)
                        .with(request -> counting(request, body)))
                .andReturn()
                .getResponse();
        assertThat(next.getStatus())
                .as("the finished upload's place is free again")
                .isEqualTo(202);

        for (int i = 1; i < SLOTS; i++) {
            release.get(i).countDown();
            assertThat(responses.get(i).get(20, TimeUnit.SECONDS).getStatus()).isEqualTo(202);
        }
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

        MockHttpServletResponse response = mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(body)
                        .with(request -> counting(request, body)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(requestBytesRead.get()).as("not a byte of the body was read").isZero();
        assertThat(staged()).isEqualTo(directoriesBefore);
    }

    private static RegistrationJobs.Origin origin() {
        return new RegistrationJobs.Origin(
                "worktree-slow", AuditLog.Credential.NONE, "registerPackFromArchive", "archive", "");
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
        return replacing(from, new WatchedBody(new ByteArrayInputStream(body), requestBytesRead, null, null));
    }

    private MockHttpServletRequest stalling(
            MockHttpServletRequest from, CountDownLatch arriving, CountDownLatch release) {
        return replacing(from, new WatchedBody(new ByteArrayInputStream(gzip()), requestBytesRead, arriving, release));
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

    /** A body that counts what is read and, when asked, announces that it is part way and waits to be released. */
    private static final class WatchedBody extends ServletInputStream {
        private static final int ANNOUNCE_AFTER = 4096;

        private final InputStream source;
        private final AtomicLong read;
        private final CountDownLatch announce;
        private final CountDownLatch release;
        private long delivered;
        private boolean announced;

        WatchedBody(InputStream source, AtomicLong read, CountDownLatch announce, CountDownLatch release) {
            this.source = source;
            this.read = read;
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
                    release.await(30, TimeUnit.SECONDS);
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
