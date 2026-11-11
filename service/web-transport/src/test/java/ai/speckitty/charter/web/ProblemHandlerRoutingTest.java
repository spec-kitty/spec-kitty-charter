package ai.speckitty.charter.web;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Problem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * The answers {@link ProblemHandler} words itself outside a parameter's constraint: a path outside the API that matches
 * nothing points to the API root under the configured base path (#454), and a required query parameter the request does
 * not carry is named (#411).
 */
class ProblemHandlerRoutingTest {

    @Test
    void aPathOutsideAConfiguredBasePathPointsToItsRoot() throws Exception {
        ProblemHandler handler = new ProblemHandler("/charter/");

        assertThat(detailOf(handler, "", "/api/v1/x"))
                .isEqualTo("This service answers under /charter/; start at GET /charter/.");
        assertThat(detailOf(handler, "", "/"))
                .isEqualTo("This service answers under /charter/; start at GET /charter/.");
    }

    @Test
    void aPathUnderTheBasePathStaysTheBare404() throws Exception {
        ProblemHandler handler = new ProblemHandler("/charter/");

        assertThat(detailOf(handler, "", "/charter/x")).isNull();
        assertThat(detailOf(handler, "", "/charter")).isNull();
    }

    @Test
    void theBasePathIsJudgedWithinTheContextPathAndDecoded() throws Exception {
        ProblemHandler handler = new ProblemHandler("/api/v1");

        assertThat(detailOf(handler, "/ctx", "/ctx/api/v1/x")).isNull();
        assertThat(detailOf(handler, "/ctx", "/ctx/api%2Fv1/x")).isNull();
        assertThat(detailOf(handler, "/ctx", "/ctx/info")).isNotNull();
    }

    @Test
    void aMissingRequiredQueryParameterIsNamed() throws Exception {
        ResponseEntity<Object> answer = new ProblemHandler()
                .handleException(
                        new MissingServletRequestParameterException("to", "String"),
                        new ServletWebRequest(new MockHttpServletRequest("DELETE", "/api/v1/packs/qa/relationships")));

        assertThat(answer).isNotNull();
        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(((Problem) answer.getBody()).getDetail())
                .isEqualTo("The query parameter to is missing, and it is required.");
    }

    private static String detailOf(ProblemHandler handler, String contextPath, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(contextPath);
        ResponseEntity<Object> answer = handler.handleException(
                new NoHandlerFoundException("GET", uri, HttpHeaders.EMPTY), new ServletWebRequest(request));
        assertThat(answer).isNotNull();
        assertThat(answer.getStatusCode().value()).isEqualTo(404);
        return ((Problem) answer.getBody()).getDetail();
    }
}
