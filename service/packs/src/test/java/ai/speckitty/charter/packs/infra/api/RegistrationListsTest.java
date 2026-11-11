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
import java.util.Set;
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
                .containsExactly("explicitAllowances", "integrityRules", "validationCriteria");
        assertThat(RegistrationLists.of(ParadigmRegistration.class))
                .as("a paradigm registration's only list is its principles (ADR-0067)")
                .containsExactly("principles");
        assertThat(RegistrationLists.of(StyleguideRegistration.class))
                .containsExactly("antiPatterns", "appliesToLanguages", "patterns", "principles", "tooling");
        assertThat(RegistrationLists.of(ToolguideRegistration.class)).containsExactly("appliesToLanguages", "commands");
    }

    /** ADR-0064: the members a body may send are read off the DTO, so a removed relationship field is not among them. */
    @Test
    void theMembersOfEachRegistrationAreReadOffItsDtoWithoutTheRemovedFields() {
        assertThat(RegistrationLists.members(DirectiveRegistration.class))
                .contains("id", "integrityRules")
                .doesNotContain("references", "relationships", "procedures");
        assertThat(RegistrationLists.members(ParadigmRegistration.class))
                .containsExactlyInAnyOrder("id", "schemaVersion", "name", "summary", "principles");
        assertThat(RegistrationLists.members(StyleguideRegistration.class)).doesNotContain("references");
        assertThat(RegistrationLists.members(ToolguideRegistration.class)).doesNotContain("references");
    }

    @Test
    void onlyARemovedRelationshipMemberIsToldWhereARelationshipIsWritten() {
        assertThat(RegistrationLists.unknownMemberDetail("directive", "procedures"))
                .isEqualTo("A directive registration has no member procedures; a relationship is registered through"
                        + " PUT /packs/{packId}/relationships.");
        assertThat(RegistrationLists.unknownMemberDetail("directive", "titel"))
                .as("a typo is named, not answered with a relationship hint")
                .isEqualTo("A directive registration has no member titel.");
    }

    @Test
    void anUndeclaredMemberIsFoundWhereverItStandsAndANestedOneIsNot() {
        assertThat(RegistrationLists.firstUnknown(bytes("{\"id\":\"a\",\"relationships\":[]}"), null, Set.of("id")))
                .contains("relationships");
        assertThat(RegistrationLists.firstUnknown(bytes("{\"surprise\":1,\"id\":\"a\"}"), null, Set.of("id")))
                .contains("surprise");
        assertThat(RegistrationLists.firstUnknown(bytes("{\"id\":{\"nested\":1}}"), null, Set.of("id")))
                .as("a nested member is bean validation's, not this check's")
                .isEmpty();
        assertThat(RegistrationLists.firstUnknown(bytes("[{\"surprise\":1}]"), null, Set.of("id")))
                .as("a body that is not an object is the converter's")
                .isEmpty();
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
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
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

    /** ADR-0067: a principle item is closed; the guard names the first undeclared member by its place. */
    @Test
    void anUnknownMemberOfAPrincipleItemIsNamedByItsPlaceWhileADeclaredItemIsNot() {
        java.util.Set<String> declared = java.util.Set.of("name", "description");
        byte[] good = "{\"principles\":[{\"name\":\"a\",\"description\":\"b\"}],\"other\":{\"x\":1}}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bad = ("{\"name\":\"n\",\"principles\":[{\"name\":\"a\",\"description\":\"b\"},"
                        + "{\"name\":\"c\",\"description\":\"d\",\"weight\":1}]}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(RegistrationLists.firstUnknownInItems(good, null, "principles", declared))
                .isEmpty();
        assertThat(RegistrationLists.firstUnknownInItems(bad, null, "principles", declared))
                .contains("principles[1].weight");
    }
}
