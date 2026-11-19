package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the tensions (tensions.feature, #602, ADR-0045 decisions 9 to 12): each step reads one list of
 * the last answer as lines of text, words and reconcilers included, so a tension that loses its words or its
 * reconcilers fails a scenario that the plain edge steps would pass. A tension reads
 * {@code <source> tension <target> reconciledBy [<urns>] when <when|-> reason <reason|->}; a finding reads
 * {@code <source> <target> resolutions [<tokens>] links [<relations>]}, its relations in the order the answer gives.
 */
public class TensionSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TENSION = "tension";

    private final ScenarioHttp http;

    /**
     * Steps over the scenario's exchanges.
     *
     * @param http the scenario's HTTP client and record
     */
    public TensionSteps(ScenarioHttp http) {
        this.http = http;
    }

    @Then("the graph's tension edges are exactly:")
    public void graphTensions(String expected) throws Exception {
        List<String> lines = new ArrayList<>();
        array("/edges").forEach(edge -> {
            if (TENSION.equals(edge.path("relation").asString())) {
                lines.add(tension(edge));
            }
        });
        assertThat(lines).as("the tension edges of the graph").isEqualTo(expectedLines(expected));
    }

    @Then("the context's tensions are exactly:")
    public void contextTensions(String expected) throws Exception {
        assertThat(lines("/tensions", TensionSteps::tension))
                .as("the tensions of the context")
                .isEqualTo(expectedLines(expected));
    }

    @Then("the activation's tensions are exactly:")
    public void activationTensions(String expected) throws Exception {
        assertThat(lines("/tensions", TensionSteps::tension))
                .as("the tensions of the activation")
                .isEqualTo(expectedLines(expected));
    }

    @Then("the activation's findings are exactly:")
    public void findings(String expected) throws Exception {
        assertThat(lines("/findings", TensionSteps::finding))
                .as("the findings of the activation")
                .isEqualTo(expectedLines(expected));
        array("/findings")
                .forEach(finding -> assertThat(finding.path("code").asString())
                        .as("the code of a finding")
                        .isEqualTo("unreconciled_tension"));
    }

    @Then("the edge {string} {string} {string} has no tension words")
    public void noWords(String source, String relation, String target) throws Exception {
        List<JsonNode> matching = new ArrayList<>();
        array("/edges").forEach(edge -> {
            if (edge.path("source").asString().equals(source)
                    && edge.path("relation").asString().equals(relation)
                    && edge.path("target").asString().equals(target)) {
                matching.add(edge);
            }
        });
        assertThat(matching).as("the edge %s %s %s", source, relation, target).hasSize(1);
        assertThat(matching.getFirst().has("when")
                        || matching.getFirst().has("reason")
                        || matching.getFirst().has("reconciledBy"))
                .as("the edge carries no when, reason or reconciledBy: %s", matching.getFirst())
                .isFalse();
    }

    /**
     * Follows a link of a finding of the last tensions answer with its method, If-Match set to that answer's revision,
     * quoted, and an empty body: what decision 12 tells a client to send.
     *
     * @param relation the link's relation
     * @param index the finding's index in {@code /findings}
     * @throws Exception when the answer is no JSON or the request fails
     */
    @When("I follow the link {string} of the finding {int} with the answer's revision and an empty body")
    public void followFindingLink(String relation, int index) throws Exception {
        JsonNode answer = JSON.readTree(http.body());
        JsonNode link = answer.path("findings").path(index).path("_links").path(relation);
        assertThat(link.path("href").isString())
                .as("finding %d has the link %s: %s", index, relation, answer)
                .isTrue();
        assertThat(link.has("templated"))
                .as("the link %s is concrete", relation)
                .isFalse();
        String revision = answer.path("revision").asString();
        http.perform(
                link.path("method").asString("GET"),
                CharterSteps.belowBase(link.path("href").asString()),
                Map.of(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE,
                        HttpHeaders.IF_MATCH,
                        "\"" + revision + "\""),
                "{}");
    }

    private static String tension(JsonNode tension) {
        List<String> by = new ArrayList<>();
        tension.path("reconciledBy").forEach(urn -> by.add(urn.asString()));
        String reconciled = tension.has("reconciledBy") ? "[" + String.join(", ", by) + "]" : "(no reconciledBy)";
        return tension.path("source").asString() + " tension "
                + tension.path("target").asString()
                + " reconciledBy " + reconciled
                + " when " + tension.path("when").asString("-")
                + " reason " + tension.path("reason").asString("-");
    }

    private static String finding(JsonNode finding) {
        List<String> resolutions = new ArrayList<>();
        finding.path("resolutions").forEach(resolution -> resolutions.add(resolution.asString()));
        List<String> links = new ArrayList<>();
        finding.path("_links").propertyNames().forEach(links::add);
        return finding.path("source").asString() + " " + finding.path("target").asString()
                + " resolutions [" + String.join(", ", resolutions) + "]"
                + " links [" + String.join(", ", links) + "]";
    }

    private JsonNode array(String pointer) throws Exception {
        JsonNode list = JSON.readTree(http.body()).at(pointer);
        assertThat(list.isArray())
                .as("%s is an array: %s", pointer, http.body())
                .isTrue();
        return list;
    }

    private List<String> lines(String pointer, Function<JsonNode, String> line) throws Exception {
        List<String> lines = new ArrayList<>();
        array(pointer).forEach(item -> lines.add(line.apply(item)));
        return lines;
    }

    private static List<String> expectedLines(String docString) {
        return docString
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
