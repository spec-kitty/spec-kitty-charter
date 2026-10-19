package ai.speckitty.charter.service.bdd;

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

    private static final String BASE = "/api/v1";

    private static final String INFO = "/info";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * One request and its response.
     *
     * @param method the lower-case method, as the contract names it
     * @param path the path below the base path, without its query
     * @param result the response
     * @param infoFingerprint after a 200 answer of a read other than {@code /info}: the {@code sourceFingerprint} that
     *     {@code /info} answered right after, with the inputs unchanged; otherwise {@code null}
     */
    public record Exchange(
            String method,
            String path,
            MvcResult result,
            @Nullable String infoFingerprint) {}

    private final MockMvc mvc;
    private final List<Exchange> exchanges = new ArrayList<>();
    private @Nullable String bearerToken;

    ScenarioHttp(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * Sends a request below the API base path and records the exchange.
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
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), BASE + path);
        if (bearerToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        headers.forEach(request::header);
        if (body != null) {
            request.content(body);
        }
        // The contract check matches the path without its query.
        String bare = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        MvcResult answered = mvc.perform(request).andReturn();
        exchanges.add(
                new Exchange(method.toLowerCase(Locale.ROOT), bare, answered, infoFingerprintAfter(bare, answered)));
        return answered;
    }

    private @Nullable String infoFingerprintAfter(String path, MvcResult answered) throws Exception {
        if (answered.getResponse().getStatus() != HttpStatus.OK.value() || path.equals(INFO)) {
            return null;
        }
        MockHttpServletRequestBuilder probe = MockMvcRequestBuilders.get(BASE + INFO);
        if (bearerToken != null) {
            probe.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
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
     * Makes every later request of the scenario carry {@code Authorization: Bearer <token>}.
     *
     * @param token the token, or {@code null} to send none
     */
    public void bearerToken(@Nullable String token) {
        bearerToken = token;
    }

    private Exchange last() {
        if (exchanges.isEmpty()) {
            throw new IllegalStateException("the scenario has sent no request yet");
        }
        return exchanges.getLast();
    }
}
