package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.util.unit.DataSize;

/**
 * The toolguide registration body guard (S5, A8): a body over the limit is refused by its {@code Content-Length} and,
 * without one, while it is read; an explicit {@code null} guide field is refused; any other body passes unchanged.
 */
class ToolguideRegistrationBodyAdviceTest {

    /** A guide cap of 16 bytes: a body limit of 96 bytes plus 1 MiB. */
    private final ToolguideRegistrationBodyAdvice advice = new ToolguideRegistrationBodyAdvice(DataSize.ofBytes(16));

    private static final long LIMIT = 6 * 16 + 1024 * 1024;

    @Test
    void theBodyLimitIsSixBytesPerGuideByteAndAMebibyteMore() {
        assertThat(ToolguideRegistrationBodyAdvice.bodyLimit(16)).isEqualTo(LIMIT);
        assertThat(ToolguideRegistrationBodyAdvice.bodyLimit(1024 * 1024)).isEqualTo(7L * 1024 * 1024);
    }

    @Test
    void aDeclaredLengthOverTheLimitIsRefusedBeforeAByteIsRead() {
        CountingStream body = new CountingStream(new byte[0]);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(LIMIT + 1);

        assertThatThrownBy(() -> read(message(body, headers))).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
            assertThat(r.getMessage()).contains(String.valueOf(LIMIT));
        });
        assertThat(body.read).isZero();
    }

    @Test
    void anUndeclaredBodyOverTheLimitIsRefusedOneBytePastIt() {
        byte[] large = new byte[Math.toIntExact(LIMIT) + 4096];
        Arrays.fill(large, (byte) ' ');
        CountingStream body = new CountingStream(large);

        assertThatThrownBy(() -> read(message(body, new HttpHeaders())))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
        assertThat(body.read).isEqualTo(LIMIT + 1);
    }

    @Test
    void aBodyAtTheLimitIsHandedOnUnchanged() throws IOException {
        byte[] json = "{\"guide\":\"## Overview\"}".getBytes(StandardCharsets.UTF_8);
        byte[] exact = Arrays.copyOf(json, Math.toIntExact(LIMIT));
        Arrays.fill(exact, json.length, exact.length, (byte) ' ');

        assertThat(read(message(new CountingStream(exact), new HttpHeaders()))
                        .getBody()
                        .readAllBytes())
                .isEqualTo(exact);
    }

    @Test
    void anExplicitNullGuideFieldIsRefusedWhileAnAbsentOneOrAnotherNullIsNot() {
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(
                        bytes("{\"guide\":null,\"guideUri\":\"https://a\"}"), null))
                .contains("guide");
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(bytes("{\"guide\":\"g\",\"guideUri\":null}"), null))
                .contains("guideUri");
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(bytes("{\"guide\":\"g\",\"lastUpdated\":null}"), null))
                .isEmpty();
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(bytes("{\"guide\":"), null))
                .as("not JSON: the converter answers it")
                .isEmpty();
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(bytes("[null]"), null))
                .isEmpty();
    }

    @Test
    void anExplicitNullListIsFoundAfterTheGuideFields() {
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(bytes("{\"guide\":\"g\",\"commands\":null}"), null))
                .contains("commands");
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(
                        bytes("{\"commands\":null,\"guide\":null,\"guideUri\":\"https://a\"}"), null))
                .as("a null guide field is named before a null list, wherever it stands")
                .contains("guide");
        assertThat(ToolguideRegistrationBodyAdvice.explicitNull(bytes("{\"guide\":\"g\",\"references\":[]}"), null))
                .as("an empty list is a value")
                .isEmpty();
    }

    private HttpInputMessage read(HttpInputMessage message) throws IOException {
        return advice.beforeBodyRead(message, null, ToolguideRegistration.class, null);
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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** A body that counts the bytes read from it. */
    private static final class CountingStream extends ByteArrayInputStream {
        private long read;

        CountingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read() {
            int b = super.read();
            if (b >= 0) {
                read++;
            }
            return b;
        }

        @Override
        public synchronized int read(byte[] into, int off, int len) {
            int n = super.read(into, off, len);
            if (n > 0) {
                read += n;
            }
            return n;
        }
    }
}
