package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackLevel;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
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
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";
    private static final String BODY = "#body";
    private static final String HEADER = "#header:";
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

    /** Every exchange of the scenario, not only the last, is checked against the schema the contract declares. */
    @After
    public void conformsToTheContract() throws Exception {
        for (ScenarioHttp.Exchange exchange : http.exchanges()) {
            MockHttpServletResponse response = exchange.result().getResponse();
            String body = response.getContentAsString();
            assertThat(CONTRACT.violations(
                            exchange.method(),
                            exchange.path(),
                            response.getStatus(),
                            response.getContentType(),
                            json(body)))
                    .as(
                            "contract conformance of %s %s: %s",
                            exchange.method().toUpperCase(Locale.ROOT), exchange.path(), body)
                    .isEmpty();
        }
    }

    /**
     * Every 200 of the scenario carries exactly one {@code Charter-Source-Fingerprint}, the digest its read verified:
     * for {@code /info} the {@code sourceFingerprint} of its own body, for another read the one {@code /info} gave
     * right after it (the inputs had not changed). Every other answer carries none.
     */
    @After
    public void fingerprintHeaderIsTheVerifiedDigest() throws Exception {
        for (ScenarioHttp.Exchange exchange : http.exchanges()) {
            MockHttpServletResponse response = exchange.result().getResponse();
            List<String> values = response.getHeaders(FINGERPRINT);
            String what = exchange.method().toUpperCase(Locale.ROOT) + " " + exchange.path();
            if (response.getStatus() != HttpStatus.OK.value()) {
                assertThat(values)
                        .as("%s answered %d and must carry no fingerprint", what, response.getStatus())
                        .isEmpty();
                continue;
            }
            assertThat(values)
                    .as("%s must carry exactly one %s", what, FINGERPRINT)
                    .hasSize(1);
            String expected = exchange.path().equals("/info")
                    ? JSON.readTree(response.getContentAsString())
                            .path("sourceFingerprint")
                            .asString()
                    : exchange.infoFingerprint();
            assertThat(values.getFirst())
                    .as("the fingerprint header of %s", what)
                    .matches(SHA256)
                    .isEqualTo(expected);
        }
    }

    @Given("the service fails unexpectedly while it reads the packs")
    public void failUnexpectedly() {
        packs.failUnexpectedly();
    }

    @When("I GET {string}")
    public void get(String path) throws Exception {
        http.get(path);
    }

    @When("I send {word} {string}")
    public void send(String method, String path) throws Exception {
        http.perform(method, path, Map.of(), null);
    }

    @When("I POST {string}")
    public void post(String path) throws Exception {
        http.perform("POST", path, Map.of(), null);
    }

    @When("I POST {string} with the body:")
    public void postWithBody(String path, String body) throws Exception {
        http.perform("POST", path, Map.of(), body);
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

    @Then("the field {string} of the response body starts with {string}")
    public void fieldStartsWith(String field, String prefix) throws Exception {
        assertThat(JSON.readTree(http.body()).path(field).asString()).startsWith(prefix);
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

    @When("I remember the response body")
    public void rememberBody() throws Exception {
        remembered.put(BODY, http.body());
    }

    @Then("the response body is the remembered one")
    public void bodyIsRemembered() throws Exception {
        assertThat(JSON.readTree(http.body())).isEqualTo(JSON.readTree(remembered.get(BODY)));
    }

    @Then("the response body differs from the remembered one")
    public void bodyDiffersFromRemembered() throws Exception {
        assertThat(JSON.readTree(http.body())).isNotEqualTo(JSON.readTree(remembered.get(BODY)));
    }

    @When("I remember the header {string}")
    public void rememberHeader(String name) {
        remembered.put(HEADER + name, String.valueOf(http.response().getHeader(name)));
    }

    @Then("the response header {string} is the remembered header")
    public void headerIsRememberedHeader(String name) {
        assertThat(http.response().getHeader(name)).isEqualTo(remembered.get(HEADER + name));
    }

    @Then("the response header {string} differs from the remembered header")
    public void headerDiffersFromRememberedHeader(String name) {
        assertThat(http.response().getHeader(name)).isNotEqualTo(remembered.get(HEADER + name));
    }

    @Then("the response header {string} is the remembered {string}")
    public void headerIsRemembered(String name, String field) {
        assertThat(http.response().getHeader(name)).isEqualTo(remembered.get(field));
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
