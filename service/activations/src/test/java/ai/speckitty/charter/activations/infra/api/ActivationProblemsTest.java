package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.web.ProblemHandler;
import ai.speckitty.charter.web.RefusalHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.MethodValidationAdapter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The activation set's answers to a request Spring refuses before the controller runs, at unit level (the contract
 * scenarios answer the same through the whole service): a body that cannot be bound is {@code 422 activation_invalid},
 * a header or path parameter that breaks its pattern is a {@code 400 Problem} whose detail names it (#411), a malformed
 * precondition is a plain {@code 400 Problem}, and a
 * body that is not JSON by its type stays {@code 415}. The mapping is ordered before the shared {@link ProblemHandler},
 * which would answer the unbindable body as a bare {@code 400}, and serves the activation controller only.
 */
class ActivationProblemsTest {

    private static final String DOCUMENT = "/api/v1/activations/b-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String VALID = "{\"schemaVersion\": 2}";

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
    void aHeaderThatBreaksItsPatternIsA400WithoutACodeWhoseDetailNamesIt() throws Exception {
        MockHttpServletResponse answer = send(put(DOCUMENT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"not a revision\"")
                .content(VALID));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(400);
        assertThat(body(answer).path("title").asString()).isEqualTo("Bad Request");
        assertThat(body(answer).has("code")).isFalse();
        assertThat(body(answer).path("detail").asString())
                .startsWith(
                        "The header If-Match is \"\\\"not a revision\\\"\", which is not in the form this parameter accepts.")
                .doesNotContain("must");
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

    @Test
    void aValidationFailureWithNoFieldErrorStillAnswersOneViolationNeverAnEmptyList() throws Exception {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "body");
        errors.reject("whole_body", "breaks its schema");
        MethodParameter parameter = new MethodParameter(
                ActivationProblems.class.getMethod("invalidBody", MethodArgumentNotValidException.class), 0);

        ResponseEntity<ai.speckitty.charter.api.model.CharterRefusal> answer = new ActivationProblems(
                        new RefusalHandler())
                .invalidBody(new MethodArgumentNotValidException(parameter, errors));

        assertThat(answer.getStatusCode().value()).isEqualTo(422);
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().getViolations()).singleElement().satisfies(violation -> {
            assertThat(violation.getPointer()).isEmpty();
            assertThat(violation.getCode()).isEqualTo("invalid_value");
        });
    }

    /** A bean whose methods carry the constraints the generated interface puts on a header, a body and a result. */
    @SuppressWarnings("unused")
    static class Constrained {
        @NotNull public String answer() {
            return "";
        }

        public void write(@Pattern(regexp = "[a-z]+") String header) {}
    }

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static ActivationProblems problems() {
        return new ActivationProblems(new RefusalHandler());
    }

    @Test
    void aWholeParameterThatBreaksItsConstraintIs400() throws Exception {
        Set<ConstraintViolation<Constrained>> violations = VALIDATOR
                .forExecutables()
                .validateParameters(
                        new Constrained(), Constrained.class.getMethod("write", String.class), new Object[] {"UPPER"});

        ResponseEntity<?> answer = problems().constraintViolations(new ConstraintViolationException(violations));

        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(answer.getBody())
                .as("a parameter not bound from the request has no name a client knows, so no detail")
                .isEqualTo(new ai.speckitty.charter.api.model.Problem("Bad Request", 400));
    }

    /** A handler whose header and path parameter are both constrained, as Spring's own method validation sees it. */
    @SuppressWarnings("unused")
    static class Headed {
        public void write(
                @RequestHeader("If-Match") @Pattern(regexp = "[a-z]+") String ifMatch,
                @PathVariable("activationId") @Size(max = 2) String id) {}
    }

    @Test
    void everyBrokenNonBodyParameterOfSpringsOwnValidationIsNamedInParameterOrder() throws Exception {
        java.lang.reflect.Method write = Headed.class.getMethod("write", String.class, String.class);
        MethodValidationResult result = new MethodValidationAdapter(VALIDATOR)
                .validateArguments(new Headed(), write, null, new Object[] {"UP", "abc"}, new Class<?>[0]);

        ResponseEntity<?> answer = problems().invalidParameters(new HandlerMethodValidationException(result));

        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(((ai.speckitty.charter.api.model.Problem) answer.getBody()).getDetail())
                .isEqualTo(
                        "The header If-Match is \"UP\", which is not in the form this parameter accepts. The path parameter"
                                + " activationId is \"abc\", which is longer than 2 character(s).");
    }

    @Test
    void aReturnValueThatBreaksItsConstraintIsTheServersFaultAnd500NeverA400() throws Exception {
        Set<ConstraintViolation<Constrained>> violations = VALIDATOR
                .forExecutables()
                .validateReturnValue(new Constrained(), Constrained.class.getMethod("answer"), null);
        assertThat(violations)
                .as("the fixture breaks a return-value constraint")
                .isNotEmpty();

        ResponseEntity<?> answer = problems().constraintViolations(new ConstraintViolationException(violations));

        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getBody()).isInstanceOf(ai.speckitty.charter.api.model.CharterRefusal.class);
        assertThat(String.valueOf(((ai.speckitty.charter.api.model.CharterRefusal) answer.getBody()).getCode()))
                .contains("internal_error");
    }
}
