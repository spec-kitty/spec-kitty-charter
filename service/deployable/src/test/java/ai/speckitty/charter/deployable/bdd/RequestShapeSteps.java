package ai.speckitty.charter.deployable.bdd;

import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps that send a request whose shape the refusal is about: a body in a media type the operation does not read
 * (415), or a JSON body over the request body limit (413). They serve the scenarios that put each operation's declared
 * refusals in front of the switch gate (ADR-0059 D1).
 */
public class RequestShapeSteps {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScenarioHttp http;

    /**
     * The steps over the scenario's HTTP seam.
     *
     * @param http the scenario's HTTP seam
     */
    public RequestShapeSteps(ScenarioHttp http) {
        this.http = http;
    }

    @When("I send {word} {string} as {string} with the body:")
    public void sendAs(String method, String path, String mediaType, String body) throws Exception {
        http.perform(method, path, Map.of(HttpHeaders.CONTENT_TYPE, mediaType), body);
    }

    @When("I send {word} {string} as {string} with If-Match {string} and the body:")
    public void sendAsWithIfMatch(String method, String path, String mediaType, String ifMatch, String body)
            throws Exception {
        http.perform(method, path, Map.of(HttpHeaders.CONTENT_TYPE, mediaType, HttpHeaders.IF_MATCH, ifMatch), body);
    }

    @When("I POST {string} as JSON with the body, its {string} padded to {int} bytes:")
    public void postPadded(String path, String field, int bytes, String body) throws Exception {
        ObjectNode json = (ObjectNode) JSON.readTree(body);
        String text = json.path(field).asString();
        int missing = bytes - text.getBytes(StandardCharsets.UTF_8).length;
        if (missing < 0) {
            throw new IllegalArgumentException("the field " + field + " is already longer than " + bytes + " bytes");
        }
        json.put(field, text + "a".repeat(missing));
        http.perform("POST", path, Map.of(HttpHeaders.CONTENT_TYPE, "application/json"), JSON.writeValueAsString(json));
    }
}
