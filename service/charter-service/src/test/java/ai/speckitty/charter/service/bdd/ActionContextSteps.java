package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Steps of contracts/charter/scenarios/action_context.feature that read the guidance as a graph. They assert on the
 * scenario's own last response, through {@link ScenarioHttp}.
 */
public class ActionContextSteps {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScenarioHttp http;

    public ActionContextSteps(ScenarioHttp http) {
        this.http = http;
    }

    /**
     * Follows {@code via} from every guidance entry of the last response, which must be the answer to {@code path}, and checks that each chain ends at
     * the action node after exactly the entry's {@code hops} steps, through entries of the answer only.
     */
    @Then("following {string} from every guidance entry of {string} reaches the action in exactly {string} edges")
    public void viaChainsMatchHops(String viaField, String path, String hopsField) throws Exception {
        assertThat(http.path()).as("the scenario read this path last").isEqualTo(path);
        assertThat(http.response().getStatus()).as(http.body()).isEqualTo(200);
        JsonNode answer = JSON.readTree(http.body());
        String action = answer.path("action").asString();
        Map<String, JsonNode> byUrn = new HashMap<>();
        answer.path("guidance").forEach(entry -> byUrn.put(entry.path("urn").asString(), entry));
        assertThat(byUrn).as("the answer lists guidance").isNotEmpty();
        byUrn.values().forEach(entry -> assertChain(entry, viaField, hopsField, action, byUrn));
    }

    private static void assertChain(
            JsonNode entry, String viaField, String hopsField, String action, Map<String, JsonNode> byUrn) {
        String urn = entry.path("urn").asString();
        assertThat(entry.path(hopsField).isInt())
                .as("%s has an integer %s", urn, hopsField)
                .isTrue();
        int steps = 0;
        JsonNode current = entry;
        while (!current.path(viaField).asString().equals(action)) {
            current = byUrn.get(current.path(viaField).asString());
            assertThat(current)
                    .as("the %s of %s is an entry of the answer", viaField, urn)
                    .isNotNull();
            steps++;
            assertThat(steps).as("the %s chain of %s ends", viaField, urn).isLessThan(byUrn.size() + 1);
        }
        assertThat(steps + 1)
                .as("edges from %s to the action", urn)
                .isEqualTo(entry.path(hopsField).asInt());
    }
}
