package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The payload's answer (research R6): the {@code Content-Type} is the mime when the packs domain's rule calls it
 * well-formed and the transport can write it, otherwise {@code application/octet-stream}, and the answer's headers and
 * conditional behaviour. The packs domain's rule itself is {@code MediaTypesTest}.
 */
class PayloadResponsesTest {

    private static final String TOKEN = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String FINGERPRINT = "sha256:" + "b".repeat(64);

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "text/x-python", "application/vnd.acme+json", "text/plain; charset=utf-8"})
    void aWellFormedMimeTheTransportCanWriteIsTheContentType(String mime) {
        assertThat(PayloadResponses.contentType(mime, true)).isEqualTo(mime);
    }

    @Test
    void aMimeTheRuleCallsMalformedIsOctetStream() {
        assertThat(PayloadResponses.contentType("plain", false)).isEqualTo("application/octet-stream");
        assertThat(PayloadResponses.contentType("image/png", false))
                .as("the rule's word is final: the transport never serves a mime the rule refused")
                .isEqualTo("application/octet-stream");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a/b/c", "image/*", "*/*", "text/plaïn", "text/plain\r\nX-Evil: 1", "text/(x)"})
    void aWellFormedMimeTheTransportCannotWriteAsAConcreteTypeIsOctetStream(String mime) {
        assertThat(PayloadResponses.contentType(mime, true)).isEqualTo("application/octet-stream");
    }

    @Test
    void a200CarriesTheBytesTheTypeTheLengthTheFingerprintAndTheSafetyHeadersAndOffersTheTag() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            ResponseEntity<Resource> answer =
                    PayloadResponses.answer(new byte[] {1, 2, 3}, "image/png", true, TOKEN, FINGERPRINT, null);

            assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(answer.getHeaders().getFirst("Content-Type")).isEqualTo("image/png");
            assertThat(answer.getHeaders().getContentLength()).isEqualTo(3);
            assertThat(answer.getHeaders().getFirst("Charter-Source-Fingerprint"))
                    .isEqualTo(FINGERPRINT);
            assertThat(answer.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(answer.getHeaders().getFirst("Content-Security-Policy"))
                    .isEqualTo("sandbox; default-src 'none'");
            assertThat(answer.getHeaders().getETag())
                    .as("the tag is written with the body, after Spring's own check")
                    .isNull();
            assertThat(answer.getBody())
                    .as("a stream, so Spring answers no range")
                    .isExactlyInstanceOf(InputStreamResource.class);
            assertThat(Objects.requireNonNull(answer.getBody()).getInputStream().readAllBytes())
                    .containsExactly(1, 2, 3);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"" + TOKEN + "\"", "*", "W/\"" + TOKEN + "\"", "\"other\", \"" + TOKEN + "\""})
    void aMatchingIfNoneMatchIsA304WithTheTagAloneAndOffersNothing(String ifNoneMatch) {
        ResponseEntity<Resource> answer =
                PayloadResponses.answer(new byte[] {1}, "image/png", true, TOKEN, FINGERPRINT, ifNoneMatch);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(answer.getHeaders().getETag()).isEqualTo("\"" + TOKEN + "\"");
        assertThat(answer.getHeaders().containsHeader("Charter-Source-Fingerprint"))
                .isFalse();
        assertThat(answer.getBody()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"other\"", "\"unclosed, \"" + TOKEN + "\""})
    void anIfNoneMatchThatMatchesNothingIsA200EvenWhenItHoldsTheTagInsideAValueThatIsNotATagList(String ifNoneMatch) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            assertThat(PayloadResponses.answer(new byte[] {1}, "image/png", true, TOKEN, FINGERPRINT, ifNoneMatch)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void a200OutsideARequestHasNoRequestToOfferItsTagTo() {
        RequestContextHolder.resetRequestAttributes();
        assertThatThrownBy(() -> PayloadResponses.answer(new byte[] {1}, "image/png", true, TOKEN, FINGERPRINT, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
