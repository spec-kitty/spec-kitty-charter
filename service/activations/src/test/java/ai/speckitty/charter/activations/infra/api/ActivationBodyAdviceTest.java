package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.transport.BodyLimit;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The #125 build notes: an activation body over 1 MiB, with a {@code null} at any depth, with a member its schema does
 * not declare, that cannot be bound or that breaks a constraint is {@code 422 activation_invalid} with violations, and
 * nothing is stored. The controller is served as the
 * service serves it ({@link StandaloneActivationMvc}); the composition root's full context answers the same in the
 * contract scenarios.
 */
class ActivationBodyAdviceTest {

    private static final String DOCUMENT = "/api/v1/activations/b-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc = StandaloneActivationMvc.over(new InMemoryActivationRepository());

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse putBody(String body) throws Exception {
        return send(put(DOCUMENT).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** The answer is 422 activation_invalid, its violations are the given pointer and code pairs, and nothing is stored. */
    private void refused(MockHttpServletResponse answer, String... pointerThenCode) throws Exception {
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(422);
        JsonNode body = JSON.readTree(answer.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("activation_invalid");
        List<String> found = new java.util.ArrayList<>();
        body.path("violations").forEach(violation -> {
            found.add(violation.path("pointer").asString());
            found.add(violation.path("code").asString());
        });
        assertThat(found).containsExactly(pointerThenCode);
        assertThat(send(get(DOCUMENT)).getStatus()).isEqualTo(404);
    }

    @Test
    void aBodyOverOneMebibyteIsTooLarge() throws Exception {
        String padding = "x".repeat(Math.toIntExact(BodyLimit.MAX_BYTES));

        refused(putBody("{\"schemaVersion\": 2, \"padding\": \"" + padding + "\"}"), "", "body_too_large");
    }

    @Test
    void aNullListInsideSelectedIsRefusedAtItsPointer() throws Exception {
        refused(
                putBody("{\"schemaVersion\": 2, \"selected\": {\"directive\": null}}"),
                "/selected/directive",
                "null_value");
    }

    @Test
    void aNullItemIsRefusedAtItsIndex() throws Exception {
        refused(
                putBody("{\"schemaVersion\": 2, \"deactivated\": {\"tactic\": [\"a-1\", null]}}"),
                "/deactivated/tactic/1",
                "null_value");
    }

    @Test
    void aNullTopLevelMemberIsRefused() throws Exception {
        refused(putBody("{\"schemaVersion\": 2, \"packs\": null}"), "/packs", "null_value");
    }

    @Test
    void anUnknownMemberAtAnyDepthIsRefusedAtItsPointer() throws Exception {
        refused(putBody("{\"schemaVersion\": 2, \"favourite\": \"a-1\"}"), "/favourite", "unknown_member");
        refused(
                putBody("{\"schemaVersion\": 2, \"selected\": {\"directives\": [\"a-1\"]}}"),
                "/selected/directives",
                "unknown_member");
        refused(
                putBody("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\", \"pin\": \"1\"}]}"),
                "/packs/0/pin",
                "unknown_member");
    }

    @Test
    void everyUnknownMemberIsReportedSoTheClientFixesTheBodyInOneRoundTrip() throws Exception {
        refused(
                putBody(
                        "{\"schemaVersion\": 2, \"favourite\": \"a-1\", \"packs\": [{\"packId\": \"fixture\", \"pin\": \"1\"}],"
                                + " \"selected\": {\"directives\": [\"a-1\"]}, \"zz\": 0}"),
                "/favourite",
                "unknown_member",
                "/packs/0/pin",
                "unknown_member",
                "/selected/directives",
                "unknown_member",
                "/zz",
                "unknown_member");
    }

    @Test
    void anUnbindableBodyWithoutAPathStillAnswersOneViolationNeverAnEmptyList() throws Exception {
        refused(putBody("[1, 2]"), "", "unreadable_body");
        refused(putBody("{\"schemaVersion\": 2, \"packs\": 5}"), "/packs", "unreadable_body");
    }

    @Test
    void aBodyThatIsNotJsonIsRefused() throws Exception {
        refused(putBody("{ not json"), "", "unreadable_body");
    }

    @Test
    void aValueOfTheWrongTypeIsRefusedAtItsPointer() throws Exception {
        refused(putBody("{\"schemaVersion\": 2, \"languages\": \"en\"}"), "/languages", "unreadable_body");
        refused(
                putBody("{\"schemaVersion\": 2, \"selected\": {\"directive\": {\"id\": \"a-1\"}}}"),
                "/selected/directive",
                "unreadable_body");
    }

    @Test
    void aScalarOfTheWrongTypeIsNeverCoercedOnEitherDocumentWrite() throws Exception {
        refused(putBody("{\"schemaVersion\": true}"), "/schemaVersion", "unreadable_body");
        refused(putBody("{\"schemaVersion\": 2, \"languages\": [7]}"), "/languages/0", "unreadable_body");
        refused(
                putBody("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"alpha\", \"version\": 1}]}"),
                "/packs/0/version",
                "unreadable_body");
        MockHttpServletResponse created = send(post("/api/v1/activations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": \"1\"}"));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(422);
    }

    @Test
    void aBrokenConstraintIsRefusedAsBeanValidationFindsIt() throws Exception {
        refused(putBody("{\"schemaVersion\": 1}"), "/schemaVersion", "invalid_value");
        refused(putBody("{\"schemaVersion\": 3}"), "/schemaVersion", "invalid_value");
        refused(
                putBody("{\"schemaVersion\": 2, \"selected\": {\"agent_profile\": [\"not an id\"]}}"),
                "/selected/agent_profile/0",
                "invalid_value");
        refused(putBody("{}"), "/schemaVersion", "invalid_value");
        refused(
                putBody("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\", \"version\": \"  \"}]}"),
                "/packs/0/version",
                "invalid_value");
    }

    @Test
    void aMemberRepeatedInTheBodyIsRefusedAtItsPointerNotReadLastWins() throws Exception {
        refused(putBody("{\"schemaVersion\": 2, \"schemaVersion\": 1}"), "/schemaVersion", "duplicate_member");
        refused(
                putBody("{\"schemaVersion\": 2, \"selected\": {\"directive\": [\"a-1\"], \"directive\": []}}"),
                "/selected/directive",
                "duplicate_member");
        refused(
                putBody("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\", \"packId\": \"other\"}]}"),
                "/packs/0/packId",
                "duplicate_member");
    }

    @Test
    void aScalarOfAnotherTypeIsNeverCoercedToTheDeclaredOne() throws Exception {
        refused(putBody("{\"schemaVersion\": \"1\"}"), "/schemaVersion", "unreadable_body");
        refused(putBody("{\"schemaVersion\": 1.0}"), "/schemaVersion", "unreadable_body");
        refused(
                putBody("{\"schemaVersion\": 2, \"selected\": {\"directive\": [1, true]}}"),
                "/selected/directive/0",
                "unreadable_body");
        refused(
                putBody("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\", \"version\": 7}]}"),
                "/packs/0/version",
                "unreadable_body");
    }

    @Test
    void aCreateIsGuardedAsAReplaceIs() throws Exception {
        MockHttpServletResponse answer = send(post("/api/v1/activations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": 2, \"selected\": {\"directive\": [null]}}"));

        assertThat(answer.getStatus()).isEqualTo(422);
        assertThat(JSON.readTree(answer.getContentAsString())
                        .path("violations")
                        .path(0)
                        .path("pointer")
                        .asString())
                .isEqualTo("/selected/directive/0");
    }

    @Test
    void anActivationIdOutsideItsGrammarIs400() throws Exception {
        MockHttpServletResponse answer = send(get("/api/v1/activations/-bad"));

        assertThat(answer.getStatus()).isEqualTo(400);
    }

    @Test
    void aValidBodyPasses() throws Exception {
        MockHttpServletResponse answer =
                putBody("{\"schemaVersion\": 2, \"selected\": {\"directive\": [\"b-2\", \"a-1\", \"b-2\"]}}");

        assertThat(answer.getStatus()).isEqualTo(201);
        assertThat(JSON.readTree(answer.getContentAsString())
                        .at("/config/selected/directive")
                        .toString())
                .isEqualTo("[\"a-1\",\"b-2\"]");
    }

    /**
     * Q4 (IC-20): {@code activatedKinds} and {@code missionTypes} are no longer members of the document. A write that
     * sends either is {@code 422 validation_failed}, one {@code removed_member} violation per member, whatever its value,
     * whose detail names the replacement; any other fault of the body is listed beside them, and nothing is stored.
     */
    @Test
    void theRemnantMembersAreValidationFailedNamingTheirReplacementOnEitherDocumentWrite() throws Exception {
        MockHttpServletResponse replaced =
                putBody("{\"schemaVersion\": 2, \"activatedKinds\": [\"directive\"], \"missionTypes\": \"research\"}");
        JsonNode problem = remnantRefused(replaced, "/activatedKinds", "/missionTypes");
        assertThat(problem.at("/violations/0/detail").asString()).contains("selected.<kind>");
        assertThat(problem.at("/violations/1/detail").asString()).contains("selected.mission_type");

        MockHttpServletResponse created = send(post("/api/v1/activations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": 2, \"missionTypes\": [\"research\"], \"zz\": 0}"));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(422);
        JsonNode both = JSON.readTree(created.getContentAsString());
        assertThat(both.path("code").asString()).isEqualTo("validation_failed");
        assertThat(both.at("/violations/0/pointer").asString()).isEqualTo("/missionTypes");
        assertThat(both.at("/violations/1/pointer").asString()).isEqualTo("/zz");
        assertThat(both.at("/violations/1/code").asString()).isEqualTo("unknown_member");
    }

    private JsonNode remnantRefused(MockHttpServletResponse answer, String... pointers) throws Exception {
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(422);
        JsonNode body = JSON.readTree(answer.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("validation_failed");
        List<String> found = new java.util.ArrayList<>();
        body.path("violations").forEach(violation -> {
            assertThat(violation.path("code").asString()).isEqualTo("removed_member");
            found.add(violation.path("pointer").asString());
        });
        assertThat(found).containsExactly(pointers);
        assertThat(send(get(DOCUMENT)).getStatus()).isEqualTo(404);
        return body;
    }

    @Test
    void theAdviceGuardsTheSubResourceBodiesToo() {
        ActivationBodyAdvice advice = new ActivationBodyAdvice();

        assertThat(advice.supports(null, ActivationConfig.class, null)).isTrue();
        assertThat(advice.supports(null, SelectionRequest.class, null)).isTrue();
        assertThat(advice.supports(null, String.class, null)).isFalse();
        assertThat(ActivationBodyAdvice.violations(
                        "{\"cascade\": null, \"extra\": 1}".getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON,
                        SelectionRequest.class))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("/cascade", "null_value"),
                        org.assertj.core.groups.Tuple.tuple("/extra", "unknown_member"));
    }

    @Test
    void aBodyInADeclaredCharsetIsReadInIt() {
        byte[] latin1 = "{\"schemaVersion\": 2, \"café\": 1}".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ActivationBodyAdvice.violations(
                        latin1,
                        MediaType.parseMediaType("application/json;charset=ISO-8859-1"),
                        ActivationConfig.class))
                .extracting(RefusalViolation::pointer)
                .containsExactly("/café");
    }
}
