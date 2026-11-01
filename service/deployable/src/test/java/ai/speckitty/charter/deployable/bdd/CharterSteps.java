package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.UriUtils;
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
    private static final LinkChecks LINK_CHECKS = new LinkChecks(CONTRACT, RelationTable.keys());
    private static final int MAX_PAGES = 100;
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";
    private static final String BODY = "#body";
    private static final String HEADER = "#header:";
    private static final String SHA256 = "sha256:[0-9a-f]{64}";
    private static final String BASE = "/api/v1";
    private static final String LINKS = "_links";
    private static final Pattern EXPRESSION = Pattern.compile("\\{(\\??)([^}]*)}");

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
     * Every success answer with a JSON body is linked as {@link LinkChecks} requires: its {@code self}, every link
     * resolving to an operation of the contract, a 201's {@code Location} and every relation key (R-20). Every success
     * answer, JSON or not, is recorded for the operation-coverage check that runs after the last scenario
     * ({@link OperationCoverage}).
     */
    @After
    public void everySuccessAnswerIsLinked() throws Exception {
        for (ScenarioHttp.Exchange exchange : http.exchanges()) {
            MockHttpServletResponse response = exchange.result().getResponse();
            OperationCoverage.record(CONTRACT, exchange.method(), exchange.path(), response.getStatus());
            JsonNode body = json(response.getContentAsString());
            if (!isSuccess(response) || body == null || !isJson(response.getContentType())) {
                continue;
            }
            LinkChecks.Answer answer = new LinkChecks.Answer(
                    exchange.method(),
                    exchange.path(),
                    exchange.query(),
                    response.getStatus(),
                    response.getHeader(HttpHeaders.LOCATION),
                    body);
            assertThat(LINK_CHECKS.violations(answer))
                    .as("the links of %s %s", exchange.method().toUpperCase(Locale.ROOT), exchange.path())
                    .isEmpty();
        }
    }

    private static boolean isSuccess(MockHttpServletResponse response) {
        return HttpStatus.valueOf(response.getStatus()).is2xxSuccessful();
    }

    private static boolean isJson(@Nullable String type) {
        return type != null && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(type));
    }

    /**
     * The hook's own fingerprint rule, independent of the contract's declarations (R-08 revised): a verified read, a
     * {@code GET} answered 200 with a JSON body on any path but the root {@code /}, carries exactly one
     * {@code Charter-Source-Fingerprint}, the digest that read verified: for {@code /info} the {@code sourceFingerprint}
     * of its own body, for another read the one {@code /info} gave right after it (the inputs had not changed). Every
     * other {@code GET} answer carries none: a refusal, the root, which reads no pack file, and {@code /openapi.yaml},
     * whose body is YAML. A write (a POST or PUT) is exempt: it reports the pack's post-write revision as an
     * {@code ETag}, not a read's source fingerprint (ADR-0026), so the fingerprint header is simply not part of a
     * write's answer and is neither required nor forbidden on it.
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
            if (!isVerifiedRead(exchange.path(), response)) {
                assertThat(values)
                        .as(
                                "%s answered %d, not a verified read, and must carry no fingerprint",
                                what, response.getStatus())
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

    /** A {@code GET} answer of a verified read: 200, a JSON body, and any path but the root. */
    private static boolean isVerifiedRead(String path, MockHttpServletResponse response) {
        String type = response.getContentType();
        return response.getStatus() == HttpStatus.OK.value()
                && !path.equals("/")
                && type != null
                && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(type));
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

    @Then("the field at {string} starts with {string}")
    public void fieldAtStartsWith(String pointer, String prefix) throws Exception {
        JsonNode field = at(pointer);
        assertThat(field.isString()).as("%s is a string: %s", pointer, field).isTrue();
        assertThat(field.asString()).startsWith(prefix);
    }

    /**
     * A created resource's {@code Location} names the resource its answer links as {@code relation}: the path of the
     * header (absolute or not) equals that link's {@code href}.
     */
    @Then("the path of the Location header is the href of the link {string}")
    public void locationIsLink(String relation) throws Exception {
        String location = http.response().getHeader(HttpHeaders.LOCATION);
        assertThat(location)
                .as("the response carries a Location: %s", http.body())
                .isNotNull();
        assertThat(URI.create(location).getRawPath()).isEqualTo(linkOf(JSON.readTree(http.body()), relation));
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
     * Expands the RFC 6570 template of a link of the last answer with the table's values and follows it. The link must
     * say it is templated. A simple expression {@code {name}} is replaced by its percent-encoded value; a form-style
     * query expression {@code {?a,b}} writes {@code ?a=..&b=..} for the names the table gives, and nothing when it gives
     * none of them.
     */
    @When("I follow the templated link {string} with:")
    public void followTemplated(String relation, DataTable values) throws Exception {
        JsonNode link = JSON.readTree(http.body()).path(LINKS).path(relation);
        assertThat(link.path("templated").asBoolean(false))
                .as("the link %s is templated: %s", relation, link)
                .isTrue();
        http.get(belowBase(expand(linkOf(JSON.readTree(http.body()), relation), values.asMap())));
    }

    /** Follows the link {@code relation} of every element of the array {@code member} of the last answer. */
    @Then("following the link {string} of every element of {string} answers {int}")
    public void followEveryElementLink(String relation, String member, int status) throws Exception {
        JsonNode elements = JSON.readTree(http.body()).path(member);
        assertThat(elements.isArray() && !elements.isEmpty())
                .as("the answer has elements in %s: %s", member, http.body())
                .isTrue();
        List<String> hrefs =
                elements.valueStream().map(element -> linkOf(element, relation)).toList();
        for (String href : hrefs) {
            http.get(belowBase(href));
            assertThat(http.response().getStatus())
                    .as("%s answers: %s", href, http.body())
                    .isEqualTo(status);
        }
    }

    /**
     * Collects the {@code kind} and {@code id} of every element of {@code member} on this page and on every page its
     * {@code relation} link leads to, following the link until a page has none, and compares them with the table in
     * order: every artifact is served exactly once, none skipped and none repeated. Every page keeps the page size the
     * answer started with and holds at most that many elements, so a {@code next} that drops {@code pageSize} fails.
     * It follows at most {@value #MAX_PAGES} pages, so a {@code next} that loops fails the step instead of hanging it.
     */
    @Then("following {string} links from the answer, the {string} ids are:")
    public void followingLinks(String relation, String member, DataTable expected) throws Exception {
        List<List<String>> seen = new ArrayList<>();
        JsonNode page = JSON.readTree(http.body());
        int pageSize = page.at("/page/pageSize").asInt();
        for (int pages = 1; ; pages++) {
            assertThat(pages)
                    .as("following %s links ends within %d pages", relation, MAX_PAGES)
                    .isLessThanOrEqualTo(MAX_PAGES);
            assertThat(http.response().getStatus())
                    .as("a followed page answers 200: %s", http.body())
                    .isEqualTo(200);
            assertThat(page.at("/page/pageSize").asInt())
                    .as("page %d keeps the page size of the answer it was followed from: %s", pages, page)
                    .isEqualTo(pageSize);
            assertThat(page.path(member).size())
                    .as("page %d holds at most its /page/pageSize %s: %s", pages, member, page)
                    .isLessThanOrEqualTo(pageSize);
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

    /** The RFC 6570 expansion of the simple and the form-style query expressions of {@code template}. */
    private static String expand(String template, Map<String, String> values) {
        Matcher expression = EXPRESSION.matcher(template);
        StringBuilder expanded = new StringBuilder();
        while (expression.find()) {
            boolean query = !expression.group(1).isEmpty();
            List<String> named = Arrays.stream(expression.group(2).split(","))
                    .filter(values::containsKey)
                    .toList();
            String replacement = query
                    ? named.stream()
                            .map(name -> name + "=" + encode(values.get(name)))
                            .collect(Collectors.joining("&", named.isEmpty() ? "" : "?", ""))
                    : named.stream().map(name -> encode(values.get(name))).collect(Collectors.joining(","));
            expression.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        expression.appendTail(expanded);
        return expanded.toString();
    }

    private static String encode(@Nullable String value) {
        return UriUtils.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private static @Nullable JsonNode json(String text) {
        try {
            return text.isEmpty() ? null : JSON.readTree(text);
        } catch (JacksonException notJson) {
            return null;
        }
    }
}
