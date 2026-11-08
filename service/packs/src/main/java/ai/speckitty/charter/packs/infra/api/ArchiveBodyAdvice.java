package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Type;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Guards the archive body of {@code PUT /packs/{packId}} before the message converter reads it (NFR-004, T076):
 *
 * <ul>
 *   <li><b>Bounded.</b> A declared {@code Content-Length} over 32 MiB is a {@code 413} before a byte is read. A body with
 *       no length, or a false one, is wrapped in a stream that counts what the converter reads and refuses one byte past
 *       the limit, so Spring never buffers more than the limit.
 *   <li><b>Matching.</b> The {@code Content-Type} must be one of the four archive types and the leading bytes must be
 *       the format's own ({@code 1f 8b} for gzip, {@code PK 03 04} or the empty archive's {@code PK 05 06} for zip), else
 *       {@code 415}. The bytes the check reads are handed on.
 * </ul>
 *
 * Both refusals are plain problems with no code ({@code ResponseStatusException}, rendered by the shared
 * {@code ProblemHandler}). The judged body leaves as an {@link ArchiveBody} that names its format.
 */
@ControllerAdvice
public class ArchiveBodyAdvice extends RequestBodyAdviceAdapter {

    /** The most body bytes an archive upload may hold, 32 MiB. */
    public static final long MAX_BODY_BYTES = 32L * 1024 * 1024;

    private static final String UPLOAD = "registerPackFromArchive";
    private static final int LEAD_BYTES = 4;
    private static final int GZIP_FIRST = 0x1f;
    private static final int GZIP_SECOND = 0x8b;
    private static final List<MediaType> GZIP_TYPES = List.of(
            MediaType.parseMediaType("application/gzip"),
            MediaType.parseMediaType("application/x-gzip"),
            MediaType.parseMediaType("application/x-tar+gzip"));
    private static final MediaType ZIP_TYPE = MediaType.parseMediaType("application/zip");

    /** An advice over the archive upload; a test constructs it directly. */
    public ArchiveBodyAdvice() {
        // Stateless.
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.getMethod() != null
                && UPLOAD.equals(methodParameter.getMethod().getName())
                && Resource.class.isAssignableFrom(methodParameter.getParameterType());
    }

    /**
     * Refuses a declared length over the limit, before the body is touched.
     *
     * @param headers the request headers
     * @throws ResponseStatusException {@code 413} when the declared length is over 32 MiB
     */
    static void requireDeclaredLengthWithinLimit(HttpHeaders headers) {
        if (headers.getContentLength() > MAX_BODY_BYTES) {
            throw tooLarge();
        }
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        HttpHeaders headers = inputMessage.getHeaders();
        requireDeclaredLengthWithinLimit(headers);
        ArchiveFormat format = formatOf(headers);
        PushbackInputStream body = new PushbackInputStream(inputMessage.getBody(), LEAD_BYTES);
        byte[] lead = body.readNBytes(LEAD_BYTES);
        body.unread(lead);
        if (!matches(format, lead)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The leading bytes of the body are not those of its media type.");
        }
        InputStream counted = new LimitedStream(body);
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return counted;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return new ArchiveBody(formatOf(inputMessage.getHeaders()), (Resource) body);
    }

    private static ArchiveFormat formatOf(HttpHeaders headers) {
        @Nullable MediaType type = headers.getContentType();
        if (type != null && GZIP_TYPES.stream().anyMatch(gzip -> gzip.equalsTypeAndSubtype(type))) {
            return ArchiveFormat.TAR_GZ;
        }
        if (type != null && ZIP_TYPE.equalsTypeAndSubtype(type)) {
            return ArchiveFormat.ZIP;
        }
        throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "The body is none of application/gzip, application/zip or their aliases.");
    }

    private static boolean matches(ArchiveFormat format, byte[] lead) {
        if (format == ArchiveFormat.TAR_GZ) {
            return lead.length >= 2 && (lead[0] & 0xff) == GZIP_FIRST && (lead[1] & 0xff) == GZIP_SECOND;
        }
        return lead.length >= LEAD_BYTES
                && lead[0] == 'P'
                && lead[1] == 'K'
                && ((lead[2] == 3 && lead[3] == 4) || (lead[2] == 5 && lead[3] == 6));
    }

    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE, "The body is larger than " + MAX_BODY_BYTES + " bytes.");
    }

    /** Counts what is read and refuses the byte after the limit; it never asks the source for more than that byte. */
    private static final class LimitedStream extends FilterInputStream {
        private long count;

        LimitedStream(InputStream source) {
            super(source);
        }

        @Override
        public int read() throws IOException {
            int next = in.read();
            if (next >= 0) {
                advance(1);
            }
            return next;
        }

        @Override
        public int read(byte[] into, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int allowed = (int) Math.min(length, MAX_BODY_BYTES + 1 - count);
            int n = in.read(into, offset, allowed);
            if (n > 0) {
                advance(n);
            }
            return n;
        }

        private void advance(int bytes) {
            count += bytes;
            if (count > MAX_BODY_BYTES) {
                throw tooLarge();
            }
        }
    }
}
