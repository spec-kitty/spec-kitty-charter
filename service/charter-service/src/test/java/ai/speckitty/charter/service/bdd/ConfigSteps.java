package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import tools.jackson.databind.ObjectMapper;

/**
 * Steps of contracts/charter/scenarios/service.feature that concern the project configuration directory and the
 * service's own version. They send and read through {@link ScenarioHttp}.
 */
public class ConfigSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEMVER = "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(-[0-9A-Za-z.-]+)?";

    private final ScenarioPacks packs;
    private final ScenarioHttp http;

    public ConfigSteps(ScenarioPacks packs, ScenarioHttp http) {
        this.packs = packs;
        this.http = http;
    }

    /** Writes a file into the scenario's project configuration directory, replacing any earlier one. */
    @Given("the project configuration file {string}:")
    public void configFile(String name, String content) {
        packs.writeConfigFile(name, content);
    }

    /** Makes sure a file of the scenario's project configuration directory does not exist. */
    @Given("the project configuration file {string} is absent")
    public void configFileAbsent(String name) {
        packs.deleteConfigFile(name);
    }

    /** Puts a directory where a file of the project configuration directory would be: reading it fails, even as root. */
    @Given("the project configuration file {string} is a directory")
    public void configFileIsADirectory(String name) {
        packs.makeConfigFileADirectory(name);
    }

    /** Checks a field of the last response body against the semantic version pattern the contract declares. */
    @Then("the field {string} of the response body is a semantic version")
    public void semanticVersionField(String field) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).matches(SEMVER);
    }
}
