package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackLevel;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The step vocabulary of contracts/charter/scenarios/README.md. After every scenario that called an operation,
 * the response is checked against the schema the contract declares for it.
 */
public class CharterSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContractConformance CONTRACT = new ContractConformance();
    private static final String SHA256 = "sha256:[0-9a-f]{64}";

    private final ScenarioHttp http;
    private final ScenarioPacks packs;
    private final Map<String, String> remembered = new HashMap<>();

    public CharterSteps(ScenarioHttp http, ScenarioPacks packs) {
        this.http = http;
        this.packs = packs;
    }

    @Before
    public void freshPacks() {
        packs.reset();
    }

    @Given("the {word} pack {string} with the file {string}:")
    public void packFile(String level, String pack, String path, String content) {
        packs.addFile(PackLevel.fromToken(level).orElseThrow(), pack, path, content);
    }

    @After
    public void deletePacks() {
        packs.delete();
    }

    @After
    public void conformsToTheContract() throws Exception {
        if (!http.hasResponse()) {
            return;
        }
        assertThat(CONTRACT.violations(
                        http.method(),
                        http.path(),
                        http.response().getStatus(),
                        http.response().getContentType(),
                        json(http.body())))
                .as(
                        "contract conformance of %s %s: %s",
                        http.method().toUpperCase(Locale.ROOT), http.path(), http.body())
                .isEmpty();
    }

    @Given("the service fails unexpectedly while it reads the packs")
    public void failUnexpectedly() {
        packs.failUnexpectedly();
    }

    @When("I GET {string}")
    public void get(String path) throws Exception {
        http.get(path);
    }

    @When("I GET {string} accepting {string}")
    public void getAccepting(String path, String accept) throws Exception {
        http.perform("GET", path, Map.of(HttpHeaders.ACCEPT, accept), null);
    }

    @Then("the response status is {int}")
    public void status(int status) throws Exception {
        assertThat(http.response().getStatus()).as(http.body()).isEqualTo(status);
    }

    @Then("the response body is:")
    public void body(String expected) throws Exception {
        JsonNode actual = JSON.readTree(http.body());
        assertThat(actual).isEqualTo(JSON.readTree(expected));
    }

    @Then("the response body, apart from {string}, is:")
    public void bodyApartFrom(String field, String expected) throws Exception {
        JsonNode actual = JSON.readTree(http.body());
        assertThat(actual.has(field)).as("the body has %s", field).isTrue();
        ObjectNode rest = ((ObjectNode) actual).deepCopy();
        rest.remove(field);
        assertThat(rest).isEqualTo(JSON.readTree(expected));
    }

    @Then("the field {string} of the response body is {string}")
    public void field(String field, String value) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).isEqualTo(value);
    }

    @Then("the field {string} of the response body is a sha256 digest")
    public void digestField(String field) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).matches(SHA256);
    }

    @When("I remember the field {string} of the response body")
    public void remember(String field) throws Exception {
        remembered.put(field, JSON.readTree(http.body()).path(field).asString());
    }

    @Then("the field {string} of the response body is the remembered one")
    public void sameAsRemembered(String field) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).isEqualTo(remembered.get(field));
    }

    @Then("the field {string} of the response body differs from the remembered one")
    public void differsFromRemembered(String field) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).isNotEqualTo(remembered.get(field));
    }

    @Then("the response header {string} is {string}")
    public void header(String name, String value) {
        assertThat(http.response().getHeader(name)).isEqualTo(value);
    }

    @Then("the response header {string} is a sha256 digest")
    public void digestHeader(String name) {
        assertThat(http.response().getHeader(name)).isNotNull().matches(SHA256);
    }

    @Then("the response has no {string} header")
    public void noHeader(String name) {
        assertThat(http.response().getHeader(name)).isNull();
    }

    @Then("the response header {string} is the contract version")
    public void contractVersionHeader(String name) {
        assertThat(http.response().getHeader(name)).isEqualTo(CONTRACT.version());
    }

    @Then("the field {string} of the response body is the contract version")
    public void contractVersionField(String field) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).isEqualTo(CONTRACT.version());
    }

    @Then("the problem code is {string}")
    public void problemCode(String code) throws Exception {
        assertThat(http.response().getContentType()).startsWith("application/problem+json");
        assertThat(JSON.readTree(http.body()).path("code").asString()).isEqualTo(code);
    }

    private static @Nullable JsonNode json(String text) {
        try {
            return text.isEmpty() ? null : JSON.readTree(text);
        } catch (JacksonException notJson) {
            return null;
        }
    }
}
