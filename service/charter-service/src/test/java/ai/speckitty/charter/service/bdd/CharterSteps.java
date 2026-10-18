package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackLevel;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The step vocabulary of contracts/charter/scenarios/README.md. */
public class CharterSteps {

    private static final String BASE = "/api/v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockMvc mvc;
    private final ScenarioPacks packs;
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

    @When("I GET {string}")
    public void get(String path) throws Exception {
        result = mvc.perform(MockMvcRequestBuilders.get(BASE + path)).andReturn();
    }

    @Then("the response status is {int}")
    public void status(int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
    }

    @Then("the response body is:")
    public void body(String expected) throws Exception {
        JsonNode actual = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(actual).isEqualTo(JSON.readTree(expected));
    }

    @Then("the problem code is {string}")
    public void problemCode(String code) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(JSON.readTree(result.getResponse().getContentAsString()).path("code").asString()).isEqualTo(code);
    }
}
