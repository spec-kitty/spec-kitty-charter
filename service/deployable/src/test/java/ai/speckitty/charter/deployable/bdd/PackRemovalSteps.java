package ai.speckitty.charter.deployable.bdd;

import io.cucumber.java.en.When;
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

    /**
     * The steps over the scenario's HTTP seam.
     *
     * @param http the scenario's HTTP seam
     * @param registration the steps that remember an {@code ETag}
     */
    public PackRemovalSteps(ScenarioHttp http, RegistrationSteps registration) {
        this.http = http;
        this.registration = registration;
    }

    @When("I DELETE {string} with the remembered ETag")
    public void deleteWithRememberedETag(String path) throws Exception {
        String remembered = registration.rememberedETag();
        if (remembered == null) {
            throw new IllegalStateException("no ETag has been remembered yet");
        }
        http.perform("DELETE", path, Map.of(HttpHeaders.IF_MATCH, remembered), null);
    }
}
