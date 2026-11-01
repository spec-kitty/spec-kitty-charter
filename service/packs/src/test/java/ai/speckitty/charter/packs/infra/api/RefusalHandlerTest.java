package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The {@link RefusalHandler} code&rarr;status map: each refusal code is answered with its own HTTP status and a
 * non-blank title, and the body carries the code's wire token. The new validation (422) and conflict/policy (409)
 * tokens resolve through the generated {@code CharterRefusal.CodeEnum}.
 */
@DisplayName("RefusalHandler status map")
class RefusalHandlerTest {

    private final RefusalHandler handler = new RefusalHandler();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "MISSION_TYPE_NOT_FOUND, 404",
        "ACTION_NOT_FOUND, 404",
        "PACK_NOT_FOUND, 404",
        "TACTIC_NOT_FOUND, 404",
        "KIND_NOT_FOUND, 404",
        "ACTIVATION_NOT_FOUND, 404",
        "ARTIFACT_NOT_FOUND, 404",
        "PRESET_NOT_FOUND, 404",
        "VALIDATION_FAILED, 422",
        "ACTIVATION_INVALID, 422",
        "REGISTRATION_CONFLICT, 409",
        "GOVERNANCE_POLICY_VIOLATION, 409",
        "ARTIFACT_REQUIRED, 409",
        "ARTIFACT_LOCKED, 409",
        "PACK_UNREADABLE, 500",
        "DRG_SCHEMA_ERROR, 500",
        "UNRESOLVED_EDGE_ENDPOINT, 500",
        "PACK_LEVEL_MISMATCH, 500",
        "STALE_INPUT, 500",
        "INTERNAL_ERROR, 500",
    })
    @DisplayName("each code is answered with its status, its token and a non-blank title")
    void eachCodeMapsToItsStatus(RefusalCode code, int expectedStatus) {
        ResponseEntity<CharterRefusal> response =
                handler.refused(new ai.speckitty.charter.kernel.domain.CharterRefusal(code, "detail"));

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        CharterRefusal body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(expectedStatus);
        assertThat(body.getCode().getValue()).isEqualTo(code.token());
        assertThat(body.getTitle()).isNotBlank();
        assertThat(body.getDetail()).isEqualTo("detail");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "MISSION_TYPE_NOT_FOUND, Mission type not found",
        "ACTION_NOT_FOUND, Action not found",
        "PACK_LEVEL_MISMATCH, Pack level mismatch",
        "PACK_UNREADABLE, Pack unreadable",
        "DRG_SCHEMA_ERROR, Invalid DRG document",
        "UNRESOLVED_EDGE_ENDPOINT, Unresolved edge endpoint",
        "INTERNAL_ERROR, Internal error",
        "GOVERNANCE_POLICY_VIOLATION, Governance policy violation",
        "STALE_INPUT, Stale input",
        "PACK_NOT_FOUND, Pack not found",
        "TACTIC_NOT_FOUND, Tactic not found",
        "DIRECTIVE_NOT_FOUND, Directive not found",
        "STYLEGUIDE_NOT_FOUND, Styleguide not found",
        "PARADIGM_NOT_FOUND, Paradigm not found",
        "TOOLGUIDE_NOT_FOUND, Toolguide not found",
        "KIND_NOT_FOUND, Kind not found",
        "VALIDATION_FAILED, Validation failed",
        "REGISTRATION_CONFLICT, Registration conflict",
        "ACTIVATION_NOT_FOUND, Activation not found",
        "ARTIFACT_NOT_FOUND, Artifact not found",
        "PRESET_NOT_FOUND, Preset not found",
        "ARTIFACT_REQUIRED, Artifact required",
        "ARTIFACT_LOCKED, Artifact locked",
        "ACTIVATION_INVALID, Activation invalid",
    })
    @DisplayName("each code is answered with its title")
    void eachCodeHasItsTitle(RefusalCode code, String title) {
        CharterRefusal body = handler.refused(new ai.speckitty.charter.kernel.domain.CharterRefusal(code, "d"))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("an unserved path kind is a 404 titled Kind not found, with the kind_not_found token")
    void anUnservedKindIsKindNotFound() {
        ResponseEntity<CharterRefusal> response = handler.refused(new ai.speckitty.charter.kernel.domain.CharterRefusal(
                RefusalCode.valueOf("KIND_NOT_FOUND"), "The service serves no artifact kind bogus."));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        CharterRefusal body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Kind not found");
        assertThat(body.getCode().getValue()).isEqualTo("kind_not_found");
        assertThat(RefusalCode.valueOf("KIND_NOT_FOUND").notFound())
                .as("a not-found code")
                .isTrue();
    }

    @Test
    @DisplayName("every refusal code has a status mapping (no code falls through)")
    void everyCodeHasAMapping() {
        for (RefusalCode code : RefusalCode.values()) {
            assertThatCode(() -> handler.refused(new ai.speckitty.charter.kernel.domain.CharterRefusal(code, "d")))
                    .as(code.name())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("the new validation and conflict tokens resolve through the generated CodeEnum")
    void theNewTokensResolveThroughTheContractEnum() {
        assertThat(CharterRefusal.CodeEnum.fromValue("validation_failed").getValue())
                .isEqualTo("validation_failed");
        assertThat(CharterRefusal.CodeEnum.fromValue("registration_conflict").getValue())
                .isEqualTo("registration_conflict");
    }

    @Test
    @DisplayName("a validation failure is 422 Unprocessable Content; a conflict is 409")
    void theNewCodesCarryTheirStatus() {
        ResponseEntity<CharterRefusal> validation = handler.refused(
                new ai.speckitty.charter.kernel.domain.CharterRefusal(RefusalCode.VALIDATION_FAILED, "bad"));
        ResponseEntity<CharterRefusal> conflict = handler.refused(
                new ai.speckitty.charter.kernel.domain.CharterRefusal(RefusalCode.REGISTRATION_CONFLICT, "dup"));

        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
