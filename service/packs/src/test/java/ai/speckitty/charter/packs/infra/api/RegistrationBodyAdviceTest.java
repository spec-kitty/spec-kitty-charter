package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;

/**
 * The directive, paradigm and styleguide registration body guard (#310): a body over the limit is refused by its
 * {@code Content-Length} before a byte is read and, without one, one byte past the limit; a body at the limit is handed
 * on unchanged. The limit is the toolguide's room for the fields beside its guide, 1 MiB.
 */
class RegistrationBodyAdviceTest {

    private static final long LIMIT = 1024 * 1024;

    private final RegistrationBodyAdvice advice = new RegistrationBodyAdvice();

    @ParameterizedTest(name = "{1}")
    @CsvSource({
        "ai.speckitty.charter.api.model.DirectiveRegistration, directive",
        "ai.speckitty.charter.api.model.ParadigmRegistration, paradigm",
        "ai.speckitty.charter.api.model.StyleguideRegistration, styleguide"
    })
    void aDeclaredLengthOverTheLimitIsRefusedBeforeAByteIsRead(Class<?> registration, String kind) {
        CountingStream body = new CountingStream(new byte[0]);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(LIMIT + 1);

        assertThatThrownBy(() -> read(message(body, headers), registration))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage())
                            .isEqualTo("The " + kind + " registration body is larger than " + LIMIT + " bytes.");
                });
        assertThat(body.read).isZero();
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource({
        "ai.speckitty.charter.api.model.DirectiveRegistration, directive",
        "ai.speckitty.charter.api.model.ParadigmRegistration, paradigm",
        "ai.speckitty.charter.api.model.StyleguideRegistration, styleguide"
    })
    void anUndeclaredBodyOverTheLimitIsRefusedOneBytePastIt(Class<?> registration, String kind) {
        byte[] large = new byte[Math.toIntExact(LIMIT) + 4096];
        Arrays.fill(large, (byte) ' ');
        CountingStream body = new CountingStream(large);

        assertThatThrownBy(() -> read(message(body, new HttpHeaders()), registration))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage()).contains(kind + " registration body");
                });
        assertThat(body.read).as("the rest of the body is never read").isEqualTo(LIMIT + 1);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "ai.speckitty.charter.api.model.DirectiveRegistration",
        "ai.speckitty.charter.api.model.ParadigmRegistration",
        "ai.speckitty.charter.api.model.StyleguideRegistration"
    })
    void aBodyAtTheLimitIsHandedOnUnchanged(Class<?> registration) throws IOException {
        byte[] json = "{\"id\":\"a\",\"references\":[]}".getBytes(StandardCharsets.UTF_8);
        byte[] exact = Arrays.copyOf(json, Math.toIntExact(LIMIT));
        Arrays.fill(exact, json.length, exact.length, (byte) ' ');
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(LIMIT);

        assertThat(read(message(new CountingStream(exact), headers), registration)
                        .getBody()
                        .readAllBytes())
                .isEqualTo(exact);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "ai.speckitty.charter.api.model.DirectiveRegistration",
        "ai.speckitty.charter.api.model.ParadigmRegistration",
        "ai.speckitty.charter.api.model.StyleguideRegistration"
    })
    void aNullItemIsRefusedNamingTheList(Class<?> registration) {
        byte[] json = "{\"id\":\"a\",\"references\":[null]}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> read(message(new CountingStream(json), new HttpHeaders()), registration))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage())
                            .isEqualTo("Item 0 of the references list is null; a list holds no null item.");
                });
    }

    @Test
    void theGuardedRegistrationsAreTheThreeWithoutAGuide() {
        for (Type t :
                new Type[] {DirectiveRegistration.class, ParadigmRegistration.class, StyleguideRegistration.class}) {
            assertThat(advice.supports(null, t, null)).as(t.getTypeName()).isTrue();
        }
        assertThat(advice.supports(null, ToolguideRegistration.class, null))
                .as("the toolguide has its own advice, with its own bound")
                .isFalse();
    }

    private HttpInputMessage read(HttpInputMessage message, Type registration) throws IOException {
        return advice.beforeBodyRead(message, null, registration, null);
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
