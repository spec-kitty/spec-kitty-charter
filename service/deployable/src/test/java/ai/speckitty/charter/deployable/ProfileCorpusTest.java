package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.application.ProfileRead;
import ai.speckitty.charter.profiles.application.ProfileSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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
 * NFR-001, SC-003 and SC-004 over the real corpus: every agent profile of the pinned spec-kitty-open-packs checkout, the
 * 26 of {@code built-in} and the 5 of {@code qa}, is read through the service's {@link ProfileSource} and through its
 * REST edge, and the body means what the pack YAML means. The expected wire is built here from the YAML alone, by the
 * rules the contract states (camel case, a list that is not declared is {@code []}, a field the pack schema defaults is
 * left out, a field it allows to be null is {@code null}, an empty specialization context or protocol is {@code null}),
 * never from the code under test. The governance of {@code java-jenny} lists {@code styleguide:java-conventions} as a
 * {@code requires} entry (SC-003), which is what made the pin move to {@code a2a9379}.
 *
 * <p>Runs under {@code :deployable:openPacksProfiles} (tag {@code open-packs}), which supplies the checkout. Nothing
 * here skips: a missing, moved or dirty checkout fails.
 */
@Tag("open-packs")
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ProfileCorpusTest.TOKEN)
@AutoConfigureMockMvc
class ProfileCorpusTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    private static final int BUILT_IN_PROFILES = 26;
    private static final int QA_PROFILES = 5;
    private static final List<String> PACKS = List.of("built-in", "qa");

    private static @Nullable Path configRoot;
    private static Path corpus = Path.of("");

    @Autowired
    private ProfileSource source;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @BeforeAll
    static void theCheckoutIsAtThePinAndClean() {
        corpus = checkout();
        String pin = System.getProperty("charter.openPacksRef");
        assertThat(pin).as("charter.openPacksRef").isNotNull().matches(SHA);
        assertThat(git("rev-parse", "HEAD").strip())
                .as("the open-packs checkout is at the pin %s (git -C %s checkout %s)", pin, corpus, pin)
                .isEqualTo(pin);
        assertThat(git("status", "--porcelain", "--untracked-files=all"))
                .as("the open-packs checkout has uncommitted changes")
                .isBlank();
    }

    @DynamicPropertySource
    static void configRootHoldingTheCorpusPacks(DynamicPropertyRegistry registry) throws IOException {
        Path source = checkout();
        Path created = Files.createTempDirectory("charter-profile-corpus");
        configRoot = created;
        for (String pack : PACKS) {
            copy(source.resolve("packs").resolve(pack), created.resolve("packs").resolve(pack));
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

    private static Path checkout() {
        String dir = System.getProperty("charter.openPacksDir");
        if (dir == null || !Files.isDirectory(Path.of(dir, "packs", "built-in"))) {
            throw new IllegalStateException(
                    "No spec-kitty-open-packs checkout at '" + dir
                            + "' (expected <checkout>/packs/built-in). Pass -PopenPacksDir=<checkout>. The corpus test never skips.");
        }
        return Path.of(dir);
    }

    private static String git(String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", corpus.toString()));
        command.addAll(List.of(args));
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("Failed: " + command + "\n" + new String(out, StandardCharsets.UTF_8));
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path file : walk.toList()) {
                Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }

    /** The profile files of a pack of the corpus, by profile id, in file-name order. */
    private static Map<String, Map<String, Object>> profilesOf(String pack) throws IOException {
        Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
        try (Stream<Path> files =
                Files.list(corpus.resolve("packs").resolve(pack).resolve("agent_profiles"))) {
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
        wire.put("roles", list(yaml, "roles"));
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
    void theCorpusHoldsTheThirtyOneProfilesOfTheTwoPacks() throws IOException {
        assertThat(profilesOf("built-in")).as("built-in profiles").hasSize(BUILT_IN_PROFILES);
        assertThat(profilesOf("qa")).as("qa profiles").hasSize(QA_PROFILES);
    }

    @Test
    void everyProfileOfTheCorpusIsReadThroughTheProfileSourceAndItsBodyMeansWhatItsYamlMeans() throws Exception {
        List<String> problems = new ArrayList<>();
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
                JsonNode expected = json.readTree(json.writeValueAsString(expectedProfile(profile.getValue())));
                if (!expected.equals(body.get("agentProfile"))) {
                    problems.add(pack + "/" + id + ": the body differs from the YAML\n  expected " + expected
                            + "\n  answered " + body.get("agentProfile"));
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(read).as("profiles read").isEqualTo(BUILT_IN_PROFILES + QA_PROFILES);
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
        assertThat(entry.get("_links").get("artifact").get("href").asString())
                .isEqualTo("/api/v1/packs/built-in/artifacts/styleguide/java-conventions");
    }

    /**
     * The governance of {@code EffectiveAgentProfileRead.java-jenny.yaml}, the contract's example, over the real corpus: the
     * profile under an activation that selects what this repository's {@code charter.yaml} selects (the two toolguides the
     * pin does not hold are left out). Every listed entry, its relation, its state and the words of its edge are the
     * pack's; DIRECTIVE_024 is a {@code requires} target the charter does not select, so it is not listed.
     */
    @Test
    void javaJennyUnderTheCharterSelectionListsWhatTheContractExampleShows() throws Exception {
        String selected = "{\"schemaVersion\":2,\"packs\":[{\"packId\":\"built-in\"}],\"selected\":{"
                + "\"agent_profile\":[\"java-jenny\"],"
                + "\"paradigm\":[\"behaviour-driven-development\",\"specification-by-example\","
                + "\"domain-driven-design\",\"c4-incremental-detail-modeling\"],"
                + "\"directive\":[\"DIRECTIVE_001\",\"DIRECTIVE_003\",\"DIRECTIVE_010\",\"DIRECTIVE_025\","
                + "\"DIRECTIVE_030\",\"DIRECTIVE_032\",\"DIRECTIVE_034\",\"DIRECTIVE_037\",\"DIRECTIVE_041\","
                + "\"DIRECTIVE_051\",\"RECONCILE_CHANGE_SCOPE_TENSIONS\",\"USE_C4_MODEL_TECHNIQUES\"],"
                + "\"styleguide\":[\"java-conventions\",\"given-when-then-authoring\",\"testing-principles\","
                + "\"test-desiderata-and-boundaries\"],"
                + "\"toolguide\":[\"gherkin\",\"java-supply-chain\"]}}";
        assertThat(mvc.perform(put("/api/v1/activations/project")
                                .header("Authorization", "Bearer " + TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selected))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        JsonNode body =
                json.readTree(mvc.perform(get("/api/v1/activations/project/artifacts/agent_profile/java-jenny?depth=1")
                                .header("Authorization", "Bearer " + TOKEN)
                                .accept(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());

        List<String> entries = new ArrayList<>();
        for (JsonNode entry : body.get("governance")) {
            JsonNode words = entry.path("suggestion");
            entries.add(entry.get("kind").asString() + ":"
                    + entry.get("artifactId").asString() + " "
                    + entry.get("relation").asString() + " via "
                    + entry.get("via").asString() + " hops "
                    + entry.get("hops").asInt() + " " + entry.get("state").asString() + "/"
                    + entry.get("reason").asString()
                    + (words.isObject()
                            ? " when \"" + words.path("when").asString() + "\" because \""
                                    + words.path("reason").asString() + "\""
                            : ""));
        }
        assertThat(entries)
                .containsExactly(
                        "directive:DIRECTIVE_001 requires via directive:DIRECTIVE_051 hops 2 active/selected",
                        "directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 active/selected",
                        "directive:DIRECTIVE_025 requires via agent_profile:java-jenny hops 1 active/selected",
                        "directive:DIRECTIVE_030 requires via agent_profile:java-jenny hops 1 active/selected",
                        "directive:DIRECTIVE_034 requires via agent_profile:java-jenny hops 1 active/selected",
                        "directive:DIRECTIVE_041 suggests via agent_profile:java-jenny hops 1 active/selected"
                                + " when \"when keeping the tests they write a clear scaffold rather than friction\""
                                + " because \"An implementer-role profile should reach the tests-as-scaffold hub when"
                                + " writing tests. Composition-only in the profile channel, authored per the #3063"
                                + " family-D operator attestation.\"",
                        "directive:DIRECTIVE_051 requires via agent_profile:java-jenny hops 1 active/selected",
                        "directive:DISCIPLINED_REFACTORING suggests via agent_profile:java-jenny hops 1"
                                + " inactive/not_selected when \"when tidying code, encountering long classes/methods,"
                                + " or discovering convoluted logic\" because \"An implementer-role profile should reach"
                                + " the disciplined-refactoring directive when restructuring code. Composition-only under"
                                + " today's traversal, authored per the #3063 family-B operator attestation.\"",
                        "directive:USE_MUTATION_TESTING_TO_VALIDATE_TEST_QUALITY suggests via agent_profile:java-jenny"
                                + " hops 1 inactive/not_selected when \"when assessing whether the tests around a change"
                                + " actually constrain its behaviour\" because \"An implementer-role profile should reach"
                                + " the mutation hub when assessing test quality. Composition-only in the profile channel,"
                                + " authored per the #3063 family-D operator attestation.\"",
                        "styleguide:java-conventions requires via agent_profile:java-jenny hops 1 active/selected");
    }
}
