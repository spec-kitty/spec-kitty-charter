package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.Base64;

/**
 * The steps of a binary payload (#105, NFR-003): a pack file written from base64, so a scenario can hold bytes that are
 * not text, and the comparison of an answer's body with a pack file byte for byte.
 */
public class AssetPayloadSteps {

    private final ScenarioHttp http;
    private final ScenarioPacks packs;

    /**
     * Steps over the scenario's packs and exchanges.
     *
     * @param http the scenario's HTTP client and record
     * @param packs the scenario's packs
     */
    public AssetPayloadSteps(ScenarioHttp http, ScenarioPacks packs) {
        this.http = http;
        this.packs = packs;
    }

    @Given("the {word} pack {string} with the file {string} in base64:")
    public void packFileInBase64(String level, String pack, String path, String base64) {
        packs.addBytes(
                PackLevel.fromToken(level).orElseThrow(),
                pack,
                path,
                Base64.getMimeDecoder().decode(base64.strip()));
    }

    @Then("the response body is the bytes of the file {string} of the pack {string}")
    public void bodyIsTheFile(String path, String pack) {
        byte[] expected = packs.bytesOf(pack, path);
        assertThat(expected).as("the pack file %s", path).isNotEmpty();
        assertThat(http.response().getContentAsByteArray()).isEqualTo(expected);
    }
}
