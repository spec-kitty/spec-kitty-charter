package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
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
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * The upload body is streamed to the job's spool file and never held in the heap (#418, A3): when the controller is
 * entered, the request stream has been read for the leading bytes of the media-type check and no further; the bytes that
 * reach the extractor are the bytes sent; and the size limits still refuse before, and while, the body is read. The
 * converters are the ones the service registers ({@link ArchiveBodyConverters}).
 */
class StreamedUploadTest {

    private static final int BODY_BYTES = 3 * 1024 * 1024;

    @TempDir
    Path staging;

    private final AtomicLong requestBytesRead = new AtomicLong();
    private final List<Long> readWhenTheControllerWasEntered = new CopyOnWriteArrayList<>();
    private final List<Object> boundBodies = new CopyOnWriteArrayList<>();
    private final List<byte[]> extracted = new CopyOnWriteArrayList<>();
    private RegistrationJobs jobs;
    private MockMvc mvc;

    @BeforeEach
    void build() {
        PackArchiveReader reader = (body, format, into) -> {
            try {
                extracted.add(body.readAllBytes());
            } catch (IOException unreadable) {
                throw new IllegalStateException(unreadable);
            }
            throw new IllegalStateException("the test only reads what was spooled, then ends the job");
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
                new ServiceInfoQuery("worktree-streamed", new ConsistentRead(new FileSystemPackSources(List::of)));
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                reader,
                new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                PackSourceIntake.none(),
                mock(PackRemovalController.class),
                mock(PackDocumentController.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(serviceConverters().toArray(new HttpMessageConverter<?>[0]))
                .addInterceptors(new RegistrationAdmission(new RegistrationSwitch(true), jobs, info))
                .setControllerAdvice(new ArchiveBodyAdvice(), new BoundBodyRecorder(), new RefusalHandler())
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();
    }

    @AfterEach
    void close() {
        jobs.close();
    }

    /** The converters of a Spring MVC application, as the service adjusts them. */
    private static List<HttpMessageConverter<?>> serviceConverters() {
        HttpMessageConverters.ServerBuilder builder =
                HttpMessageConverters.forServer().registerDefaults();
        new ArchiveBodyConverters().configureMessageConverters(builder);
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        builder.build().forEach(converters::add);
        return converters;
    }

    @Test
    void theControllerIsEnteredWithTheBodyUnreadBeyondTheLeadAndTheBytesReachTheExtractorWhole() throws Exception {
        byte[] body = gzip(BODY_BYTES);

        mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(body)
                        .with(request -> counting(request, body)))
                .andExpect(status().isAccepted());

        assertThat(readWhenTheControllerWasEntered)
                .as("request bytes read when the controller was entered: the lead of the media-type check only")
                .singleElement()
                .satisfies(read -> assertThat(read).isLessThanOrEqualTo(8192L));
        assertThat(boundBodies)
                .singleElement()
                .satisfies(bound ->
                        assertThat(bound).isNotInstanceOf(org.springframework.core.io.ByteArrayResource.class));
        awaitExtraction();
        assertThat(extracted).singleElement().isEqualTo(body);
    }

    @Test
    void aDeclaredLengthOverTheLimitIsRefused413BeforeAByteIsRead() throws Exception {
        byte[] body = gzip(16);

        mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(body)
                        .header("Content-Length", ArchiveBodyAdvice.MAX_BODY_BYTES + 1)
                        .with(request -> counting(request, body)))
                .andExpect(status().is(413));

        assertThat(requestBytesRead.get())
                .as("the request stream was never touched")
                .isZero();
        assertThat(stagingEntries()).as("and nothing is staged").isEmpty();
    }

    @Test
    void aBodyThatBreaksOffWhileItStreamsIsA400AndLeavesNothingInStaging() throws Exception {
        byte[] body = gzip(BODY_BYTES);

        mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(body)
                        .with(request -> breakingOff(request, body, BODY_BYTES / 2)))
                .andExpect(status().isBadRequest());

        assertThat(jobs.retained()).as("no job was taken").isZero();
        assertThat(jobs.owned()).isEmpty();
        assertThat(stagingEntries())
                .as("the spool of the broken upload is gone")
                .isEmpty();
    }

    private void awaitExtraction() throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (extracted.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private List<Path> stagingEntries() throws IOException {
        if (!Files.exists(staging)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(staging)) {
            return entries.toList();
        }
    }

    private static byte[] gzip(int length) {
        byte[] body = new byte[length];
        body[0] = 0x1f;
        body[1] = (byte) 0x8b;
        for (int i = 2; i < length; i++) {
            body[i] = (byte) (i * 31);
        }
        return body;
    }

    private MockHttpServletRequest counting(MockHttpServletRequest from, byte[] body) {
        return replacing(from, new CountingStream(new ByteArrayInputStream(body), requestBytesRead, -1));
    }

    private MockHttpServletRequest breakingOff(MockHttpServletRequest from, byte[] body, int after) {
        return replacing(from, new CountingStream(new ByteArrayInputStream(body), requestBytesRead, after));
    }

    /** The same request over a body stream the test watches. */
    private static MockHttpServletRequest replacing(MockHttpServletRequest from, ServletInputStream stream) {
        String declared = from.getHeader("Content-Length");
        long length = declared != null ? Long.parseLong(declared) : from.getContentLengthLong();
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

    /** A request body that counts what is read and can fail part way. */
    private static final class CountingStream extends ServletInputStream {
        private final InputStream source;
        private final AtomicLong read;
        private final long breakAfter;
        private long delivered;

        CountingStream(InputStream source, AtomicLong read, long breakAfter) {
            this.source = source;
            this.read = read;
            this.breakAfter = breakAfter;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] into, int offset, int length) throws IOException {
            if (breakAfter >= 0 && delivered >= breakAfter) {
                throw new IOException("the client went away");
            }
            int n = source.read(into, offset, length);
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

    /** Sees the body as the controller is about to get it, after the archive guard has judged it. */
    @org.springframework.web.bind.annotation.ControllerAdvice
    private final class BoundBodyRecorder extends RequestBodyAdviceAdapter {
        @Override
        public boolean supports(
                MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
            return parameter.getMethod() != null
                    && "registerPackFromArchive".equals(parameter.getMethod().getName());
        }

        @Override
        public Object afterBodyRead(
                Object body,
                HttpInputMessage inputMessage,
                MethodParameter parameter,
                Type targetType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            readWhenTheControllerWasEntered.add(requestBytesRead.get());
            boundBodies.add(body instanceof ArchiveBody archive ? archive.source() : body);
            return body;
        }
    }
}
