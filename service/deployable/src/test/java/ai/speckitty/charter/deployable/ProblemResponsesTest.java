package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The failures the contract scenarios cannot provoke: a method the path does not have, the container's error page
 * reached by a forward and by a direct request, and a server error Spring resolves itself (logged once, never
 * shown). The 404 and 406 problems and the unexpected-exception {@code internal_error} body are pinned by
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
