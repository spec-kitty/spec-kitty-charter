package ai.speckitty.charter.deployable.bdd;

import io.cucumber.java.en.When;
import java.util.Map;

/**
 * The steps that decide what a request sends for the worktree guard: no token, another {@code Authorization} header
 * or a browser header. Every other request of the suite carries the suite's token, which {@code ScenarioHttp} adds.
 * In an {@code Authorization} or header value, {@code {token}} stands for that token.
 */
public class SecuritySteps {

    private static final String TOKEN = "{token}";

    private final ScenarioHttp http;

    public SecuritySteps(ScenarioHttp http) {
        this.http = http;
    }

    @When("I {word} {string} without the worktree token")
    public void withoutToken(String method, String path) throws Exception {
        http.perform(method, path, Map.of(), null, null);
    }

    @When("I {word} {string} with the header {string}: {string}")
    public void withHeader(String method, String path, String name, String value) throws Exception {
        http.perform(method, path, Map.of(name, expand(value)), null);
    }

    @When("I {word} {string} with the Authorization {string}")
    public void withAuthorization(String method, String path, String value) throws Exception {
        http.perform(method, path, Map.of("Authorization", expand(value)), null);
    }

    @When("I {word} {string} without the worktree token and with the header {string}: {string}")
    public void withoutTokenWithHeader(String method, String path, String name, String value) throws Exception {
        http.perform(method, path, Map.of(name, expand(value)), null, null);
    }

    @When("I {word} {string} with the headers {string}: {string} and {string}: {string}")
    public void withHeaders(
            String method, String path, String first, String firstValue, String second, String secondValue)
            throws Exception {
        http.perform(method, path, Map.of(first, expand(firstValue), second, expand(secondValue)), null);
    }

    private String expand(String value) {
        return value.replace(TOKEN, http.suiteToken());
    }
}
