package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the agent profile read (agent-profile-reads.feature): the {@code governance} list of the last
 * answer as lines of text, compared with the doc string in the order the answer gives. The order is part of what is
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

    @Then("the field at {string} is null")
    public void fieldIsNull(String pointer) throws Exception {
        JsonNode field = JSON.readTree(http.body()).at(pointer);
        assertThat(field.isMissingNode())
                .as("%s is there: %s", pointer, http.body())
                .isFalse();
        assertThat(field.isNull()).as("%s is null: %s", pointer, field).isTrue();
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
        JsonNode suggestion = entry.path("suggestion");
        if (suggestion.isObject()) {
            line.append(" suggested when \"")
                    .append(suggestion.path("when").asString())
                    .append("\" because \"")
                    .append(suggestion.path("reason").asString())
                    .append('"');
        }
        return line.toString();
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
