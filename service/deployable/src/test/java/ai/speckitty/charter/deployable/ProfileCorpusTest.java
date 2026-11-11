package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.application.ProfileRead;
import ai.speckitty.charter.profiles.application.ProfileSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NFR-001, SC-003 and SC-004 over the example packs (ADR-0070): every agent profile of the three example packs,
 * {@code built-in}, {@code org-base} and {@code project}, is read through the service's {@link ProfileSource} and
 * through its REST edge, and the body means what the pack YAML means. The expected wire is built here from the YAML
 * alone, by the rules the contract states (camel case, a list that is not declared is {@code []}, a field the pack
 * schema defaults is left out, a field it allows to be null is {@code null}, an empty specialization context or protocol
 * is {@code null}), never from the code under test. The governance of the example implementer {@code java-jenny} lists
 * {@code styleguide:java-conventions} as a {@code requires} entry (SC-003).
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ProfileCorpusTest.TOKEN)
@AutoConfigureMockMvc
class ProfileCorpusTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final Map<String, Integer> PROFILES =
            Map.of(ExamplePacks.BUILT_IN, 5, ExamplePacks.ORG, 1, ExamplePacks.PROJECT, 1);
    private static final List<String> PACKS = ExamplePacks.names();

    private static @Nullable Path configRoot;
    private static final Path CORPUS = ExamplePacks.packsDirectory();

    @Autowired
    private ProfileSource source;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void configRootHoldingTheExamplePacks(DynamicPropertyRegistry registry) throws IOException {
        Path created = Files.createTempDirectory("charter-profile-corpus");
        configRoot = created;
        for (String pack : PACKS) {
            ExamplePacks.copyOf(pack, created.resolve("packs"));
        }
        registry.add("charter.config-root", created::toString);
    }

    @AfterAll
    static void deleteTheCopy() throws IOException {
        // Only the temporary directory this class created is ever removed: nothing is deleted when it was never made.
        if (configRoot == null || !configRoot.startsWith(Path.of(System.getProperty("java.io.tmpdir")))) {
            return;
        }
        try (Stream<Path> walk = Files.walk(configRoot)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** The profile files of a pack of the corpus, by profile id, in file-name order. */
    private static Map<String, Map<String, Object>> profilesOf(String pack) throws IOException {
        Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(CORPUS.resolve(pack).resolve("agent_profiles"))) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".agent.yaml"))
                    .sorted()
                    .toList()) {
                Object loaded = new Load(LoadSettings.builder().build()).loadFromString(Files.readString(file));
                @SuppressWarnings("unchecked")
                Map<String, Object> yaml = (Map<String, Object>) loaded;
                profiles.put((String) yaml.get("profile-id"), yaml);
            }
        }
        return profiles;
    }

    // ---- the expected wire, from the YAML alone --------------------------------------------------------------------

    private static Map<String, Set<Map<String, String>>> graphEdgeWords() throws IOException {
        return PackGraphEdges.words(CORPUS, PACKS);
    }

    /**
     * Every entry of the wire governance carries an {@code edge} (an object, never null or absent) equal to the words of
     * the edge written from its {@code via} to it with its relation; {@code requires_of_suggested} entries are
     * {@code requires} edges. Counts the entries that carry words, so that the check is seen to bite.
     */
    private static void edgeProblems(
            String where,
            JsonNode governance,
            Map<String, Set<Map<String, String>>> written,
            List<String> problems,
            int[] worded) {
        for (JsonNode entry : governance) {
            JsonNode edge = entry.get("edge");
            String urn = entry.has("urn")
                    ? entry.get("urn").asString()
                    : entry.get("kind").asString() + ":"
                            + entry.get("artifactId").asString();
            String key =
                    entry.get("via").asString() + " " + entry.get("relation").asString() + " " + urn;
            if (edge == null || !edge.isObject()) {
                problems.add(where + ": " + key + " has no edge object: " + edge);
                continue;
            }
            Map<String, String> actual = new LinkedHashMap<>();
            edge.properties()
                    .forEach(member ->
                            actual.put(member.getKey(), member.getValue().asString()));
            if (!written.getOrDefault(key, Set.of()).contains(actual)) {
                problems.add(
                        where + ": the edge of " + key + " is " + actual + ", the graph writes " + written.get(key));
            }
            if (!actual.isEmpty()) {
                worded[0]++;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> yaml, String key) {
        Object value = yaml.get(key);
        return value == null ? List.of() : (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> yaml, String key) {
        Object value = yaml.get(key);
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    /** Puts the member only when the file declares it: the pack schema's defaulted fields are left out otherwise. */
    private static void putIfDeclared(Map<String, Object> wire, String name, Map<String, Object> yaml, String key) {
        Object value = yaml.get(key);
        if (value != null) {
            wire.put(name, value instanceof Number || value instanceof Boolean ? value : String.valueOf(value));
        }
    }

    private static Map<String, Object> expectedProfile(Map<String, Object> yaml) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("profileId", yaml.get("profile-id"));
        wire.put("name", yaml.get("name"));
        putIfDeclared(wire, "description", yaml, "description");
        putIfDeclared(wire, "schemaVersion", yaml, "schema-version");
        wire.put("role", yaml.get("role"));
        wire.put("altRoles", list(yaml, "alt-roles"));
        wire.put("avatarImage", yaml.get("avatar-image"));
        wire.put("capabilities", list(yaml, "capabilities"));
        putIfDeclared(wire, "routingPriority", yaml, "routing-priority");
        putIfDeclared(wire, "maxConcurrentTasks", yaml, "max-concurrent-tasks");
        wire.put("model", yaml.get("model"));
        wire.put("effort", yaml.get("effort"));
        putIfDeclared(wire, "sentinel", yaml, "sentinel");
        wire.put("purpose", yaml.get("purpose"));
        Map<String, Object> specialization = map(yaml, "specialization");
        Map<String, Object> expectedSpecialization = new LinkedHashMap<>();
        expectedSpecialization.put("primaryFocus", specialization.get("primary-focus"));
        putIfDeclared(expectedSpecialization, "secondaryAwareness", specialization, "secondary-awareness");
        putIfDeclared(expectedSpecialization, "avoidanceBoundary", specialization, "avoidance-boundary");
        putIfDeclared(expectedSpecialization, "successDefinition", specialization, "success-definition");
        wire.put("specialization", expectedSpecialization);
        Map<String, Object> collaboration = map(yaml, "collaboration");
        Map<String, Object> expectedCollaboration = new LinkedHashMap<>();
        expectedCollaboration.put("handoffTo", list(collaboration, "handoff-to"));
        expectedCollaboration.put("handoffFrom", list(collaboration, "handoff-from"));
        expectedCollaboration.put("worksWith", list(collaboration, "works-with"));
        expectedCollaboration.put("outputArtifacts", list(collaboration, "output-artifacts"));
        expectedCollaboration.put("operatingProcedures", list(collaboration, "operating-procedures"));
        expectedCollaboration.put("canonicalVerbs", list(collaboration, "canonical-verbs"));
        wire.put("collaboration", expectedCollaboration);
        wire.put(
                "modeDefaults",
                list(yaml, "mode-defaults").stream()
                        .map(mode -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) mode;
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("mode", m.get("mode"));
                            out.put("description", m.get("description"));
                            out.put("useCase", m.get("use-case"));
                            return out;
                        })
                        .toList());
        putIfDeclared(wire, "initializationDeclaration", yaml, "initialization-declaration");
        wire.put("specializationContext", expectedContext(map(yaml, "specialization-context")));
        wire.put("appliesToLanguages", list(yaml, "applies_to_languages"));
        wire.put(
                "directiveReferences",
                list(yaml, "directive-references").stream()
                        .map(ProfileCorpusTest::directiveReference)
                        .toList());
        wire.put("tacticReferences", references(yaml, "tactic-references"));
        wire.put("toolguideReferences", references(yaml, "toolguide-references"));
        wire.put("styleguideReferences", references(yaml, "styleguide-references"));
        wire.put("selfReviewProtocol", expectedProtocol(map(yaml, "self-review-protocol")));
        wire.put("excluding", expectedExcluding(yaml.get("excluding")));
        return wire;
    }

    /** Null when the file declares no context, and when it declares one with six empty lists. */
    private static Object expectedContext(Map<String, Object> context) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("languages", list(context, "languages"));
        wire.put("frameworks", list(context, "frameworks"));
        wire.put("filePatterns", list(context, "file-patterns"));
        wire.put("domainKeywords", list(context, "domain-keywords"));
        wire.put("writingStyle", list(context, "writing-style"));
        wire.put("complexityPreference", list(context, "complexity-preference"));
        return wire.values().stream().allMatch(value -> ((List<?>) value).isEmpty()) ? null : wire;
    }

    /** Null when the file declares no protocol, and when it declares one with no steps. */
    private static Object expectedProtocol(Map<String, Object> protocol) {
        List<Object> steps = list(protocol, "steps");
        if (steps.isEmpty()) {
            return null;
        }
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put(
                "steps",
                steps.stream()
                        .map(step -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> s = (Map<String, Object>) step;
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("name", s.get("name"));
                            out.put("command", s.get("command"));
                            out.put("gate", s.get("gate"));
                            return out;
                        })
                        .toList());
        return wire;
    }

    private static Object expectedExcluding(Object excluding) {
        Map<String, Object> wire = new LinkedHashMap<>();
        if (excluding == null) {
            return null;
        }
        if (excluding instanceof List<?> fields) {
            wire.put("wholeFields", fields);
            wire.put("fieldValues", null);
            return wire;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) excluding;
        wire.put("wholeFields", null);
        wire.put(
                "fieldValues",
                values.entrySet().stream()
                        .map(entry -> {
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("field", entry.getKey());
                            out.put("values", entry.getValue() == null ? List.of() : entry.getValue());
                            return out;
                        })
                        .toList());
        return wire;
    }

    private static Map<String, Object> directiveReference(Object reference) {
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) reference;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", String.valueOf(r.get("code")));
        out.put("name", r.get("name"));
        out.put("rationale", r.get("rationale"));
        return out;
    }

    private static List<Object> references(Map<String, Object> yaml, String key) {
        return list(yaml, key).stream()
                .map(reference -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> r = (Map<String, Object>) reference;
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", r.get("id"));
                    out.put("rationale", r.get("rationale"));
                    return (Object) out;
                })
                .toList();
    }

    // ---- the tests --------------------------------------------------------------------------------------------------

    @Test
    void theExamplePacksHoldSevenProfilesInTheThreePacks() throws IOException {
        for (String pack : PACKS) {
            assertThat(profilesOf(pack)).as("%s profiles", pack).hasSize(PROFILES.get(pack));
        }
    }

    @Test
    void everyExampleProfileIsReadThroughTheProfileSourceAndItsBodyMeansWhatItsYamlMeans() throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, Set<Map<String, String>>> written = graphEdgeWords();
        int[] worded = {0};
        int read = 0;
        for (String pack : PACKS) {
            for (Map.Entry<String, Map<String, Object>> profile :
                    profilesOf(pack).entrySet()) {
                String id = profile.getKey();
                ProfileRead viaSource = source.read(new PackIdentifier(pack), id, 1);
                read++;
                if (!viaSource.profile().localId().equals(id)) {
                    problems.add(pack + "/" + id + ": the source answered "
                            + viaSource.profile().localId());
                }
                problems.addAll(governanceProblems(pack, id, viaSource));
                MockHttpServletResponse response = mvc.perform(
                                get("/api/v1/packs/" + pack + "/artifacts/agent_profile/" + id + "?depth=1")
                                        .header("Authorization", "Bearer " + TOKEN)
                                        .accept(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse();
                if (response.getStatus() != 200) {
                    problems.add(pack + "/" + id + ": answered " + response.getStatus() + " "
                            + response.getContentAsString());
                    continue;
                }
                JsonNode body = json.readTree(response.getContentAsString());
                edgeProblems(pack + "/" + id, body.get("governance"), written, problems, worded);
                JsonNode expected = json.readTree(json.writeValueAsString(expectedProfile(profile.getValue())));
                if (!expected.equals(body.get("agentProfile"))) {
                    problems.add(pack + "/" + id + ": the body differs from the YAML\n  expected " + expected
                            + "\n  answered " + body.get("agentProfile"));
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(worded[0]).as("entries whose edge states a word").isPositive();
        assertThat(read)
                .as("profiles read")
                .isEqualTo(
                        PROFILES.values().stream().mapToInt(Integer::intValue).sum());
    }

    /** The invariants of a governance answer, whatever the profile. */
    private static List<String> governanceProblems(String pack, String id, ProfileRead read) {
        List<String> problems = new ArrayList<>();
        List<String> urns =
                read.governance().stream().map(e -> e.urn().toString()).toList();
        if (!urns.equals(urns.stream().sorted().toList())) {
            problems.add(pack + "/" + id + ": the governance is not in urn order");
        }
        if (urns.contains("agent_profile:" + id)) {
            problems.add(pack + "/" + id + ": the governance lists the profile itself");
        }
        if (urns.stream().distinct().count() != urns.size()) {
            problems.add(pack + "/" + id + ": an artifact is listed twice");
        }
        for (GovernanceEntry entry : read.governance()) {
            boolean parentListed = entry.via().toString().equals("agent_profile:" + id)
                    || urns.contains(entry.via().toString());
            if (!parentListed) {
                problems.add(pack + "/" + id + ": " + entry.urn() + " names " + entry.via()
                        + " as via, which is not listed");
            }
        }
        return problems;
    }

    @Test
    void javaJennyRequiresTheJavaConventionsStyleguideAndTheEntryLinksItsRead() throws Exception {
        ProfileRead read = source.read(new PackIdentifier("built-in"), "java-jenny", 0);

        GovernanceEntry conventions = read.governance().stream()
                .filter(entry -> entry.urn().toString().equals("styleguide:java-conventions"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "java-jenny does not require styleguide:java-conventions: " + read.governance()));
        assertThat(conventions.step()).isEqualTo(GovernanceEntry.Step.REQUIRES);
        assertThat(conventions.via().toString()).isEqualTo("agent_profile:java-jenny");
        assertThat(conventions.held()).isTrue();
        assertThat(conventions.holder().orElseThrow().value()).isEqualTo("built-in");
        JsonNode body = json.readTree(mvc.perform(get("/api/v1/packs/built-in/artifacts/agent_profile/java-jenny")
                        .header("Authorization", "Bearer " + TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode entry = null;
        for (JsonNode candidate : body.get("governance")) {
            if (Objects.equals(candidate.get("urn").asString(), "styleguide:java-conventions")) {
                entry = candidate;
            }
        }
        assertThat(entry).as("the wire entry of styleguide:java-conventions").isNotNull();
        assertThat(entry.get("step").asString()).isEqualTo("requires");
        assertThat(entry.get("edge").has("when")).isFalse();
        assertThat(entry.get("edge").get("reason").asString())
                .startsWith("A language-specialist profile requires the conventions styleguide");
        assertThat(entry.get("_links").get("artifact").get("href").asString())
                .isEqualTo("/api/v1/packs/built-in/artifacts/styleguide/java-conventions");
    }

    /**
     * The governance of {@code EffectiveAgentProfileRead.java-jenny.yaml}, the contract's example, over the built-in
     * example pack: the profile under an activation that selects it, three directives and two style guides. Every listed
     * entry, its relation, its state and the words of its edge are the pack's; DIRECTIVE_001 is reached through
     * DIRECTIVE_010 (hops 2) and is locked by the pack policy; the tactic java-jenny suggests is out of force; the procedure it requires is not selected, so
     * it is not listed (a requires target is not a suggestion, and no preset of the pack requires it).
     */
    @Test
    void javaJennyUnderASelectionListsWhatTheContractExampleShows() throws Exception {
        String selected = "{\"schemaVersion\":2,\"packs\":[{\"packId\":\"built-in\"}],\"selected\":{"
                + "\"agent_profile\":[\"java-jenny\"],"
                + "\"directive\":[\"DIRECTIVE_001\",\"DIRECTIVE_003\",\"DIRECTIVE_010\"],"
                + "\"styleguide\":[\"java-conventions\",\"boring-code-review\"]}}";
        assertThat(mvc.perform(put("/api/v1/activations/project")
                                .header("Authorization", "Bearer " + TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selected))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        String answer = mvc.perform(get("/api/v1/activations/project/artifacts/agent_profile/java-jenny?depth=1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = json.readTree(answer);

        List<String> problems = new ArrayList<>();
        int[] worded = {0};
        edgeProblems("java-jenny under the selection", body.get("governance"), graphEdgeWords(), problems, worded);
        assertThat(problems).isEmpty();
        assertThat(worded[0]).as("entries whose edge states a word").isPositive();
        List<String> entries = new ArrayList<>();
        for (JsonNode entry : body.get("governance")) {
            JsonNode words = entry.get("edge");
            assertThat(words.isObject()).as("the edge of %s", entry).isTrue();
            List<String> stated = new ArrayList<>();
            if (words.has("when")) {
                stated.add("when \"" + words.get("when").asString() + "\"");
            }
            if (words.has("reason")) {
                stated.add("because \"" + words.get("reason").asString() + "\"");
            }
            entries.add(entry.get("kind").asString() + ":"
                    + entry.get("artifactId").asString() + " "
                    + entry.get("relation").asString() + " via "
                    + entry.get("via").asString() + " hops "
                    + entry.get("hops").asInt() + " " + entry.get("state").asString() + "/"
                    + entry.get("reason").asString() + " edge {" + String.join(" ", stated) + "}");
        }
        assertThat(entries)
                .containsExactly(
                        "directive:DIRECTIVE_001 requires via directive:DIRECTIVE_010 hops 2 active/locked edge {}",
                        "directive:DIRECTIVE_003 requires via agent_profile:java-jenny hops 1 active/selected"
                                + " edge {when \"when an implementation choice departs from the plan\" because"
                                + " \"An implementer records the decision it takes, so the reviewer reads why the code"
                                + " differs from the plan.\"}",
                        "directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 active/selected edge {}",
                        "styleguide:boring-code-review suggests via agent_profile:java-jenny hops 1 active/required"
                                + " edge {when \"when answering review comments on the code it wrote\" because"
                                + " \"An implementer-role profile reaches the review styleguide when it answers a"
                                + " review.\"}",
                        "styleguide:java-conventions requires via agent_profile:java-jenny hops 1 active/selected"
                                + " edge {because \"A language-specialist profile requires the conventions styleguide of"
                                + " each of its applies_to_languages (java).\"}",
                        "tactic:autonomous-operation-protocol suggests via agent_profile:java-jenny hops 1"
                                + " inactive/not_selected edge {when \"when it works through a task without an operator at"
                                + " hand\" because \"An implementer-role profile reaches the autonomous-operation tactic"
                                + " for unattended work.\"}");
    }
}
