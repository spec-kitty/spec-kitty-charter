package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.web.ProblemHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The activation set's answers to a request Spring refuses before the controller runs, at unit level (WP09's contract
 * scenarios answer the same through the whole service): a body that cannot be bound is {@code 422 activation_invalid},
 * a header or path parameter that breaks its pattern and a malformed precondition are a plain {@code 400 Problem}, and a
 * body that is not JSON by its type stays {@code 415}. The mapping is ordered before the shared {@link ProblemHandler},
 * which would answer the unbindable body as a bare {@code 400}, and serves the activation controller only.
 */
class ActivationProblemsTest {

    private static final String DOCUMENT = "/api/v1/activations/b-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String VALID = "{\"schemaVersion\": 1}";

    private final MockMvc mvc = StandaloneActivationMvc.over(new InMemoryActivationRepository());

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private static JsonNode body(MockHttpServletResponse answer) throws Exception {
        return JSON.readTree(answer.getContentAsString());
    }

    @Test
    void anUnbindableBodyIs422ActivationInvalidNotTheSharedBare400() throws Exception {
        MockHttpServletResponse answer =
                send(put(DOCUMENT).contentType(MediaType.APPLICATION_JSON).content("{ not json"));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(422);
        assertThat(answer.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(answer).path("code").asString()).isEqualTo("activation_invalid");
        assertThat(body(answer).at("/violations/0/code").asString()).isEqualTo("unreadable_body");
    }

    @Test
    void aHeaderThatBreaksItsPatternIsAPlain400WithoutACode() throws Exception {
        MockHttpServletResponse answer = send(put(DOCUMENT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"not a revision\"")
                .content(VALID));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(400);
        assertThat(body(answer).path("title").asString()).isEqualTo("Bad Request");
        assertThat(body(answer).has("code")).isFalse();
    }

    @Test
    void aMalformedPreconditionIsAPlain400WithItsDetail() throws Exception {
        MockHttpServletResponse answer = send(put(DOCUMENT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "*")
                .header("If-None-Match", "*")
                .content(VALID));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(400);
        assertThat(body(answer).path("detail").asString())
                .isEqualTo("A write carries If-Match or If-None-Match, not both.");
        assertThat(body(answer).has("code")).isFalse();
    }

    @Test
    void aBodyThatIsNotJsonByItsTypeStays415() throws Exception {
        MockHttpServletResponse answer =
                send(put(DOCUMENT).contentType(MediaType.TEXT_PLAIN).content(VALID));

        assertThat(answer.getStatus()).isEqualTo(415);
    }

    @Test
    void theMappingRunsBeforeTheSharedProblemHandlerAndServesTheActivationControllerOnly() {
        int mapping = ActivationProblems.class.getAnnotation(Order.class).value();
        int shared = ProblemHandler.class.getAnnotation(Order.class).value();

        assertThat(mapping).isLessThan(shared);
        assertThat(ActivationProblems.class
                        .getAnnotation(RestControllerAdvice.class)
                        .assignableTypes())
                .containsExactly(ActivationController.class);
    }
}
