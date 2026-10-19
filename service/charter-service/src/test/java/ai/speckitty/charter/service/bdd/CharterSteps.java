package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackLevel;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The step vocabulary of contracts/charter/scenarios/README.md. After every scenario that called an operation,
 * the response is checked against the schema the contract declares for it.
 */
public class CharterSteps {

    private static final String BASE = "/api/v1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContractConformance CONTRACT = new ContractConformance();
    private static final String SHA256 = "sha256:[0-9a-f]{64}";

    private final MockMvc mvc;
    private final ScenarioPacks packs;
    private final Map<String, String> remembered = new HashMap<>();
    private @Nullable String path;
    private MvcResult result;

    public CharterSteps(MockMvc mvc, ScenarioPacks packs) {
        this.mvc = mvc;
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
        if (path == null) {
            return;
        }
        assertThat(CONTRACT.violations(
                        "get",
                        path,
                        result.getResponse().getStatus(),
                        result.getResponse().getContentType(),
                        json(result.getResponse().getContentAsString())))
                .as(
                        "contract conformance of GET %s: %s",
                        path, result.getResponse().getContentAsString())
                .isEmpty();
    }

    @Given("the service fails unexpectedly while it reads the packs")
    public void failUnexpectedly() {
        packs.failUnexpectedly();
    }

    @When("I GET {string}")
    public void get(String path) throws Exception {
        // The contract check matches the path without its query.
        this.path = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        result = mvc.perform(MockMvcRequestBuilders.get(BASE + path)).andReturn();
    }

    @When("I GET {string} accepting {string}")
    public void getAccepting(String path, String accept) throws Exception {
        this.path = path;
        result = mvc.perform(MockMvcRequestBuilders.get(BASE + path).header(HttpHeaders.ACCEPT, accept))
                .andReturn();
    }

    @Then("the response status is {int}")
    public void status(int status) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(status);
    }

    @Then("the response body is:")
    public void body(String expected) throws Exception {
        JsonNode actual = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(actual).isEqualTo(JSON.readTree(expected));
    }

    @Then("the response body, apart from {string}, is:")
    public void bodyApartFrom(String field, String expected) throws Exception {
        JsonNode actual = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(actual.has(field)).as("the body has %s", field).isTrue();
        ObjectNode rest = ((ObjectNode) actual).deepCopy();
        rest.remove(field);
        assertThat(rest).isEqualTo(JSON.readTree(expected));
    }

    @Then("the field {string} of the response body is {string}")
    public void field(String field, String value) throws Exception {
        assertThat(JSON.readTree(result.getResponse().getContentAsString())
                        .path(field)
                        .asString())
                .isEqualTo(value);
    }

    @Then("the field {string} of the response body is a sha256 digest")
    public void digestField(String field) throws Exception {
        assertThat(JSON.readTree(result.getResponse().getContentAsString())
                        .path(field)
                        .asString())
                .matches(SHA256);
    }

    @When("I remember the field {string} of the response body")
    public void remember(String field) throws Exception {
        remembered.put(
                field,
                JSON.readTree(result.getResponse().getContentAsString())
                        .path(field)
                        .asString());
    }

    @Then("the field {string} of the response body is the remembered one")
    public void sameAsRemembered(String field) throws Exception {
        assertThat(JSON.readTree(result.getResponse().getContentAsString())
                        .path(field)
                        .asString())
                .isEqualTo(remembered.get(field));
    }

    @Then("the field {string} of the response body differs from the remembered one")
    public void differsFromRemembered(String field) throws Exception {
        assertThat(JSON.readTree(result.getResponse().getContentAsString())
                        .path(field)
                        .asString())
                .isNotEqualTo(remembered.get(field));
    }

    @Then("the response header {string} is {string}")
    public void header(String name, String value) {
        assertThat(result.getResponse().getHeader(name)).isEqualTo(value);
    }

    @Then("the response header {string} is a sha256 digest")
    public void digestHeader(String name) {
        assertThat(result.getResponse().getHeader(name)).isNotNull().matches(SHA256);
    }

    @Then("the response has no {string} header")
    public void noHeader(String name) {
        assertThat(result.getResponse().getHeader(name)).isNull();
    }

    @Then("the response header {string} is the contract version")
    public void contractVersionHeader(String name) {
        assertThat(result.getResponse().getHeader(name)).isEqualTo(CONTRACT.version());
    }

    @Then("the field {string} of the response body is the contract version")
    public void contractVersionField(String field) throws Exception {
        assertThat(JSON.readTree(result.getResponse().getContentAsString())
                        .path(field)
                        .asString())
                .isEqualTo(CONTRACT.version());
    }

    @Then("the problem code is {string}")
    public void problemCode(String code) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(JSON.readTree(result.getResponse().getContentAsString())
                        .path("code")
                        .asString())
                .isEqualTo(code);
    }

    private static @Nullable JsonNode json(String text) {
        try {
            return text.isEmpty() ? null : JSON.readTree(text);
        } catch (JacksonException notJson) {
            return null;
        }
    }
}
