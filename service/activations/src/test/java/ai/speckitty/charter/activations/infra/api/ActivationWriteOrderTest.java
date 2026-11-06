package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The precondition order of the per-artifact and preset writes (FR-010, ADR-0020 D6): {@code 428}, then
 * {@code 404 activation_not_found}, then {@code 412}, and only then a {@code 422}, of the path or of the body. A body
 * fault (a member the schema does not declare, a {@code null}, a value of the wrong type, which is never coerced, a
 * member declared twice, a token outside the cascade's kinds, an empty cascade, a body that is not JSON or is missing)
 * is judged at the {@code 422} step, never at the body read; a malformed {@code If-Match} is a {@code 400} before all
 * of them.
 */
class ActivationWriteOrderTest {

    private static final String SELECT = "/api/v1/activations/a-1/selections/directive/a-1";
    private static final String DEACTIVATE = "/api/v1/activations/a-1/exclusions/directive/a-1";
    private static final String PRESET = "/api/v1/activations/a-1/presets/p";
    private static final String MISSING = "/api/v1/activations/no-such/selections/directive/a-1";
    private static final String STALE = "\"sha256:" + "1f".repeat(32) + "\"";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc = StandaloneActivationMvc.over(new InMemoryActivationRepository());
    private String etag = "";

    @BeforeEach
    void storeTheActivation() throws Exception {
        MockHttpServletResponse created = mvc.perform(put("/api/v1/activations/a-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaVersion\": 1}"))
                .andReturn()
                .getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        etag = created.getHeader("ETag");
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse putBody(String path, String body, String... ifMatch) throws Exception {
        MockHttpServletRequestBuilder request =
                put(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (ifMatch.length > 0) {
            request.header("If-Match", ifMatch[0]);
        }
        return send(request);
    }

    private static List<String> violations(MockHttpServletResponse answer) throws Exception {
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(422);
        JsonNode body = JSON.readTree(answer.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("activation_invalid");
        List<String> found = new ArrayList<>();
        body.path("violations").forEach(violation -> {
            found.add(violation.path("pointer").asString());
            found.add(violation.path("code").asString());
        });
        return found;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"zz\": 1}",
                "{\"cascade\": null}",
                "{\"cascade\": [\"bogus\"]}",
                "{\"cascade\": []}",
                "{\"cascade\": \"directive\"}",
                "{\"includeDeactivated\": \"x\"}",
                "{ not json",
                "[]"
            })
    void aBadBodyWithoutIfMatchIs428(String body) throws Exception {
        assertThat(putBody(SELECT, body).getStatus()).isEqualTo(428);
        assertThat(putBody(DEACTIVATE, body).getStatus()).isEqualTo(428);
        assertThat(putBody(PRESET, "{\"force\": \"x\"}").getStatus()).isEqualTo(428);
    }

    @Test
    void aMissingBodyWithoutIfMatchIs428() throws Exception {
        assertThat(send(put(SELECT).contentType(MediaType.APPLICATION_JSON)).getStatus())
                .isEqualTo(428);
    }

    @Test
    void aBadBodyOnAnActivationThatIsNotStoredIs404() throws Exception {
        MockHttpServletResponse answer = putBody(MISSING, "{\"cascade\": [\"bogus\"], \"zz\": null}", "*");

        assertThat(answer.getStatus()).isEqualTo(404);
        assertThat(JSON.readTree(answer.getContentAsString()).path("code").asString())
                .isEqualTo("activation_not_found");
    }

    @Test
    void aBadBodyWithAStaleIfMatchIs412() throws Exception {
        assertThat(putBody(SELECT, "{\"zz\": 1}", STALE).getStatus()).isEqualTo(412);
        assertThat(putBody(PRESET, "{\"force\": \"x\"}", STALE).getStatus()).isEqualTo(412);
    }

    @Test
    void aMalformedIfMatchIs400BeforeTheBodyIsJudged() throws Exception {
        assertThat(putBody(SELECT, "{\"zz\": 1}", "not-a-revision").getStatus()).isEqualTo(400);
        assertThat(send(delete(SELECT).header("If-Match", "\"x\"")).getStatus()).isEqualTo(400);
    }

    @Test
    void onceThePreconditionsHoldTheBodyIs422AtItsPointers() throws Exception {
        assertThat(violations(putBody(SELECT, "{\"zz\": 1, \"cascade\": null}", etag)))
                .containsExactly("/cascade", "null_value", "/zz", "unknown_member");
        assertThat(violations(putBody(SELECT, "{\"includeDeactivated\": \"x\"}", etag)))
                .containsExactly("/includeDeactivated", "unreadable_body");
        assertThat(violations(putBody(SELECT, "{\"cascade\": []}", etag))).containsExactly("/cascade", "invalid_value");
        assertThat(violations(putBody(PRESET, "{\"force\": \"x\"}", etag)))
                .containsExactly("/force", "unreadable_body");
        assertThat(violations(putBody(DEACTIVATE, "{ not json", etag))).containsExactly("", "unreadable_body");
        assertThat(violations(
                        send(put(SELECT).contentType(MediaType.APPLICATION_JSON).header("If-Match", etag))))
                .containsExactly("", "unreadable_body");
        assertThat(send(get("/api/v1/activations/a-1")).getHeader("ETag")).isEqualTo(etag);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "\"true\"", "0", "\"false\"", "1.0", "\"\""})
    void aScalarOfTheWrongTypeIsNeverCoercedButIs422AfterThePreconditions(String value) throws Exception {
        String select = "{\"cascade\": [\"directive\"], \"includeDeactivated\": " + value + "}";
        String preset = "{\"force\": " + value + "}";

        assertThat(putBody(SELECT, select).getStatus()).isEqualTo(428);
        assertThat(putBody(PRESET, preset).getStatus()).isEqualTo(428);
        assertThat(putBody(SELECT, select, STALE).getStatus()).isEqualTo(412);
        assertThat(putBody(PRESET, preset, STALE).getStatus()).isEqualTo(412);
        assertThat(violations(putBody(SELECT, select, etag))).containsExactly("/includeDeactivated", "unreadable_body");
        assertThat(violations(putBody(PRESET, preset, etag))).containsExactly("/force", "unreadable_body");
        assertThat(send(get("/api/v1/activations/a-1")).getHeader("ETag")).isEqualTo(etag);
    }

    @Test
    void aCascadeTokenOfTheWrongTypeIsNeverCoercedToAString() throws Exception {
        assertThat(violations(putBody(SELECT, "{\"cascade\": [1]}", etag)))
                .containsExactly("/cascade/0", "unreadable_body");
        assertThat(violations(putBody(DEACTIVATE, "{\"cascade\": [true]}", etag)))
                .containsExactly("/cascade/0", "unreadable_body");
    }

    @Test
    void aMemberDeclaredTwiceIs422AtItsPointerAfterThePreconditions() throws Exception {
        String twice = "{\"cascade\": [\"all\"], \"cascade\": [\"all\"]}";

        assertThat(putBody(SELECT, twice).getStatus()).isEqualTo(428);
        assertThat(putBody(SELECT, twice, STALE).getStatus()).isEqualTo(412);
        assertThat(violations(putBody(SELECT, twice, etag))).containsExactly("/cascade", "unreadable_body");
        assertThat(violations(putBody(DEACTIVATE, twice, etag))).containsExactly("/cascade", "unreadable_body");
        assertThat(violations(putBody(PRESET, "{\"force\": true, \"force\": false}", etag)))
                .containsExactly("/force", "unreadable_body");
        assertThat(send(get("/api/v1/activations/a-1")).getHeader("ETag")).isEqualTo(etag);
    }

    @Test
    void aCascadeTokenOutsideTheKindsIsTheServicesUnknownKindAtItsIndex() throws Exception {
        assertThat(violations(putBody(SELECT, "{\"cascade\": [\"directive\", \"bogus\"]}", etag)))
                .containsExactly("/cascade/1", "unknown_kind");
        assertThat(violations(putBody(DEACTIVATE, "{\"cascade\": [\"mission_type\"]}", etag)))
                .containsExactly("/cascade/0", "unknown_kind");
    }

    @Test
    void aWellFormedBodyStillWrites() throws Exception {
        MockHttpServletResponse answer = putBody(SELECT, "{\"cascade\": [\"directive\"]}", etag);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
    }
}
