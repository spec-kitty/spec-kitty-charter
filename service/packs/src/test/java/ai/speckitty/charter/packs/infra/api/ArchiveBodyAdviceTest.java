package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

/**
 * The archive body guard (NFR-004, T076): a body over 32 MiB is a 413 from its {@code Content-Length} before a byte is
 * read, and one without a length is counted while it streams; the media type must match the leading bytes (415); the
 * judged body carries its format on to the controller.
 */
class ArchiveBodyAdviceTest {

    private static final long LIMIT = 32L * 1024 * 1024;
    private static final byte[] GZIP = {0x1f, (byte) 0x8b, 8, 0, 1, 2, 3, 4};
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0};
    private static final byte[] EMPTY_ZIP = {'P', 'K', 5, 6, 0, 0, 0, 0};

    private final ArchiveBodyAdvice advice = new ArchiveBodyAdvice();

    @Test
    void aDeclaredLengthOverTheLimitIsA413BeforeAByteIsRead() {
        CountingStream body = new CountingStream(GZIP);
        HttpHeaders headers = headers("application/gzip");
        headers.setContentLength(LIMIT + 1);

        assertThatThrownBy(() -> advice.beforeBodyRead(message(body, headers), parameter(), Resource.class, null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        refused -> assertThat(refused.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(413)));
        assertThat(body.read.get()).as("the request stream was never touched").isZero();
    }

    @Test
    void aDeclaredLengthAtTheLimitIsHandedOn() throws IOException {
        HttpHeaders headers = headers("application/gzip");
        headers.setContentLength(LIMIT);

        HttpInputMessage judged = advice.beforeBodyRead(
                message(new ByteArrayInputStream(GZIP), headers), parameter(), Resource.class, null);

        assertThat(judged.getBody().readAllBytes()).isEqualTo(GZIP);
    }

    @Test
    void aBodyWithoutALengthIsCountedWhileItStreamsAndRefusedOneBytePastTheLimit() throws IOException {
        byte[] over = new byte[Math.toIntExact(LIMIT) + 4096];
        System.arraycopy(GZIP, 0, over, 0, GZIP.length);
        CountingStream body = new CountingStream(over);

        HttpInputMessage judged =
                advice.beforeBodyRead(message(body, headers("application/gzip")), parameter(), Resource.class, null);

        assertThatThrownBy(() -> judged.getBody().readAllBytes())
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        refused -> assertThat(refused.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(413)));
        assertThat(body.read.get())
                .as("the rest of the body is never read: the limit and the few bytes of the check")
                .isLessThanOrEqualTo(LIMIT + 1 + 8192);
    }

    @Test
    void aBodyOfExactlyTheLimitWithoutALengthIsHandedOn() throws IOException {
        byte[] exact = new byte[Math.toIntExact(LIMIT)];
        System.arraycopy(GZIP, 0, exact, 0, GZIP.length);

        HttpInputMessage judged = advice.beforeBodyRead(
                message(new ByteArrayInputStream(exact), headers("application/gzip")),
                parameter(),
                Resource.class,
                null);

        assertThat(judged.getBody().readAllBytes()).hasSize(exact.length);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"application/gzip", "application/x-gzip", "application/x-tar+gzip"})
    void aGzipTypeWithGzipBytesIsATarGz(String contentType) throws IOException {
        HttpInputMessage judged = advice.beforeBodyRead(
                message(new ByteArrayInputStream(GZIP), headers(contentType)), parameter(), Resource.class, null);

        assertThat(judged.getBody().readAllBytes())
                .as("the leading bytes are not consumed by the check")
                .isEqualTo(GZIP);
        Object handed = advice.afterBodyRead(new ByteArrayResource(GZIP), judged, parameter(), Resource.class, null);
        assertThat(handed)
                .isInstanceOfSatisfying(
                        ArchiveBody.class,
                        archive -> assertThat(archive.format()).isEqualTo(ArchiveFormat.TAR_GZ));
    }

    @Test
    void aZipTypeWithZipBytesIsAZipAndSoIsTheEmptyArchivesEndRecord() throws IOException {
        for (byte[] zip : new byte[][] {ZIP, EMPTY_ZIP}) {
            HttpInputMessage judged = advice.beforeBodyRead(
                    message(new ByteArrayInputStream(zip), headers("application/zip")),
                    parameter(),
                    Resource.class,
                    null);

            assertThat(judged.getBody().readAllBytes()).isEqualTo(zip);
            assertThat(advice.afterBodyRead(new ByteArrayResource(zip), judged, parameter(), Resource.class, null))
                    .isInstanceOfSatisfying(
                            ArchiveBody.class,
                            archive -> assertThat(archive.format()).isEqualTo(ArchiveFormat.ZIP));
        }
    }

    @ParameterizedTest(name = "{0} holding {1}")
    @CsvSource({
        "application/zip, gzip",
        "application/gzip, zip",
        "application/x-gzip, zip",
        "application/gzip, text",
        "application/zip, text",
        "application/gzip, empty",
        "application/zip, empty",
        "application/gzip, short",
    })
    void bytesThatDoNotMatchTheMediaTypeAreA415(String contentType, String holding) {
        byte[] bytes = switch (holding) {
            case "gzip" -> GZIP;
            case "zip" -> ZIP;
            case "text" -> "just some text, not an archive".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case "short" -> new byte[] {0x1f};
            default -> new byte[0];
        };

        assertThatThrownBy(() -> advice.beforeBodyRead(
                        message(new ByteArrayInputStream(bytes), headers(contentType)),
                        parameter(),
                        Resource.class,
                        null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        refused -> assertThat(refused.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(415)));
    }

    @Test
    void aMediaTypeOfNoneOfTheFourIsA415() {
        assertThatThrownBy(() -> advice.beforeBodyRead(
                        message(new ByteArrayInputStream(GZIP), headers("text/plain")),
                        parameter(),
                        Resource.class,
                        null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        refused -> assertThat(refused.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(415)));
        assertThatThrownBy(() -> advice.beforeBodyRead(
                        message(new ByteArrayInputStream(GZIP), new HttpHeaders()), parameter(), Resource.class, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void itGuardsTheArchiveParameterOfTheUploadAndNoOtherBody() throws NoSuchMethodException {
        Method upload = PackRegistrationController.class.getMethod(
                "registerPackFromArchive", String.class, Resource.class, String.class, String.class);
        Method source = PackRegistrationController.class.getMethod(
                "registerPackFromSource", ai.speckitty.charter.api.model.PackSource.class, String.class, String.class);

        assertThat(advice.supports(new MethodParameter(upload, 1), Resource.class, null))
                .isTrue();
        assertThat(advice.supports(new MethodParameter(upload, 0), String.class, null))
                .isFalse();
        assertThat(advice.supports(
                        new MethodParameter(source, 0), ai.speckitty.charter.api.model.PackSource.class, null))
                .isFalse();
    }

    private static MethodParameter parameter() {
        try {
            return new MethodParameter(
                    PackRegistrationController.class.getMethod(
                            "registerPackFromArchive", String.class, Resource.class, String.class, String.class),
                    1);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(missing);
        }
    }

    private static HttpHeaders headers(String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return headers;
    }

    private static HttpInputMessage message(InputStream body, HttpHeaders headers) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return body;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    /** A body that counts the bytes read from it. */
    private static final class CountingStream extends ByteArrayInputStream {
        private final AtomicLong read = new AtomicLong();

        CountingStream(byte[] bytes) {
            super(Arrays.copyOf(bytes, bytes.length));
        }

        @Override
        public synchronized int read() {
            int b = super.read();
            if (b >= 0) {
                read.incrementAndGet();
            }
            return b;
        }

        @Override
        public synchronized int read(byte[] into, int offset, int length) {
            int n = super.read(into, offset, length);
            if (n > 0) {
                read.addAndGet(n);
            }
            return n;
        }
    }
}
