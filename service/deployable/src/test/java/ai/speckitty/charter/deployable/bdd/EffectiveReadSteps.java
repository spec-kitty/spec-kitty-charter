package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the effective reads and of the tags they share with {@code getActivation} (effective-reads.feature,
 * layer-merge.feature; operator decisions {@code 01M6Y11E55YP5TG1EJ1MDKCFRP} and {@code 01M6Y3GZFRC5WEQQ7G6GT0YMW3}): a
 * request that sends the {@code ETag} an earlier answer was remembered with, a write whose {@code If-Match} is that
 * tag, a page requested with the cursor of an earlier page, and the comparison of a body member with the revision that
 * the closure tag no longer is.
 */
public class EffectiveReadSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ETAG = HttpHeaders.ETAG;
    private static final String CURSOR_FIELD = "/pageInfo/nextPageCursor";
    private static final String JSON_TYPE = "application/json";

    private final ScenarioHttp http;
    private final CharterSteps charter;
    private final ActivationSteps activations;

    /**
     * The steps over the scenario's HTTP seam and the glue whose remembered values and stored tag they read.
     *
     * @param http the scenario's HTTP seam
     * @param charter the glue that remembers headers and fields
     * @param activations the glue that keeps the stored activation's tag
     */
    public EffectiveReadSteps(ScenarioHttp http, CharterSteps charter, ActivationSteps activations) {
        this.http = http;
        this.charter = charter;
        this.activations = activations;
    }

    @When("I GET {string} with If-None-Match the remembered header {string}")
    public void getIfNoneMatchRemembered(String path, String header) throws Exception {
        http.perform("GET", path, Map.of(HttpHeaders.IF_NONE_MATCH, rememberedHeader(header)), null);
    }

    @When("I follow the link {string} with If-None-Match the remembered header {string}")
    public void followIfNoneMatchRemembered(String relation, String header) throws Exception {
        String path = CharterSteps.belowBase(CharterSteps.linkOf(JSON.readTree(http.body()), relation));
        http.perform("GET", path, Map.of(HttpHeaders.IF_NONE_MATCH, rememberedHeader(header)), null);
    }

    @When("I PUT {string} with If-Match the remembered header {string} and the body:")
    public void putIfMatchRemembered(String path, String header, String body) throws Exception {
        http.perform(
                "PUT",
                path,
                Map.of(HttpHeaders.CONTENT_TYPE, JSON_TYPE, HttpHeaders.IF_MATCH, rememberedHeader(header)),
                body);
    }

    @When("I GET {string} with the remembered cursor")
    public void getWithRememberedCursor(String path) throws Exception {
        String cursor = charter.remembered(CURSOR_FIELD);
        assertThat(cursor)
                .as(
                        "a next page cursor was remembered with: I remember the field \"%s\" of the response body",
                        CURSOR_FIELD)
                .isNotNull()
                .isNotEmpty();
        http.get(path + (path.contains("?") ? "&" : "?") + "pageCursor=" + cursor);
    }

    @Then("the field at {string} is the stored revision")
    public void fieldIsStoredRevision(String pointer) throws Exception {
        assertThat(unquoted(activations.storedETag())).isEqualTo(stringAt(pointer));
    }

    @Then("the field at {string} is the remembered header {string} without its quotes")
    public void fieldIsRememberedHeader(String pointer, String header) throws Exception {
        assertThat(unquoted(rememberedHeader(header))).isEqualTo(stringAt(pointer));
    }

    private String rememberedHeader(String name) {
        String value = charter.rememberedHeader(name);
        assertThat(value)
                .as("the header %s was remembered with: I remember the header \"%s\"", name, ETAG)
                .isNotNull()
                .isNotEqualTo("null");
        return value;
    }

    private String stringAt(String pointer) throws Exception {
        JsonNode field = JSON.readTree(http.body()).at(pointer);
        assertThat(field.isString())
                .as("%s is a string: %s", pointer, http.body())
                .isTrue();
        return field.asString();
    }

    private static String unquoted(String tag) {
        assertThat(tag).as("a quoted entity tag").startsWith("\"").endsWith("\"");
        return tag.substring(1, tag.length() - 1);
    }
}
