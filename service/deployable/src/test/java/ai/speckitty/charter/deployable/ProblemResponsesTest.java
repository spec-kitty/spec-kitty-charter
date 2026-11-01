package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The failures the contract scenarios cannot provoke: a method the path does not have, the container's error page
 * reached by a forward and by a direct request, a server error Spring resolves itself (logged once, never
 * shown), a registration body that bean validation refuses before the handler runs, and one that sends a list as an
 * explicit {@code null}. The 404 and 406 problems and the unexpected-exception {@code internal_error} body are pinned by
 * {@code errors.feature}; what the container answers before Spring sees the request is {@code ConnectorErrorsTest}.
 */
@SpringBootTest(
        classes = {CharterServiceApplication.class, ProblemResponsesTest.Unwritable.class},
        properties = "charter.worktree-token=" + ProblemResponsesTest.TOKEN)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ProblemResponsesTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Unwritable {
        @Bean
        UnwritableController unwritableController() {
            return new UnwritableController();
        }
    }

    /** A handler whose response Spring cannot write: a failure {@code ResponseEntityExceptionHandler} resolves. */
    @RestController
    static class UnwritableController {
        @GetMapping("/test/unwritable")
        String unwritable() {
            throw new HttpMessageNotWritableException("a secret the body must not show");
        }
    }

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String BEARER = "Bearer " + TOKEN;

    private static final String INTERNAL_ERROR = "{\"title\":\"Internal error\",\"status\":500,"
            + "\"code\":\"internal_error\",\"detail\":\"The charter service failed unexpectedly; the failure is logged.\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    void aMethodThePathDoesNotHaveIsAMethodNotAllowedProblemThatKeepsTheAllowHeader() throws Exception {
        mvc.perform(post("/api/v1/info").header("Authorization", BEARER))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("{\"title\":\"Method Not Allowed\",\"status\":405}", JsonCompareMode.STRICT));
    }

    /**
     * A registration body without a field the contract requires is a client error. With bean validation on the
     * classpath (ADR-0041 decision 2), Spring refuses it as the contract's {@code default} problem before the
     * controller runs; before that, the missing field surfaced as an {@code internal_error} 500 (#228).
     */
    @Test
    void aRegistrationBodyWithoutARequiredFieldIsABadRequestProblemNotAServerError() throws Exception {
        mvc.perform(
                        post("/api/v1/packs/qa/artifacts/directive")
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"id\": \"X\", \"schemaVersion\": \"1.0\", \"intent\": \"i\", \"enforcement\": \"required\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("{\"title\":\"Bad Request\",\"status\":400}", JsonCompareMode.STRICT));
    }

    /**
     * A registration that sends a list as an explicit {@code null} is a {@code 422 validation_failed}, for each kind,
     * in the assembled application: the body advices that refuse it are picked up beside the controllers (#292). A
     * {@code null} for the required {@code principles} is refused the same way, before bean validation, which answers
     * a {@code principles} left out with the 400 above. The per-list matrices are the controller tests'.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "directive | {\"id\":\"X\",\"schemaVersion\":\"1.0\",\"title\":\"t\",\"intent\":\"i\","
                        + "\"enforcement\":\"required\",\"references\":null}",
                "paradigm | {\"id\":\"x\",\"schemaVersion\":\"1.0\",\"name\":\"n\",\"summary\":\"s\","
                        + "\"relationships\":null}",
                "styleguide | {\"id\":\"x\",\"schemaVersion\":\"1.0\",\"title\":\"t\",\"scope\":\"code\","
                        + "\"principles\":[\"p\"],\"patterns\":null}",
                "styleguide | {\"id\":\"x\",\"schemaVersion\":\"1.0\",\"title\":\"t\",\"scope\":\"code\","
                        + "\"principles\":null}",
                "toolguide | {\"id\":\"x\",\"schemaVersion\":\"1.0\",\"tool\":\"x\",\"title\":\"t\","
                        + "\"summary\":\"s\",\"guide\":\"## Overview\",\"commands\":null}"
            })
    void aRegistrationWithAnExplicitNullListIsAValidationFailedRefusal(String kind, String body) throws Exception {
        mvc.perform(post("/api/v1/packs/qa/artifacts/" + kind)
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("list is null")));
    }

    /**
     * A registration that sends a {@code null} item in a list is a {@code 422 validation_failed} naming the list, for
     * each kind, in the assembled application: neither the converter nor bean validation refuses a {@code null} item,
     * and the controller's mapping answered it {@code 500} (#309). The per-list matrices are the controller tests'.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "directive | references | 0 | {\"id\":\"X\",\"schemaVersion\":\"1.0\",\"title\":\"t\",\"intent\":\"i\","
                        + "\"enforcement\":\"required\",\"references\":[null]}",
                "paradigm | relationships | 0 | {\"id\":\"x\",\"schemaVersion\":\"1.0\",\"name\":\"n\","
                        + "\"summary\":\"s\",\"relationships\":[null]}",
                "styleguide | principles | 1 | {\"id\":\"x\",\"schemaVersion\":\"1.0\",\"title\":\"t\","
                        + "\"scope\":\"code\",\"principles\":[\"p\",null]}",
                "toolguide | commands | 0 | {\"id\":\"x\",\"schemaVersion\":\"1.0\",\"tool\":\"x\",\"title\":\"t\","
                        + "\"summary\":\"s\",\"guide\":\"## Overview\",\"commands\":[null]}"
            })
    void aRegistrationWithANullListItemIsAValidationFailedRefusalNotAServerError(
            String kind, String list, int item, String body) throws Exception {
        mvc.perform(post("/api/v1/packs/qa/artifacts/" + kind)
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Item ")))
                .andExpect(
                        jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(" of the " + list + " list")));
    }

    /**
     * A {@code null} required field inside an object item is bean validation's {@code 400}, not the item check's and
     * not a server error: the item check refuses only a {@code null} item (#309).
     */
    @Test
    void aNullRequiredFieldInsideAListItemIsABadRequestProblemNotAServerError() throws Exception {
        mvc.perform(post("/api/v1/packs/qa/artifacts/directive")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"X\",\"schemaVersion\":\"1.0\",\"title\":\"t\",\"intent\":\"i\","
                                + "\"enforcement\":\"required\",\"references\":[{\"type\":null,\"id\":\"a\","
                                + "\"when\":null,\"reason\":null}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * A directive, paradigm or styleguide registration body over 1 MiB is a {@code 422 validation_failed}, in the
     * assembled application (#310). The bound's edges are {@code RegistrationBodyAdviceTest}'s.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"directive", "paradigm", "styleguide"})
    void aRegistrationBodyOverTheLimitIsAValidationFailedRefusal(String kind) throws Exception {
        byte[] body = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(body, (byte) ' ');
        body[0] = '{';
        body[body.length - 1] = '}';
        mvc.perform(post("/api/v1/packs/qa/artifacts/" + kind)
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(
                        jsonPath("$.detail").value("The " + kind + " registration body is larger than 1048576 bytes."));
    }

    @Test
    void theContainerErrorPageAnswersWithTheProblemOfTheForwardedStatus() throws Exception {
        mvc.perform(get("/error").header("Authorization", BEARER).requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("{\"title\":\"Bad Request\",\"status\":400}", JsonCompareMode.STRICT));
    }

    @Test
    void theContainerErrorPageForwardedANonErrorStatusAnswersWithAnInternalErrorRefusal() throws Exception {
        mvc.perform(get("/error").header("Authorization", BEARER).requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 200))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(INTERNAL_ERROR, JsonCompareMode.STRICT));
    }

    @Test
    void aDirectRequestForTheContainerErrorPageIsAPathTheServiceDoesNotHave() throws Exception {
        mvc.perform(get("/error").header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("{\"title\":\"Not Found\",\"status\":404}", JsonCompareMode.STRICT));
    }

    @Test
    void aServerErrorSpringResolvesIsLoggedOnceWithItsExceptionAndNeverShownInTheBody(CapturedOutput output)
            throws Exception {
        String body = mvc.perform(get("/test/unwritable").header("Authorization", BEARER))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(INTERNAL_ERROR, JsonCompareMode.STRICT))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("secret").doesNotContain("HttpMessageNotWritable");
        assertThat(output.getAll())
                .containsOnlyOnce("ERROR")
                .containsOnlyOnce("HttpMessageNotWritableException: a secret the body must not show");
    }

    @Test
    void aDirectRequestForTheContainerErrorPageNeedsTheTokenToo() throws Exception {
        mvc.perform(get("/error"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("{\"title\":\"Unauthorized\",\"status\":401}", JsonCompareMode.STRICT));
        mvc.perform(get("/error").header("Authorization", BEARER).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPathOutsideTheApiIsGuardedToo() throws Exception {
        mvc.perform(get("/test/unwritable")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/info")).andExpect(status().isUnauthorized());
    }
}
