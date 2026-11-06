package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the activation register and its closure read (activation-register.feature, ADR-0020 D3-D6,
 * ADR-0059 D4-D5): storing an activation as a precondition, the conditional requests a register answers, and the
 * assertions on a list's size and on an {@code ETag} that quotes a body field. Every scenario shares one Spring
 * context, so the in-memory register is emptied before each one.
 */
public class ActivationSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String JSON_TYPE = "application/json";
    private static final String PROJECT = "/activations/project";

    private final ScenarioHttp http;
    private final ActivationRepository repository;
    private @Nullable String storedETag;

    /**
     * The steps over the scenario's HTTP seam and the service's activation register.
     *
     * @param http the scenario's HTTP seam
     * @param repository the register the service stores activations in
     */
    public ActivationSteps(ScenarioHttp http, ActivationRepository repository) {
        this.http = http;
        this.repository = repository;
    }

    /** No activation leaks from one scenario into the next: the suite runs without a configuration root. */
    @Before
    public void emptyRegister() {
        if (repository instanceof InMemoryActivationRepository inMemory) {
            inMemory.clear();
        }
    }

    @Given("the project activates the pack(s) {string}")
    public void projectActivates(String packIds) throws Exception {
        String packs = Arrays.stream(packIds.split(","))
                .map(String::strip)
                .map(id -> "{\"packId\":\"" + id + "\"}")
                .collect(Collectors.joining(","));
        store(PROJECT, "{\"schemaVersion\":1,\"packs\":[" + packs + "]}");
    }

    @Given("the activation {string} holds:")
    public void activationHolds(String activationId, String body) throws Exception {
        store("/activations/" + activationId, body);
    }

    @When("I GET {string} with If-None-Match the stored ETag")
    public void getIfNoneMatchStored(String path) throws Exception {
        if (storedETag == null) {
            throw new IllegalStateException("no activation has been stored yet");
        }
        http.perform("GET", path, Map.of(HttpHeaders.IF_NONE_MATCH, storedETag), null);
    }

    @When("I PUT {string} with the header {string}: {string} and the body:")
    public void putWithHeader(String path, String name, String value, String body) throws Exception {
        http.perform("PUT", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE, name, value), body);
    }

    @When("I PUT {string} with the headers {string}: {string} and {string}: {string} and the body:")
    public void putWithHeaders(
            String path, String first, String firstValue, String second, String secondValue, String body)
            throws Exception {
        http.perform(
                "PUT", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE, first, firstValue, second, secondValue), body);
    }

    @Then("the answer holds exactly {int} packs")
    public void holdsPacks(int count) throws Exception {
        itemCount("/packs", count);
    }

    @Then("the field at {string} holds exactly {int} item(s)")
    public void itemCount(String pointer, int count) throws Exception {
        JsonNode list = JSON.readTree(http.body()).at(pointer);
        assertThat(list.isArray())
                .as("%s is an array: %s", pointer, http.body())
                .isTrue();
        assertThat(list.size()).as("the items of %s", pointer).isEqualTo(count);
    }

    @Then("the response header {string} is the quoted field {string}")
    public void headerQuotesField(String name, String pointer) throws Exception {
        JsonNode field = JSON.readTree(http.body()).at(pointer);
        assertThat(field.isString())
                .as("%s is a string: %s", pointer, http.body())
                .isTrue();
        assertThat(http.response().getHeader(name)).isEqualTo("\"" + field.asString() + "\"");
    }

    /** Stores a document with a plain PUT, which must create it, and keeps the ETag it was stored with. */
    private void store(String path, String body) throws Exception {
        http.perform("PUT", path, Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE), body);
        assertThat(http.response().getStatus())
                .as("PUT %s creates the activation: %s", path, http.body())
                .isEqualTo(HttpStatus.CREATED.value());
        storedETag = http.response().getHeader(HttpHeaders.ETAG);
    }
}
