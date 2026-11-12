package ai.speckitty.charter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.api.model.RefusalViolation;
import ai.speckitty.charter.api.model.RequiringPack;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
        "MISSION_TYPE_NOT_IN_FORCE, 409",
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
        "MISSION_TYPE_NOT_IN_FORCE, Mission type not in force",
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
    @DisplayName("mission_type_not_in_force is a 409 on the effective-prompt read, the one operation that raises it")
    void aMissionTypeNotInForceIsAConflictOnThePromptRead() throws NoSuchMethodException {
        ResponseEntity<CharterRefusal> response =
                refusedIn("getEffectiveStepPrompt", RefusalCode.MISSION_TYPE_NOT_IN_FORCE);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        CharterRefusal body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode().getValue()).isEqualTo("mission_type_not_in_force");
        assertThat(body.getStatus()).isEqualTo(409);
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

    /**
     * The kernel records are written fully qualified and the generated DTOs imported (J8), so a body member mapped
     * from the wrong type fails to compile or fails here.
     */
    @Test
    @DisplayName("a refusal's violations render on the problem body, each with its pointer, code and detail")
    void violationsRenderOnTheBody() {
        CharterRefusal body = handler.refused(ai.speckitty.charter.kernel.domain.CharterRefusal.withViolations(
                        RefusalCode.ACTIVATION_INVALID,
                        "The activation names an unknown tactic.",
                        List.of(new ai.speckitty.charter.kernel.domain.RefusalViolation(
                                "/deactivated/tactic/0", "unknown_artifact", "No tactic t-1 is in the packs."))))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(422);
        assertThat(body.getViolations())
                .containsExactly(new RefusalViolation(
                        "/deactivated/tactic/0", "unknown_artifact", "No tactic t-1 is in the packs."));
        assertThat(body.getRequiredBy()).as("left out, not []").isNull();
    }

    @Test
    @DisplayName("at most 100 violations render, and the detail counts the ones left out")
    void violationsAreCappedAndTheRestCounted() {
        List<ai.speckitty.charter.kernel.domain.RefusalViolation> violations = java.util.stream.IntStream.range(0, 250)
                .mapToObj(index -> new ai.speckitty.charter.kernel.domain.RefusalViolation(
                        "/selected/tactic/" + index, "unknown_artifact", "No tactic t-" + index + " is in the packs."))
                .toList();

        CharterRefusal body = handler.refused(ai.speckitty.charter.kernel.domain.CharterRefusal.withViolations(
                        RefusalCode.ACTIVATION_INVALID, "The activation names unknown tactics.", violations))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.getViolations()).hasSize(RefusalHandler.MAX_VIOLATIONS).hasSize(100);
        assertThat(body.getViolations().getFirst().getPointer()).isEqualTo("/selected/tactic/0");
        assertThat(body.getViolations().getLast().getPointer()).isEqualTo("/selected/tactic/99");
        assertThat(body.getDetail())
                .isEqualTo("The activation names unknown tactics. 150 more violations are not listed.");
    }

    /** The controller methods the handler names an operation after; the bodies are never run. */
    @SuppressWarnings("unused")
    private static final class Operations {
        void putPackRelationship() {}

        void putPackDirective() {}

        void getOffering() {}

        void registerPackDirective() {}

        void registerPackParadigm() {}

        void putPackParadigm() {}

        void registerPackStyleguide() {}

        void putPackStyleguide() {}

        void registerPackToolguide() {}

        void putPackToolguide() {}

        void deletePackArtifact() {}

        void deletePackRelationship() {}

        void createActivation() {}

        void replaceActivation() {}

        void selectActivationArtifact() {}

        void unselectActivationArtifact() {}

        void deactivateActivationArtifact() {}

        void reactivateActivationArtifact() {}

        void applyActivationPreset() {}

        void listEffectiveArtifacts() {}

        void getEffectiveGraph() {}

        void getEffectiveStepPrompt() {}
    }

    private ResponseEntity<CharterRefusal> refusedIn(String operation, RefusalCode code) throws NoSuchMethodException {
        org.springframework.mock.web.MockHttpServletRequest servlet =
                new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.web.context.request.ServletWebRequest request =
                new org.springframework.web.context.request.ServletWebRequest(servlet);
        request.setAttribute(
                org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new org.springframework.web.method.HandlerMethod(
                        new Operations(), Operations.class.getDeclaredMethod(operation)),
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        return handler.refused(new ai.speckitty.charter.kernel.domain.CharterRefusal(code, "detail"), request);
    }

    @Test
    @DisplayName(
            "prompt_fragment_invalid is a 422 on putPackRelationship alone: the code's own status is 500 elsewhere")
    void promptFragmentInvalidIsA422OnTheRelationshipWriteOnly() throws NoSuchMethodException {
        ResponseEntity<CharterRefusal> onTheWrite =
                refusedIn("putPackRelationship", RefusalCode.PROMPT_FRAGMENT_INVALID);

        assertThat(onTheWrite.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(onTheWrite.getBody().getCode()).isEqualTo(CharterRefusal.CodeEnum.PROMPT_FRAGMENT_INVALID);
        assertThat(refusedIn("putPackDirective", RefusalCode.PROMPT_FRAGMENT_INVALID)
                        .getStatusCode())
                .as("another impact-checked write keeps the code's own status")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(refusedIn("getOffering", RefusalCode.PROMPT_FRAGMENT_INVALID).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(handler.refused(new ai.speckitty.charter.kernel.domain.CharterRefusal(
                                RefusalCode.PROMPT_FRAGMENT_INVALID, "detail"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("the relationship write keeps the composition codes as 422, with its own copy of the map")
    void theRelationshipWriteStillAnswersTheCompositionCodesAs422() throws NoSuchMethodException {
        assertThat(refusedIn("putPackRelationship", RefusalCode.PACK_REFERENCE_PACK_MISSING)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refusedIn("putPackDirective", RefusalCode.PACK_REFERENCE_PACK_MISSING)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "registerPackDirective",
                "putPackDirective",
                "registerPackParadigm",
                "putPackParadigm",
                "registerPackStyleguide",
                "putPackStyleguide",
                "registerPackToolguide",
                "putPackToolguide",
                "putPackRelationship",
                "deletePackArtifact",
                "deletePackRelationship",
                "createActivation",
                "replaceActivation",
                "selectActivationArtifact",
                "unselectActivationArtifact",
                "deactivateActivationArtifact",
                "reactivateActivationArtifact",
                "applyActivationPreset"
            })
    @DisplayName("prompt_slot_missing is a 422 on every write that judges an enhancement in force before it stores")
    void promptSlotMissingIsA422OnTheImpactCheckedAndTheActivationWrites(String operation)
            throws NoSuchMethodException {
        ResponseEntity<CharterRefusal> refused = refusedIn(operation, RefusalCode.PROMPT_SLOT_MISSING);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refused.getBody().getCode()).isEqualTo(CharterRefusal.CodeEnum.PROMPT_SLOT_MISSING);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"getOffering", "listEffectiveArtifacts", "getEffectiveGraph"})
    @DisplayName("the twin: prompt_slot_missing keeps its own status, 500, on a composing read")
    void promptSlotMissingIsA500OnAComposingRead(String operation) throws NoSuchMethodException {
        assertThat(refusedIn(operation, RefusalCode.PROMPT_SLOT_MISSING).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("the twin: an activation write keeps the composition codes' own status, 500")
    void anActivationWriteKeepsTheCompositionCodesAs500() throws NoSuchMethodException {
        assertThat(refusedIn("selectActivationArtifact", RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING)
                        .getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "PACK_DEPENDENCY_CYCLE, 422",
        "PACK_DEPENDENCY_UNKNOWN, 422",
        "PACK_DEPENDENCY_OUT_OF_RANGE, 422",
        "PACK_PARENT_UNRESOLVED, 422",
        "PACK_LEVEL_VIOLATION, 422",
        "PACK_NAME_CONFLICT, 422",
        "PACK_DUPLICATE_ID, 422",
        "PACK_SIBLING_CONFLICT, 422",
        "PACK_REFERENCE_UNQUALIFIED, 422",
        "PACK_REFERENCE_PACK_MISSING, 422",
        "PACK_REFERENCE_ARTIFACT_MISSING, 422",
        "PACK_VERSION_CONFLICT, 409",
        "REGISTRATION_CONFLICT, 409",
        "VALIDATION_FAILED, 422",
        "OVERRIDE_NOT_PERMITTED, 422",
        "PROMPT_SLOT_MISSING, 422",
        "PACK_STORAGE_FULL, 503",
        "REGISTRATION_TIMED_OUT, 504",
        "PACK_UNREADABLE, 500",
        "INTERNAL_ERROR, 500",
    })
    @DisplayName(
            "the problem of a refused registration carries the status its x-refusal entry gives getPackRegistration")
    void aRefusedRegistrationCarriesTheStatusOfItsEntryForTheStatusResource(RefusalCode code, int expectedStatus) {
        ai.speckitty.charter.kernel.domain.RefusalPack pack = new ai.speckitty.charter.kernel.domain.RefusalPack(
                "01M6R0WP14AAAAAAAAAAAAAAAA", "dependent", "dependent");
        ai.speckitty.charter.api.model.PackRegistrationProblem problem = handler.registrationProblem(
                new ai.speckitty.charter.kernel.domain.CharterRefusal(code, "detail", java.util.List.of(pack)));

        assertThat(problem.getStatus()).isEqualTo(expectedStatus);
        assertThat(problem.getCode()).isEqualTo(code.token());
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isEqualTo("detail");
        assertThat(problem.getPacks())
                .extracting(ai.speckitty.charter.api.model.RefusalPack::getPackId)
                .containsExactly("dependent");
    }

    @Test
    @DisplayName("a request the service cannot take now is a 503 with Retry-After and no code")
    void retryLaterIsAServiceUnavailableWithRetryAfter() {
        ResponseEntity<Problem> response = handler.retryLater(
                new ai.speckitty.charter.kernel.transport.RetryLaterException("The registry is busy.", 7));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().get("Retry-After")).containsExactly("7");
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        Problem body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(503);
        assertThat(body.getDetail()).isEqualTo("The registry is busy.");
    }

    @Test
    @DisplayName("at most 100 packs render, as artifact_referenced promises")
    void packsAreCapped() {
        List<ai.speckitty.charter.kernel.domain.RefusalPack> packs = java.util.stream.IntStream.range(0, 150)
                .mapToObj(index -> new ai.speckitty.charter.kernel.domain.RefusalPack(
                        "01M6R0WP14" + String.format("%016d", index), "pack-" + index, "name-" + index))
                .toList();

        CharterRefusal body = handler.refused(ai.speckitty.charter.kernel.domain.CharterRefusal.withPacksAndViolations(
                        RefusalCode.ARTIFACT_REFERENCED, "Other packs refer to it.", packs, List.of()))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.getPacks()).hasSize(100);
        assertThat(body.getPacks().getFirst().getPackId()).isEqualTo("pack-0");
        assertThat(body.getPacks().getLast().getPackId()).isEqualTo("pack-99");
    }

    @Test
    @DisplayName("exactly 100 violations render whole, and the detail is unchanged")
    void violationsAtTheCapRenderWhole() {
        List<ai.speckitty.charter.kernel.domain.RefusalViolation> violations = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> new ai.speckitty.charter.kernel.domain.RefusalViolation(
                        "/selected/tactic/" + index, "unknown_artifact", "No tactic t-" + index + " is in the packs."))
                .toList();

        CharterRefusal body = handler.refused(ai.speckitty.charter.kernel.domain.CharterRefusal.withViolations(
                        RefusalCode.ACTIVATION_INVALID, "The activation names unknown tactics.", violations))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.getViolations()).hasSize(100);
        assertThat(body.getDetail()).isEqualTo("The activation names unknown tactics.");
    }

    @Test
    @DisplayName("a refusal's requiring packs render on the problem body, each with its extends chain")
    void requiredByRendersOnTheBody() {
        CharterRefusal body = handler.refused(ai.speckitty.charter.kernel.domain.CharterRefusal.withRequiredBy(
                        RefusalCode.ARTIFACT_REQUIRED,
                        "The tactic t-1 is required.",
                        List.of(new ai.speckitty.charter.kernel.domain.RequiringPack(
                                "team-pack", List.of("org-pack")))))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getRequiredBy()).containsExactly(new RequiringPack("team-pack", List.of("org-pack")));
        assertThat(body.getViolations()).as("left out, not []").isNull();
    }

    @Test
    @DisplayName("a refusal without violations or requiring packs leaves both members out")
    void emptyMembersAreLeftOut() {
        CharterRefusal body = handler.refused(
                        new ai.speckitty.charter.kernel.domain.CharterRefusal(RefusalCode.PACK_NOT_FOUND, "d"))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.getViolations()).isNull();
        assertThat(body.getRequiredBy()).isNull();
        assertThat(body.getPacks()).isNull();
    }
}
