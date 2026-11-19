package ai.speckitty.charter.deployable.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * The step vocabulary of the removal scenarios ({@code pack-removal.feature}): a {@code DELETE} with the {@code ETag} an
 * earlier step remembered, which a pack revision write requires ({@code 428} without it, {@code 412} with a stale one).
 * A {@code DELETE} with a header spelled out is {@code SecuritySteps}'. It sends through the one {@link ScenarioHttp} the
 * scenario owns, so a later Then sees the exchange.
 */
public class PackRemovalSteps {

    private final ScenarioHttp http;
    private final RegistrationSteps registration;
    private final ScenarioPacks packs;

    /**
     * The steps over the scenario's HTTP seam.
     *
     * @param http the scenario's HTTP seam
     * @param registration the steps that remember an {@code ETag}
     * @param packs the scenario's pack directory, whose {@code packs} domain holds what a registration stored
     */
    public PackRemovalSteps(ScenarioHttp http, RegistrationSteps registration, ScenarioPacks packs) {
        this.http = http;
        this.registration = registration;
        this.packs = packs;
    }

    /**
     * Overwrites a file of a pack a registration stored, as an operator's hand or a failing disk would: the service is
     * not told.
     *
     * @param pack the configured name the pack is stored under
     * @param relativePath the pack-relative path
     * @param content the new content
     * @throws IOException when the file cannot be written
     */
    @Given("the stored pack {string} has the file {string}:")
    public void storedPackHasFile(String pack, String relativePath, String content) throws IOException {
        Path root = packs.rootDirectory()
                .orElseThrow(() -> new IllegalStateException("no pack has been stored yet"))
                .resolve("packs")
                .resolve(pack);
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("the pack " + pack + " is not stored");
        }
        Files.writeString(root.resolve(relativePath), content);
    }

    @When("I DELETE {string} with the remembered ETag")
    public void deleteWithRememberedETag(String path) throws Exception {
        String remembered = registration.rememberedETag();
        if (remembered == null) {
            throw new IllegalStateException("no ETag has been remembered yet");
        }
        http.perform("DELETE", path, Map.of(HttpHeaders.IF_MATCH, remembered), null);
    }

    @When("I DELETE {string} without If-Match")
    public void deleteWithoutIfMatch(String path) throws Exception {
        http.perform("DELETE", path, Map.of(), null);
    }
}
