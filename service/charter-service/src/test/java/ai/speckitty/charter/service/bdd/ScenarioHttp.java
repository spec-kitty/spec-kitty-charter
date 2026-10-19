package ai.speckitty.charter.service.bdd;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The HTTP seam of one scenario. Every glue class sends its requests and reads its responses through the one instance
 * the scenario owns, so a {@code Then} step in one class sees the request a {@code When} step in another sent. It
 * keeps the last response with its method and path, which the conformance check after the scenario reads.
 */
public class ScenarioHttp {

    private static final String BASE = "/api/v1";

    private final MockMvc mvc;
    private @Nullable MvcResult result;
    private @Nullable String method;
    private @Nullable String path;
    private @Nullable String bearerToken;

    ScenarioHttp(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * Sends a request below the API base path and remembers its response as the scenario's last one.
     *
     * @param method the HTTP method, for example {@code GET}
     * @param path the path below the base path, with its query if any
     * @param headers the request headers; an {@code Authorization} header here wins over {@link #bearerToken}
     * @param body the request body, or {@code null} for none
     * @return the response
     * @throws Exception when the request cannot be performed
     */
    public MvcResult perform(String method, String path, Map<String, String> headers, @Nullable String body)
            throws Exception {
        MockHttpServletRequestBuilder request =
                MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), BASE + path);
        if (bearerToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        headers.forEach(request::header);
        if (body != null) {
            request.content(body);
        }
        this.method = method.toLowerCase(java.util.Locale.ROOT);
        // The contract check matches the path without its query.
        this.path = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        result = mvc.perform(request).andReturn();
        return result;
    }

    /**
     * Sends {@code GET} with no headers and no body.
     *
     * @param path the path below the base path, with its query if any
     * @return the response
     * @throws Exception when the request cannot be performed
     */
    public MvcResult get(String path) throws Exception {
        return perform("GET", path, Map.of(), null);
    }

    /**
     * Whether the scenario has sent a request yet.
     *
     * @return {@code true} once {@link #perform} has run
     */
    public boolean hasResponse() {
        return result != null;
    }

    /**
     * The scenario's last response.
     *
     * @return the response of the last request
     * @throws IllegalStateException when the scenario has sent no request
     */
    public MockHttpServletResponse response() {
        return last().getResponse();
    }

    /**
     * The body of the scenario's last response.
     *
     * @return the body as text
     * @throws Exception when the body cannot be read
     */
    public String body() throws Exception {
        return response().getContentAsString();
    }

    /**
     * The method of the last request.
     *
     * @return the lower-case method, as the contract names it
     */
    public String method() {
        return require(method);
    }

    /**
     * The path of the last request, without its query.
     *
     * @return the path below the base path
     */
    public String path() {
        return require(path);
    }

    /**
     * Makes every later request of the scenario carry {@code Authorization: Bearer <token>}.
     *
     * @param token the token, or {@code null} to send none
     */
    public void bearerToken(@Nullable String token) {
        bearerToken = token;
    }

    private MvcResult last() {
        return require(result);
    }

    private static <T> T require(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("the scenario has sent no request yet");
        }
        return value;
    }
}
