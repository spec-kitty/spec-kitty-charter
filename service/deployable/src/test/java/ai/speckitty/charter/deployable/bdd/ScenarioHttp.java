package ai.speckitty.charter.deployable.bdd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * The HTTP seam of one scenario. Every glue class sends its requests and reads its responses through the one instance
 * the scenario owns, so a {@code Then} step in one class sees the request a {@code When} step in another sent. It
 * records every exchange of the scenario, in order, which the checks after the scenario read; the steps see the last
 * one.
 *
 * <p>After a 200 answer of any read but {@code /info}, it also asks {@code /info} once, off the record, and keeps the
 * fingerprint that answer gives. The inputs have not changed in between, so it is the digest the read should have
 * verified: the check after the scenario compares it with the read's {@code Charter-Source-Fingerprint}.
 */
public class ScenarioHttp {

    /** The worktree token the suite configures the service with: 40 characters, as a launcher would generate. */
    public static final String TOKEN = "scenario-worktree-token-0123456789-abcde";

    private static final String BASE = "/api/v1";

    private static final String INFO = "/info";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * One request and its response.
     *
     * @param method the lower-case method, as the contract names it
     * @param path the path below the base path, without its query
     * @param query the raw query of the request, without its {@code ?}; {@code null} when it has none
     * @param result the response
     * @param infoFingerprint after a 200 answer of a read other than {@code /info}: the {@code sourceFingerprint} that
     *     {@code /info} answered right after, with the inputs unchanged; otherwise {@code null}
     */
    public record Exchange(
            String method,
            String path,
            @Nullable String query,
            MvcResult result,
            @Nullable String infoFingerprint) {}

    private final MockMvc mvc;
    private final String suiteToken;
    private final List<Exchange> exchanges = new ArrayList<>();

    /**
     * The seam of one scenario, which sends {@code token} as its bearer token until a step says otherwise.
     *
     * @param mvc the MockMvc the service answers through
     * @param token the worktree token the suite configures the service with
     */
    ScenarioHttp(MockMvc mvc, String token) {
        this.mvc = mvc;
        this.suiteToken = token;
    }

    /**
     * Sends a request below the API base path and records the exchange.
     *
     * @param method the HTTP method, for example {@code GET}
     * @param path the path below the base path, with its query if any
     * @param headers the request headers; an {@code Authorization} header here replaces the bearer token
     * @param body the request body, or {@code null} for none
     * @return the response
     * @throws Exception when the request cannot be performed
     */
    public MvcResult perform(String method, String path, Map<String, String> headers, @Nullable String body)
            throws Exception {
        return perform(method, path, headers, body, suiteToken);
    }

    /**
     * Sends a request below the API base path with an explicit bearer token and records the exchange.
     *
     * @param method the HTTP method, for example {@code GET}
     * @param path the path below the base path, with its query if any
     * @param headers the request headers; an {@code Authorization} header here replaces the bearer token
     * @param body the request body, or {@code null} for none
     * @param token the token to send as {@code Authorization: Bearer <token>}, or {@code null} to send none
     * @return the response
     * @throws Exception when the request cannot be performed
     */
    public MvcResult perform(
            String method, String path, Map<String, String> headers, @Nullable String body, @Nullable String token)
            throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), BASE + path);
        boolean ownAuthorization = headers.keySet().stream().anyMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase);
        if (token != null && !ownAuthorization) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        headers.forEach(request::header);
        if (body != null) {
            request.content(body);
        }
        // The contract check matches the path without its query.
        int mark = path.indexOf('?');
        String bare = mark < 0 ? path : path.substring(0, mark);
        String query = mark < 0 ? null : path.substring(mark + 1);
        MvcResult answered = mvc.perform(request).andReturn();
        exchanges.add(new Exchange(
                method.toLowerCase(Locale.ROOT), bare, query, answered, infoFingerprintAfter(bare, answered)));
        return answered;
    }

    private @Nullable String infoFingerprintAfter(String path, MvcResult answered) throws Exception {
        if (answered.getResponse().getStatus() != HttpStatus.OK.value() || path.equals(INFO)) {
            return null;
        }
        MockHttpServletRequestBuilder probe = MockMvcRequestBuilders.get(BASE + INFO);
        probe.header(HttpHeaders.AUTHORIZATION, "Bearer " + suiteToken);
        MockHttpServletResponse info = mvc.perform(probe).andReturn().getResponse();
        return info.getStatus() == HttpStatus.OK.value()
                ? JSON.readTree(info.getContentAsString())
                        .path("sourceFingerprint")
                        .asString()
                : null;
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
        return !exchanges.isEmpty();
    }

    /**
     * Every exchange of the scenario so far, in the order they were sent.
     *
     * @return a copy of the record; empty when the scenario has sent no request
     */
    public List<Exchange> exchanges() {
        return List.copyOf(exchanges);
    }

    /**
     * The scenario's last response.
     *
     * @return the response of the last request
     * @throws IllegalStateException when the scenario has sent no request
     */
    public MockHttpServletResponse response() {
        return last().result().getResponse();
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
        return last().method();
    }

    /**
     * The path of the last request, without its query.
     *
     * @return the path below the base path
     */
    public String path() {
        return last().path();
    }

    /**
     * The worktree token the suite configures the service with.
     *
     * @return the token, never {@code null}
     */
    public String suiteToken() {
        return suiteToken;
    }

    private Exchange last() {
        if (exchanges.isEmpty()) {
            throw new IllegalStateException("the scenario has sent no request yet");
        }
        return exchanges.getLast();
    }
}
