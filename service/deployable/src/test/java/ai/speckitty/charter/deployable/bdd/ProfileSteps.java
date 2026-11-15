package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the agent profile reads (agent-profile-reads.feature, effective-agent-profile.feature), of the
 * profile enhancement refusal (profiles.feature) and of the profile selection (profile-selection.feature). The
 * profiles.feature steps author packs of one profile each, stored as files the way a pack configured before the rule
 * is, so the effective profile read, not the registration, judges them: a pack is configured at its level with a
 * descriptor of its own, an enhancing pack depends on the pack whose profile it enhances, and the effective read
 * stores an activation over every such pack that selects every such profile. The rest is the
 * {@code governance} list of the last answer as lines of text, compared with the doc string in the order the answer gives. The order is part of what is
 * pinned: the entries are sorted by urn. A line also says whether the entry links to the pack-scoped read of its
 * artifact and where that read is, so a scenario states which entries link and which do not.
 */
public class ProfileSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ARTIFACT_LINK_PREFIX = "/api/v1/packs/";

    private static final String ACTIVATION = "effective";

    private final ScenarioHttp http;
    private final ScenarioPacks packs;

    /** The packs this scenario's profile steps configured, by name, with their descriptor {@code pack_id}. */
    private final Map<String, String> descriptors = new LinkedHashMap<>();

    /** The profiles this scenario's profile steps wrote, each with the pack that holds it, in the order written. */
    private final Map<String, String> profiles = new LinkedHashMap<>();

    /**
     * Steps over the scenario's exchanges and packs.
     *
     * @param http the scenario's HTTP client and record
     * @param packs the scenario's packs
     */
    public ProfileSteps(ScenarioHttp http, ScenarioPacks packs) {
        this.http = http;
        this.packs = packs;
    }

    /**
     * One pack holding one agent profile whose specialization's primary focus is the given text; the pack is configured
     * at the level the step names, with a descriptor of its own.
     */
    @Given("the {word} pack {string} holds the agent profile {string} with the specialization {string}")
    public void packHoldsProfile(String level, String pack, String profileId, String focus) {
        configure(level, pack, null);
        writeProfile(level, pack, profileId, focus);
    }

    /**
     * A pack holding an agent profile that enhances one written by an earlier step and whose specialization leaves out
     * the base's: its primary focus is another text. The pack depends on the pack that holds the base, and the enhances
     * edge names the base qualified by that pack.
     */
    @Given("the {word} pack {string} holds an agent profile that enhances {string} and leaves out its specialization")
    public void packHoldsDroppingEnhancer(String level, String pack, String baseId) {
        String basePack = profiles.get(baseId);
        if (basePack == null) {
            throw new IllegalStateException("no earlier step wrote the agent profile " + baseId);
        }
        configure(level, pack, basePack);
        String enhancerId = baseId + "-" + pack;
        writeProfile(level, pack, enhancerId, "Something else than " + baseId + " does");
        packs.addFile(
                level(level),
                pack,
                "agent_profile.graph.yaml",
                String.join(
                        "\n",
                        "schema_version: '1.0'",
                        "generated_at: STATIC",
                        "nodes: []",
                        "edges:",
                        "- {source: 'agent_profile:" + enhancerId + "', target: '" + basePack + "::agent_profile:"
                                + baseId + "', relation: enhances}"));
    }

    /**
     * Stores an activation over every pack the profile steps configured, selecting every profile they wrote, and reads
     * the effective profile under it.
     */
    @When("I read the effective agent profile {string}")
    public void readEffectiveProfile(String profileId) throws Exception {
        String packList = descriptors.keySet().stream()
                .map(pack -> "{\"packId\":\"" + pack + "\"}")
                .collect(Collectors.joining(","));
        String selected = profiles.keySet().stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        http.perform(
                "PUT",
                "/activations/" + ACTIVATION,
                Map.of("Content-Type", "application/json"),
                "{\"schemaVersion\":2,\"packs\":[" + packList + "],\"selected\":{\"agent_profile\":[" + selected
                        + "]}}");
        assertThat(http.response().getStatus())
                .as("the activation over the scenario's packs is stored: %s", http.body())
                .isEqualTo(201);
        http.perform("GET", "/activations/" + ACTIVATION + "/artifacts/agent_profile/" + profileId, Map.of(), null);
    }

    /**
     * The effective read is refused with {@code 500 profile_enhancement_drops_field}: the content was stored before the
     * rule, so the packs are at fault, not the request. The refusal names the enhancing pack first in its packs member and
     * in its detail, and the field in its detail.
     */
    @Then("the request is refused naming the pack {string} and the field {string}")
    public void refusedNamingPackAndField(String pack, String field) throws Exception {
        JsonNode problem = JSON.readTree(http.body());
        assertThat(http.response().getStatus())
                .as("the status of %s", http.body())
                .isEqualTo(500);
        assertThat(problem.path("code").asString()).isEqualTo("profile_enhancement_drops_field");
        assertThat(problem.path("packs").path(0).path("packId").asString())
                .as("the enhancing pack, named first")
                .isEqualTo(pack);
        assertThat(problem.path("packs").path(0).path("name").asString()).isEqualTo(pack);
        assertThat(problem.path("detail").asString())
                .as("the detail names the pack and the field")
                .contains("of pack " + pack + " ")
                .contains(" its " + field + ";");
    }

    private void configure(String level, String pack, @Nullable String dependsOn) {
        if (descriptors.containsKey(pack)) {
            return;
        }
        String packId = String.format("01M7DFBF%018d", descriptors.size() + 1);
        descriptors.put(pack, packId);
        StringBuilder descriptor = new StringBuilder()
                .append("pack_id: ")
                .append(packId)
                .append('\n')
                .append("pack_version: 1.0.0\n")
                .append("parent_pack: null\n")
                .append("name: ")
                .append(pack)
                .append('\n')
                .append("pack_level: ")
                .append(level)
                .append('\n');
        if (dependsOn != null) {
            descriptor
                    .append("depends_on:\n")
                    .append("  - pack_id: ")
                    .append(descriptors.get(dependsOn))
                    .append('\n')
                    .append("    range: \">=1.0,<2\"\n");
        }
        packs.addFile(level(level), pack, "pack.yaml", descriptor.toString());
    }

    private void writeProfile(String level, String pack, String profileId, String focus) {
        profiles.put(profileId, pack);
        packs.addFile(
                level(level),
                pack,
                "agent_profiles/" + profileId + ".agent.yaml",
                String.join(
                        "\n",
                        "profile-id: " + profileId,
                        "name: " + profileId,
                        "role: reviewer",
                        "purpose: Review the work.",
                        "specialization:",
                        "  primary-focus: " + focus));
    }

    private static PackLevel level(String token) {
        return PackLevel.fromToken(token).orElseThrow(() -> new IllegalArgumentException("no pack level " + token));
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

    /**
     * The candidates of a profile selection (profile-selection.feature) as lines, {@code <id> in <pack> as <fit>}, in the
     * answer's order. Each candidate must link {@code effective-artifact}, the read of its profile under the activation
     * the request named.
     */
    @Then("the selection answers exactly:")
    public void selection(String expected) throws Exception {
        JsonNode list = JSON.readTree(http.body()).path("candidates");
        assertThat(list.isArray()).as("candidates is an array: %s", http.body()).isTrue();
        // The path is /activations/{activationId}/roles/{role}/profile-selection.
        String path = http.path();
        int start = path.indexOf('/', 1) + 1;
        String activation = path.substring(start, path.indexOf('/', start));
        List<String> lines = new ArrayList<>();
        list.forEach(candidate -> {
            String id = candidate.path("id").asString();
            assertThat(candidate
                            .path("_links")
                            .path("effective-artifact")
                            .path("href")
                            .asString())
                    .as("the effective-artifact link of %s", id)
                    .isEqualTo("/api/v1/activations/" + activation + "/artifacts/agent_profile/" + id);
            lines.add(id + " in " + candidate.path("pack").asString() + " as "
                    + candidate.path("fit").asString());
        });
        assertThat(lines)
                .as("the candidates of the selection")
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
