package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.PackSource;
import ai.speckitty.charter.kernel.transport.BodyLimit;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Guards the JSON body of {@code POST /packs} before the message converter reads it (NFR-004): it is held to the shared
 * body limit ({@link BodyLimit#MAX_BYTES}, 1 MiB), by its declared {@code Content-Length} before a byte is read and
 * otherwise one byte past the limit, so an oversized body is never parsed nor held whole. The refusal is a plain
 * {@code 413} with no code, as the contract declares. A body that is not JSON is the converter's {@code 415}.
 */
@ControllerAdvice
public class PackSourceBodyAdvice extends RequestBodyAdviceAdapter {

    private static final String OPERATION = "registerPackFromSource";

    /** An advice over the source registration; a test constructs it directly. */
    public PackSourceBodyAdvice() {
        // Stateless.
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.getMethod() != null
                && OPERATION.equals(methodParameter.getMethod().getName())
                && PackSource.class.equals(targetType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        HttpHeaders headers = inputMessage.getHeaders();
        if (headers.getContentLength() > BodyLimit.MAX_BYTES) {
            throw tooLarge();
        }
        InputStream counted = new LimitedStream(inputMessage.getBody());
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

    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE, "The body is larger than " + BodyLimit.MAX_BYTES + " bytes.");
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
            int allowed = (int) Math.min(length, BodyLimit.MAX_BYTES + 1 - count);
            int n = in.read(into, offset, allowed);
            if (n > 0) {
                advance(n);
            }
            return n;
        }

        private void advance(int bytes) {
            count += bytes;
            if (count > BodyLimit.MAX_BYTES) {
                throw tooLarge();
            }
        }
    }
}
