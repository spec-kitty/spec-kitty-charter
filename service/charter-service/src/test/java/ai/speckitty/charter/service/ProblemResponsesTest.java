package ai.speckitty.charter.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.domain.CharterPackRepository;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The failures the contract scenarios cannot provoke: an unexpected exception, a method the path does not have,
 * and the container's error page. Each is a closed {@code Problem}: title and status, nothing else.
 */
@SpringBootTest(classes = {CharterServiceApplication.class, ProblemResponsesTest.Failing.class})
@AutoConfigureMockMvc
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
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void anUnexpectedExceptionIsAnInternalServerErrorProblemThatHidesTheException() throws Exception {
        mvc.perform(get("/api/v1/packs"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        content().json("{\"title\":\"Internal Server Error\",\"status\":500}", JsonCompareMode.STRICT));
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
    void theContainerErrorPageWithoutAnErrorStatusAnswersWithAnInternalServerErrorProblem() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 200))
                .andExpect(status().isInternalServerError())
                .andExpect(
                        content().json("{\"title\":\"Internal Server Error\",\"status\":500}", JsonCompareMode.STRICT));
        mvc.perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(
                        content().json("{\"title\":\"Internal Server Error\",\"status\":500}", JsonCompareMode.STRICT));
    }
}
