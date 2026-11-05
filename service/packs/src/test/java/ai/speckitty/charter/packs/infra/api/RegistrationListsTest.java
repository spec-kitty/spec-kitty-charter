package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.BodyLimit;
import ai.speckitty.charter.web.JsonBodyGuard;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;

/**
 * The list members read off the generated registration DTOs, and the bounded read of a registration body refused as
 * {@code validation_failed} (#310). The names are pinned here so a contract change that adds, renames or drops a list
 * shows up as a failure. The guard's own null and charset cases are the shared web transport's ({@code JsonBodyGuardTest}).
 */
class RegistrationListsTest {

    @Test
    void theListMembersOfEachRegistrationAreReadOffItsDto() {
        assertThat(RegistrationLists.of(DirectiveRegistration.class))
                .containsExactly(
                        "explicitAllowances",
                        "integrityRules",
                        "procedures",
                        "references",
                        "relationships",
                        "validationCriteria");
        assertThat(RegistrationLists.of(ParadigmRegistration.class))
                .containsExactly("directiveRefs", "references", "relationships");
        assertThat(RegistrationLists.of(StyleguideRegistration.class))
                .containsExactly(
                        "antiPatterns", "appliesToLanguages", "patterns", "principles", "references", "tooling");
        assertThat(RegistrationLists.of(ToolguideRegistration.class))
                .containsExactly("appliesToLanguages", "commands", "references");
    }

    @Test
    void theBoundedReadRefusesABodyOverTheLimitAndReadsNoMoreThanOneBytePastIt() throws IOException {
        HttpHeaders declared = new HttpHeaders();
        declared.setContentLength(9);
        assertThatThrownBy(() -> JsonBodyGuard.readBounded(
                        message(new byte[0], declared), 8, RegistrationLists.refusal("directive")))
                .as("by its Content-Length, before a byte is read")
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage()).isEqualTo("The directive registration body is larger than 8 bytes.");
                });

        ByteArrayInputStream over = new ByteArrayInputStream(bytes("0123456789abcdef"));
        assertThatThrownBy(() -> JsonBodyGuard.readBounded(
                        message(over, new HttpHeaders()), 8, RegistrationLists.refusal("paradigm")))
                .as("without one, one byte past the limit")
                .isInstanceOf(CharterRefusal.class);
        assertThat(over.available()).as("the rest is never read").isEqualTo(16 - 9);

        assertThat(JsonBodyGuard.readBounded(
                        message(bytes("01234567"), new HttpHeaders()), 8, RegistrationLists.refusal("styleguide")))
                .as("a body at the limit is read whole")
                .isEqualTo(bytes("01234567"));
        assertThat(BodyLimit.MAX_BYTES).isEqualTo(1024 * 1024);
    }

    @Test
    void aDtoThatShowsNoListIsRefusedRatherThanLeftUnguarded() {
        assertThatThrownBy(() -> RegistrationLists.of(String.class)).isInstanceOf(IllegalStateException.class);
    }

    private static HttpInputMessage message(byte[] body, HttpHeaders headers) {
        return message(new ByteArrayInputStream(body), headers);
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

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
