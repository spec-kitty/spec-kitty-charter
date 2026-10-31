package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
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
    private static final String BASE = "/api/v1";
    private static final String LINKS = "_links";

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
     * Every 200 of a <em>read</em> (a GET) carries exactly one {@code Charter-Source-Fingerprint}, the digest that read
     * verified: for {@code /info} the {@code sourceFingerprint} of its own body, for another read the one {@code /info}
     * gave right after it (the inputs had not changed); every other read answer carries none. A write (a POST or PUT) is
     * exempt: it reports the pack's post-write revision as an {@code ETag}, not a read's source fingerprint (ADR-0026),
     * so the fingerprint header is simply not part of a write's answer and is neither required nor forbidden on it.
     */
    @After
    public void fingerprintHeaderIsTheVerifiedDigest() throws Exception {
        for (ScenarioHttp.Exchange exchange : http.exchanges()) {
            if (!exchange.method().equalsIgnoreCase("GET")) {
                continue;
            }
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

    @When("the file {string} of the pack {string} is deleted")
    public void deletePackFile(String path, String pack) {
        packs.deleteFile(pack, path);
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

    @Then("the field at {string} is {string}")
    public void fieldAt(String pointer, String value) throws Exception {
        JsonNode field = at(pointer);
        assertThat(field.isString()).as("%s is a string: %s", pointer, field).isTrue();
        assertThat(field.asString()).isEqualTo(value);
    }

    @Then("the field at {string} is {int}")
    public void numberAt(String pointer, int value) throws Exception {
        JsonNode field = at(pointer);
        assertThat(field.isIntegralNumber())
                .as("%s is an integer: %s", pointer, field)
                .isTrue();
        assertThat(field.asInt()).isEqualTo(value);
    }

    @Then("^the field at \"([^\"]*)\" is (true|false)$")
    public void booleanAt(String pointer, String value) throws Exception {
        JsonNode field = at(pointer);
        assertThat(field.isBoolean()).as("%s is a boolean: %s", pointer, field).isTrue();
        assertThat(field.asBoolean()).isEqualTo(Boolean.parseBoolean(value));
    }

    @Then("the answer has no link {string}")
    public void noLink(String relation) throws Exception {
        JsonNode links = JSON.readTree(http.body()).path(LINKS);
        assertThat(links.isObject())
                .as("the answer has _links: %s", http.body())
                .isTrue();
        assertThat(links.has(relation))
                .as("the answer has the link %s", relation)
                .isFalse();
    }

    @Then("the problem has no code")
    public void problemWithoutCode() throws Exception {
        assertThat(http.response().getContentType()).startsWith("application/problem+json");
        assertThat(JSON.readTree(http.body()).has("code"))
                .as("the problem has a code: %s", http.body())
                .isFalse();
    }

    @When("I follow the link {string}")
    public void follow(String relation) throws Exception {
        http.get(belowBase(linkOf(JSON.readTree(http.body()), relation)));
    }

    /**
     * Collects the {@code kind} and {@code id} of every element of {@code member} on this page and on every page its
     * {@code relation} link leads to, following the link until a page has none, and compares them with the table in
     * order: every artifact is served exactly once, none skipped and none repeated.
     */
    @Then("following {string} links from the answer, the {string} ids are:")
    public void followingLinks(String relation, String member, DataTable expected) throws Exception {
        List<List<String>> seen = new ArrayList<>();
        JsonNode page = JSON.readTree(http.body());
        while (true) {
            assertThat(http.response().getStatus())
                    .as("a followed page answers 200: %s", http.body())
                    .isEqualTo(200);
            page.path(member)
                    .valueStream()
                    .forEach(item -> seen.add(List.of(
                            item.path("kind").asString(), item.path("id").asString())));
            if (!page.path(LINKS).has(relation)) {
                break;
            }
            http.get(belowBase(linkOf(page, relation)));
            page = JSON.readTree(http.body());
        }
        assertThat(seen).isEqualTo(expected.cells().subList(1, expected.height()));
    }

    private JsonNode at(String pointer) throws Exception {
        JsonNode field = JSON.readTree(http.body()).at(pointer);
        assertThat(field.isMissingNode())
                .as("the body has %s: %s", pointer, http.body())
                .isFalse();
        return field;
    }

    private static String linkOf(JsonNode answer, String relation) {
        JsonNode href = answer.path(LINKS).path(relation).path("href");
        assertThat(href.isString())
                .as("the answer has the link %s: %s", relation, answer)
                .isTrue();
        return href.asString();
    }

    /** A link's href with the server prefix taken off, as the request steps expect a path below it. */
    private static String belowBase(String href) {
        assertThat(href).as("a link's href starts with the server prefix").startsWith(BASE + "/");
        return href.substring(BASE.length());
    }

    private static @Nullable JsonNode json(String text) {
        try {
            return text.isEmpty() ? null : JSON.readTree(text);
        } catch (JacksonException notJson) {
            return null;
        }
    }
}
