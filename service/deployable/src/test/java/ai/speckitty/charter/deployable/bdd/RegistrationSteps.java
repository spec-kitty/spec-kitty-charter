package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The write-path step vocabulary of the directive and relationship registration scenarios: PUT/POST with a body, the
 * If-Match precondition (literal, or the ETag remembered from a prior answer), and the relationship assertions of a
 * read-back body. It sends through the one {@link ScenarioHttp} the scenario owns, so a later Then sees an earlier
 * When's exchange.
 */
public class RegistrationSteps {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScenarioHttp http;
    private @Nullable String rememberedETag;

    /**
     * The steps over the scenario's HTTP seam.
     *
     * @param http the scenario's HTTP seam
     */
    public RegistrationSteps(ScenarioHttp http) {
        this.http = http;
    }

    private static final String JSON_TYPE = "application/json";

    @When("I POST {string} as JSON with the body:")
    public void postAsJson(String path, String body) throws Exception {
        http.perform("POST", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE), body);
    }

    @When("I PUT {string} with the body:")
    public void put(String path, String body) throws Exception {
        http.perform("PUT", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE), body);
    }

    @When("I PUT {string} with If-Match {string} and the body:")
    public void putWithIfMatch(String path, String ifMatch, String body) throws Exception {
        http.perform("PUT", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE, HttpHeaders.IF_MATCH, ifMatch), body);
    }

    @When("I PUT {string} with the remembered ETag and the body:")
    public void putWithRememberedETag(String path, String body) throws Exception {
        if (rememberedETag == null) {
            throw new IllegalStateException("no ETag has been remembered yet");
        }
        http.perform(
                "PUT", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE, HttpHeaders.IF_MATCH, rememberedETag), body);
    }

    @When("I PUT {string} with the body, its {string} padded to {int} bytes:")
    public void putPadded(String path, String field, int bytes, String body) throws Exception {
        ObjectNode json = (ObjectNode) JSON.readTree(body);
        String text = json.path(field).asString();
        int missing = bytes - text.getBytes(StandardCharsets.UTF_8).length;
        if (missing < 0) {
            throw new IllegalArgumentException("the field " + field + " is already longer than " + bytes + " bytes");
        }
        json.put(field, text + "a".repeat(missing));
        http.perform("PUT", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE), JSON.writeValueAsString(json));
    }

    @Then("the field {string} of the response body is the lines:")
    public void fieldLines(String field, String lines) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).isEqualTo(lines + "\n");
    }

    @When("I remember the ETag")
    public void rememberETag() {
        rememberedETag = http.response().getHeader(HttpHeaders.ETAG);
        assertThat(rememberedETag).as("the response carries an ETag").isNotNull();
    }

    @Then("the response body has {int} relationship(s)")
    public void relationshipCount(int count) throws Exception {
        JsonNode relationships = JSON.readTree(http.body()).path("relationships");
        assertThat(relationships.isArray())
                .as("the body has a relationships array")
                .isTrue();
        assertThat(relationships.size()).isEqualTo(count);
    }

    @Then("a relationship of the response body points to {string}")
    public void aRelationshipPointsTo(String to) throws Exception {
        JsonNode relationships = JSON.readTree(http.body()).path("relationships");
        boolean found = false;
        for (JsonNode relationship : relationships) {
            if (to.equals(relationship.path("to").asString())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("a relationship points to %s", to).isTrue();
    }
}
