package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the agent profile reads (agent-profile-reads.feature, effective-agent-profile.feature): the
 * {@code governance} list of the last answer as lines of text, compared with the doc string in the order the answer gives. The order is part of what is
 * pinned: the entries are sorted by urn. A line also says whether the entry links to the pack-scoped read of its
 * artifact and where that read is, so a scenario states which entries link and which do not.
 */
public class ProfileSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ARTIFACT_LINK_PREFIX = "/api/v1/packs/";

    private final ScenarioHttp http;

    /**
     * Steps over the scenario's exchanges.
     *
     * @param http the scenario's HTTP client and record
     */
    public ProfileSteps(ScenarioHttp http) {
        this.http = http;
    }

    @Then("the governance holds exactly the entries:")
    public void governance(String expected) throws Exception {
        JsonNode list = JSON.readTree(http.body()).path("governance");
        assertThat(list.isArray()).as("governance is an array: %s", http.body()).isTrue();
        List<String> lines = new ArrayList<>();
        list.forEach(entry -> lines.add(line(entry)));
        assertThat(lines)
                .as("the governance entries of the answer")
                .isEqualTo(expected.lines()
                        .map(String::strip)
                        .filter(text -> !text.isEmpty())
                        .toList());
    }

    @Then("the effective governance holds exactly the entries:")
    public void effectiveGovernance(String expected) throws Exception {
        JsonNode list = JSON.readTree(http.body()).path("governance");
        assertThat(list.isArray()).as("governance is an array: %s", http.body()).isTrue();
        List<String> lines = new ArrayList<>();
        list.forEach(entry -> lines.add(effectiveLine(entry)));
        assertThat(lines)
                .as("the effective governance entries of the answer")
                .isEqualTo(expected.lines()
                        .map(String::strip)
                        .filter(text -> !text.isEmpty())
                        .toList());
    }

    @Then("the field at {string} is an empty object")
    public void fieldIsAnEmptyObject(String pointer) throws Exception {
        JsonNode field = JSON.readTree(http.body()).at(pointer);
        assertThat(field.isObject())
                .as("%s is an object: %s", pointer, http.body())
                .isTrue();
        assertThat(field.size()).as("%s has no member: %s", pointer, field).isZero();
    }

    @Then("the field at {string} is null")
    public void fieldIsNull(String pointer) throws Exception {
        JsonNode field = JSON.readTree(http.body()).at(pointer);
        assertThat(field.isMissingNode())
                .as("%s is there: %s", pointer, http.body())
                .isFalse();
        assertThat(field.isNull()).as("%s is null: %s", pointer, field).isTrue();
    }

    /**
     * An entry under an activation as a line: the urn, how the walk reached it, the state and the reason the activation
     * gives it, the pack that holds the version in force and whether it links that pack's read; the edge last.
     */
    private static String effectiveLine(JsonNode entry) {
        StringBuilder line = new StringBuilder()
                .append(entry.path("kind").asString())
                .append(':')
                .append(entry.path("artifactId").asString())
                .append(' ')
                .append(entry.path("step").asString())
                .append(" via ")
                .append(entry.path("via").asString())
                .append(" hops ")
                .append(entry.path("hops").asInt())
                .append(' ')
                .append(entry.path("state").asString())
                .append('/')
                .append(entry.path("reason").asString())
                .append(" in ")
                .append(entry.path("packId").asString())
                .append(linked(entry) ? " linked" : " unlinked");
        line.append(edgeText(entry));
        return line.toString();
    }

    private static String line(JsonNode entry) {
        StringBuilder line = new StringBuilder()
                .append(entry.path("urn").asString())
                .append(' ')
                .append(entry.path("step").asString())
                .append(" via ")
                .append(entry.path("via").asString())
                .append(" hops ")
                .append(entry.path("hops").asInt());
        line.append(
                entry.path("held").asBoolean() ? " in " + entry.path("packId").asString() : " not held");
        line.append(linked(entry) ? " linked" : " unlinked");
        line.append(edgeText(entry));
        return line.toString();
    }

    /**
     * The edge of an entry as text: {@code  edge {}} for an edge that states neither word, otherwise the words it
     * states, {@code  edge {when "<when>" because "<reason>"}} (one alone is written alone). Every entry has an edge: a
     * missing or non-object member is a failure.
     */
    private static String edgeText(JsonNode entry) {
        JsonNode edge = entry.path("edge");
        assertThat(edge.isObject())
                .as("the edge of %s is an object: %s", entry, edge)
                .isTrue();
        StringBuilder text = new StringBuilder(" edge {");
        String separator = "";
        if (edge.has("when")) {
            text.append("when \"").append(edge.path("when").asString()).append('"');
            separator = " ";
        }
        if (edge.has("reason")) {
            text.append(separator)
                    .append("because \"")
                    .append(edge.path("reason").asString())
                    .append('"');
        }
        return text.append('}').toString();
    }

    /** Whether the entry links its artifact, and links the read in the pack that holds it. */
    private static boolean linked(JsonNode entry) {
        JsonNode href = entry.path("_links").path("artifact").path("href");
        if (!href.isString()) {
            return false;
        }
        String expected = ARTIFACT_LINK_PREFIX + entry.path("packId").asString() + "/artifacts/"
                + entry.path("kind").asString() + "/" + entry.path("artifactId").asString();
        assertThat(href.asString())
                .as("the artifact link of %s", entry.path("urn"))
                .isEqualTo(expected);
        return true;
    }
}
