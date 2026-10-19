package ai.speckitty.charter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.domain.CharterPackRepository;
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
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The failures the contract scenarios cannot provoke: an unexpected exception, a method the path does not have,
 * and the container's error page. A 4xx is a closed {@code Problem} of title and status; every 500 is the
 * {@code internal_error} refusal with a generic detail.
 */
@SpringBootTest(classes = {CharterServiceApplication.class, ProblemResponsesTest.Failing.class})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ProblemResponsesTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Failing {
        @Bean
        @Primary
        CharterPackRepository failingRepository() {
            return () -> {
                throw new IllegalStateException("a secret the body must not show");
            };
        }

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

    private static final String INTERNAL_ERROR = "{\"title\":\"Internal error\",\"status\":500,"
            + "\"code\":\"internal_error\",\"detail\":\"The charter service failed unexpectedly; the failure is logged.\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    void anUnexpectedExceptionIsAnInternalErrorRefusalThatHidesTheException() throws Exception {
        mvc.perform(get("/api/v1/packs"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json(INTERNAL_ERROR, JsonCompareMode.STRICT));
    }

    @Test
    void aMethodThePathDoesNotHaveIsAMethodNotAllowedProblemThatKeepsTheAllowHeader() throws Exception {
        mvc.perform(post("/api/v1/packs"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("{\"title\":\"Method Not Allowed\",\"status\":405}", JsonCompareMode.STRICT));
    }

    @Test
    void theContainerErrorPageAnswersWithTheProblemOfTheForwardedStatus() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("{\"title\":\"Bad Request\",\"status\":400}", JsonCompareMode.STRICT));
    }

    @Test
    void theContainerErrorPageWithoutAnErrorStatusAnswersWithAnInternalErrorRefusal() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 200))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(INTERNAL_ERROR, JsonCompareMode.STRICT));
        mvc.perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(INTERNAL_ERROR, JsonCompareMode.STRICT));
    }

    @Test
    void aServerErrorSpringResolvesIsLoggedOnceWithItsExceptionAndNeverShownInTheBody(CapturedOutput output)
            throws Exception {
        String body = mvc.perform(get("/test/unwritable"))
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
}
