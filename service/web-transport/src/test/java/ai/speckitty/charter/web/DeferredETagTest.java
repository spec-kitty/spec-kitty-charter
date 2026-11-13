package ai.speckitty.charter.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tag a handler offers through {@link DeferredETag}: it is written on the {@code 2xx} the handler answers, and never
 * on the problem answered when the handler fails after offering it, and the offer does not outlive its body.
 */
class DeferredETagTest {

    private static final String TAG = "\"sha256:offered\"";
    private static final String ATTRIBUTE = DeferredETag.class.getName() + ".etag";

    /** A handler that offers its tag before it builds its body, as the reads do. */
    @RestController
    static class Offering {

        @GetMapping("/answers")
        ResponseEntity<Map<String, String>> answers(HttpServletRequest request) {
            DeferredETag.offer(request, TAG);
            return ResponseEntity.ok(Map.of("id", "answered"));
        }

        @GetMapping("/refuses")
        ResponseEntity<Map<String, String>> refuses(HttpServletRequest request) {
            DeferredETag.offer(request, TAG);
            throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "The mapper found no artifact.");
        }

        @GetMapping("/fails")
        ResponseEntity<Map<String, String>> fails(HttpServletRequest request) {
            DeferredETag.offer(request, TAG);
            throw new IllegalStateException("the mapper failed");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Offering())
            .setControllerAdvice(new RefusalHandler(), new ProblemHandler(), new DeferredETag())
            .build();

    @Test
    void theOfferedTagIsWrittenOnThe200AndTheOfferIsCleared() throws Exception {
        mvc.perform(get("/answers"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", TAG))
                .andExpect(request().attribute(ATTRIBUTE, nullValue()));
    }

    @Test
    void aRefusalRaisedAfterTheOfferCarriesNoTag() throws Exception {
        mvc.perform(get("/refuses"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(request().attribute(ATTRIBUTE, nullValue()));
    }

    @Test
    void aFailureRaisedAfterTheOfferIsAProblemWithNoTag() throws Exception {
        mvc.perform(get("/fails"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(request().attribute(ATTRIBUTE, nullValue()));
    }
}
