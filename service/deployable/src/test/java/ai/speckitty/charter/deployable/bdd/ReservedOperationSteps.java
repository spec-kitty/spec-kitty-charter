package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The 501 pin of a reserved operation (ADR-0020 D8): the operation is declared in full, carries {@code x-reserved},
 * and answers {@code 501 Not Implemented} with a {@code Problem} that has no code. The step reads the declaration
 * itself, with no {@code default} fallback, so a missing {@code 501} or {@code x-reserved} fails here even though
 * the conformance check after the scenario would accept the shared {@code Problem}.
 */
public class ReservedOperationSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContractConformance CONTRACT = new ContractConformance();
    private static final int NOT_IMPLEMENTED = 501;

    private final ScenarioHttp http;

    /**
     * Steps over the scenario's exchanges.
     *
     * @param http the scenario's HTTP client and record
     */
    public ReservedOperationSteps(ScenarioHttp http) {
        this.http = http;
    }

    @Then("the operation is refused as not implemented")
    public void refusedAsNotImplemented() throws Exception {
        String what = http.method().toUpperCase(Locale.ROOT) + " " + http.path();
        ContractConformance.DeclaredOperation operation = CONTRACT.operation(http.method(), http.path())
                .orElseThrow(() -> new AssertionError("the contract declares no operation for " + what));
        assertThat(operation.reserved())
                .as("%s (%s) carries x-reserved", what, operation.pathTemplate())
                .isTrue();
        assertThat(operation.declares(NOT_IMPLEMENTED))
                .as("%s (%s) declares a 501 of its own, not through default", what, operation.pathTemplate())
                .isTrue();

        MockHttpServletResponse response = http.response();
        assertThat(response.getStatus()).as("the status of %s", what).isEqualTo(NOT_IMPLEMENTED);
        assertThat(MediaType.parseMediaType(String.valueOf(response.getContentType())))
                .as("the content type of %s", what)
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .isTrue());
        JsonNode body = JSON.readTree(response.getContentAsString());
        assertThat(body.has("code"))
                .as("the 501 of %s is a Problem with no code: %s", what, body)
                .isFalse();
        assertThat(body.path("status").asInt()).as("the problem status").isEqualTo(NOT_IMPLEMENTED);
        assertThat(response.getHeader("Charter-Contract-Version"))
                .as("Charter-Contract-Version on %s", what)
                .isNotBlank();
        assertThat(response.getHeader("Charter-Worktree"))
                .as("Charter-Worktree on %s", what)
                .isNotBlank();
        assertThat(response.getHeaders("Charter-Source-Fingerprint"))
                .as("%s reads no pack file, so it carries no fingerprint", what)
                .isEmpty();
    }
}
