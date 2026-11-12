package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the effective graph and the effective action context (effective-graph-and-context.feature, PR C,
 * IC-11): each step reads one list of the last answer as lines of text and compares it with the doc string, in the order
 * the answer gives. The order is part of what is pinned: nodes, edges and layer overrides are sorted by the contract, and
 * the context lists its artifacts in URN order (ADR-0018 D3). Each artifact of the context is read with its
 * {@code appliesThrough} (mission effective-step-and-action-context, squad R7), so a constant value cannot pass a scenario
 * whose rows differ in it; an item without one reads {@code (no appliesThrough)} and fails every row.
 */
public class EffectiveGraphSteps {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScenarioHttp http;

    /**
     * Steps over the scenario's exchanges.
     *
     * @param http the scenario's HTTP client and record
     */
    public EffectiveGraphSteps(ScenarioHttp http) {
        this.http = http;
    }

    @Then("the graph holds exactly the nodes:")
    public void nodes(String expected) throws Exception {
        assertThat(lines("/nodes", node -> node.path("urn").asString()))
                .as("the nodes of the graph")
                .isEqualTo(expectedLines(expected));
    }

    @Then("the graph holds exactly the edges:")
    public void edges(String expected) throws Exception {
        assertThat(lines(
                        "/edges",
                        edge -> edge.path("source").asString() + " "
                                + edge.path("relation").asString() + " "
                                + edge.path("target").asString()))
                .as("the edges of the graph")
                .isEqualTo(expectedLines(expected));
    }

    @Then("the graph holds exactly the layer overrides:")
    public void layerOverrides(String expected) throws Exception {
        assertThat(lines(
                        "/layerOverrides",
                        replaced -> replaced.path("artifact").asString() + " won by " + side(replaced.path("winner"))
                                + " over " + side(replaced.path("loser"))))
                .as("the layer overrides of the graph")
                .isEqualTo(expectedLines(expected));
    }

    @Then("the context holds exactly the artifacts:")
    public void artifacts(String expected) throws Exception {
        assertThat(lines("/artifacts", EffectiveGraphSteps::artifact))
                .as("the artifacts of the context")
                .isEqualTo(expectedLines(expected));
    }

    /**
     * One artifact of the context, by its {@code <kind>:<id>}: {@code absent} when no item of {@code /artifacts} is that
     * artifact, {@code listed / <appliesThrough>} when exactly one is and carries that {@code appliesThrough}
     * (effective-context-scoping.feature).
     *
     * @param artifact the artifact, {@code <kind>:<id>}
     * @param expected {@code absent} or {@code listed / <appliesThrough>}
     * @throws Exception when the answer is no JSON
     */
    @Then("the context lists {string} as {string}")
    public void lists(String artifact, String expected) throws Exception {
        List<String> found = new ArrayList<>();
        JsonNode list = JSON.readTree(http.body()).at("/artifacts");
        assertThat(list.isArray()).as("/artifacts is an array: %s", http.body()).isTrue();
        list.forEach(item -> {
            if ((item.path("kind").asString() + ":" + item.path("artifactId").asString()).equals(artifact)) {
                found.add("listed / " + appliesThrough(item));
            }
        });
        assertThat(found.isEmpty() ? List.of("absent") : found)
                .as("how the context lists %s", artifact)
                .containsExactly(expected);
    }

    @Then("the context holds exactly the enhancements:")
    public void enhancements(String expected) throws Exception {
        assertThat(lines("/enhancements", EffectiveGraphSteps::enhancement))
                .as("the enhancements of the context")
                .isEqualTo(expectedLines(expected));
    }

    /**
     * The slots of an effective prompt (effective-step-prompt.feature, mission effective-step-and-action-context, WP07):
     * one line per fragment, {@code <slot> <level> <packId> "<packName>" <enhancement>}, in the answer's order, and
     * {@code <slot> (empty)} for a slot no fragment fills, so the order of the slots and of the fragments is pinned.
     *
     * @param expected the lines
     * @throws Exception when the answer is no JSON
     */
    @Then("the prompt holds exactly the slots:")
    public void slots(String expected) throws Exception {
        List<String> found = new ArrayList<>();
        String body = http.body();
        JsonNode slots = JSON.readTree(body).at("/slots");
        assertThat(slots.isArray()).as("/slots is an array: %s", body).isTrue();
        slots.forEach(slot -> {
            String id = slot.path("slot").asString();
            JsonNode fragments = slot.path("fragments");
            assertThat(fragments.isArray())
                    .as("the fragments of %s are an array: %s", id, body)
                    .isTrue();
            if (fragments.isEmpty()) {
                found.add(id + " (empty)");
            }
            fragments.forEach(fragment -> found.add(id + " "
                    + fragment.path("level").asString() + " "
                    + fragment.path("packId").asString() + " \""
                    + fragment.path("packName").asString() + "\" "
                    + fragment.path("enhancement").asString()));
        });
        assertThat(found).as("the slots of the prompt").isEqualTo(expectedLines(expected));
    }

    private static String side(JsonNode side) {
        return side.path("packId").asString() + "@" + side.path("level").asString();
    }

    private static String artifact(JsonNode artifact) {
        String line = artifact.path("kind").asString() + ":"
                + artifact.path("artifactId").asString() + " "
                + artifact.path("state").asString() + "/"
                + artifact.path("reason").asString() + " "
                + appliesThrough(artifact);
        JsonNode suggestion = artifact.path("suggestion");
        if (suggestion.isMissingNode()) {
            return line;
        }
        return line + " suggested when \"" + suggestion.path("when").asString() + "\" because \""
                + suggestion.path("reason").asString() + "\"";
    }

    private static String appliesThrough(JsonNode artifact) {
        JsonNode value = artifact.path("appliesThrough");
        return value.isString() ? value.asString() : "(no appliesThrough)";
    }

    private static String enhancement(JsonNode enhancement) {
        List<String> enhancers = new ArrayList<>();
        enhancement
                .path("enhancedBy")
                .forEach(enhancer -> enhancers.add(enhancer.path("kind").asString() + ":"
                        + enhancer.path("artifactId").asString()));
        return enhancement.path("target").path("kind").asString() + ":"
                + enhancement.path("target").path("id").asString() + " is enhanced by " + String.join(", ", enhancers);
    }

    /** The members of the array at the pointer, each as a line; the array must be there. */
    private List<String> lines(String pointer, java.util.function.Function<JsonNode, String> line) throws Exception {
        JsonNode list = JSON.readTree(http.body()).at(pointer);
        assertThat(list.isArray())
                .as("%s is an array: %s", pointer, http.body())
                .isTrue();
        List<String> lines = new ArrayList<>();
        list.forEach(item -> lines.add(line.apply(item)));
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
