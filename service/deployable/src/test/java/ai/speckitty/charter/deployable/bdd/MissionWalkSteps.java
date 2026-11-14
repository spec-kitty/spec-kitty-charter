package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.api.PackSourceSwitch;
import ai.speckitty.charter.packs.infra.fetch.PackSourceProperties;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The glue of journey 12 (a mission walk under one activation, #572): business-language steps over charter's example
 * packs that reach every read by following links from the API root, as a client would. No step builds a path below the
 * root, apart from the pack registration (`POST /packs`), which the root does not link yet.
 *
 * <p>The walk steps collect every fault of every step before they fail, so one run names all the steps a fixture or a
 * change breaks, not only the first.
 */
public class MissionWalkSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LINKS = "_links";
    private static final String EFFECTIVE_ARTIFACT = "effective-artifact";

    /**
     * The buckets of an activation's skipped report that say the document asked for something the service did not
     * give: a pin, a required artifact or an override it could not hold or apply, an id that names nothing, a deny the
     * floor overrides. The other buckets (scope, language, layer overrides) are the effective-set algebra at work.
     */
    private static final List<String> REFUSED = List.of(
            "pinNotHeld", "requiredNotHeld", "requiredNotInForce", "staleIds", "overrideNotApplied", "denyNotApplied");

    /** A slot marker pair of a prompt: {@code <!-- <slot>:start -->} (ADR-0066). */
    private static final Pattern SLOT_START = Pattern.compile("<!--\\s*([a-z0-9-]+:[a-z0-9-]+):start\\s*-->");

    /** The business names of the kinds a team reads, as the journey writes them. */
    private static final Map<String, String> KINDS = Map.of(
            "agent profile", "agent_profile",
            "mission type", "mission_type",
            "procedure", "procedure");

    private final ScenarioHttp http;
    private final PackSourceSwitch switches;
    private final PackSourceProperties properties;
    private final PackRegistrationSteps registration;

    private @Nullable JsonNode activation;
    private @Nullable JsonNode document;
    private @Nullable JsonNode readItem;
    private final List<StepWalk> walked = new ArrayList<>();

    /** What the walk of one step answered: the step read, its action context and its prompt. */
    private record StepWalk(
            String step, int stepStatus, int contextStatus, JsonNode context, int promptStatus, JsonNode prompt) {}

    /**
     * The journey's steps.
     *
     * @param http the scenario's HTTP client and record
     * @param switches whether each pack source kind is on
     * @param properties the bound source configuration, whose filesystem root receives the registered example pack
     * @param registration the registration steps, which switch registration on and poll a registration to its end
     */
    public MissionWalkSteps(
            ScenarioHttp http,
            PackSourceSwitch switches,
            PackSourceProperties properties,
            PackRegistrationSteps registration) {
        this.http = http;
        this.switches = switches;
        this.properties = properties;
        this.registration = registration;
    }

    @Before
    public void forgetTheWalk() {
        activation = null;
        document = null;
        readItem = null;
        walked.clear();
    }

    // ---- packs and the activation ---------------------------------------------------------------------------------

    /**
     * Copies one of charter's example packs under the filesystem source root, as {@code packs/<name>}, and registers it
     * through {@code POST /packs} with a filesystem source; the registration must succeed.
     *
     * @param name the example pack, {@code org-base} or {@code project}
     */
    @Given("the example pack {string} is registered from the filesystem source")
    public void registerExamplePack(String name) throws Exception {
        Path root = properties.filesystem().root();
        assertThat(root)
                .as("the scenarios' context sets charter.pack-sources.filesystem.root")
                .isNotNull();
        ExamplePacks.copyOf(name, Objects.requireNonNull(root).resolve("packs"));
        switches.set(PackSourceKind.FILESYSTEM, true);
        registration.switchedOn();
        http.perform(
                "POST",
                "/packs",
                Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
                JSON.writeValueAsString(Map.of("kind", "filesystem", "packId", name, "path", "packs/" + name)));
        assertThat(http.response().getStatus())
                .as("the registration of %s is accepted: %s", name, http.body())
                .isEqualTo(202);
        registration.pollUntilItEnds();
        assertThat(field("/state"))
                .as("the registration of %s: %s", name, http.body())
                .isEqualTo("succeeded");
    }

    /**
     * Creates the activation by following the root's {@code activations} link with its method and the document; the
     * service must create it.
     */
    @When("the project activates its charter:")
    public void activate(String document) throws Exception {
        this.document = JSON.readTree(document);
        http.get("/");
        JsonNode root = body();
        JsonNode method = root.path(LINKS).path("activations").path("method");
        assertThat(method.isString())
                .as("the root's activations link names its method: %s", root)
                .isTrue();
        http.perform(
                method.asString(),
                CharterSteps.belowBase(CharterSteps.linkOf(root, "activations")),
                Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
                document);
        assertThat(http.response().getStatus())
                .as("the activation is created: %s", http.body())
                .isEqualTo(201);
        activation = body();
    }

    /**
     * The service created the activation (a selection the packs do not hold is refused with a 422, so the 201 is the
     * first proof), its skipped report lists nothing it refused to give, and every artifact the document sent selects is
     * active in the activation's effective list, whatever the reason it gives (selected, or required or locked by a
     * pack).
     */
    @Then("every selection of the charter is in force and nothing is refused")
    public void everySelectionInForce() throws Exception {
        http.get(CharterSteps.belowBase(CharterSteps.linkOf(activation(), "skipped")));
        assertThat(http.response().getStatus())
                .as("the skipped report: %s", http.body())
                .isEqualTo(200);
        JsonNode report = body().path("report");
        List<String> faults = new ArrayList<>();
        for (String bucket : REFUSED) {
            JsonNode listed = report.path(bucket);
            if (!listed.isArray()) {
                faults.add("the skipped report has no " + bucket + ": " + report);
            } else if (!listed.isEmpty()) {
                faults.add("the skipped report lists " + bucket + ": " + listed);
            }
        }
        assertThat(document).as("the project activated its charter").isNotNull();
        JsonNode selected = Objects.requireNonNull(document).path("selected");
        assertThat(selected.isObject() && !selected.isEmpty())
                .as("the charter selects something: %s", document)
                .isTrue();
        for (Map.Entry<String, JsonNode> kind : selected.properties()) {
            Map<String, JsonNode> listed = effectiveList(kind.getKey());
            for (JsonNode id : kind.getValue()) {
                JsonNode item = listed.get(id.asString());
                if (item == null) {
                    faults.add(kind.getKey() + ":" + id.asString() + " is not in the effective list");
                } else if (!"active".equals(item.path("state").asString())) {
                    faults.add(kind.getKey() + ":" + id.asString() + " is "
                            + item.path("state").asString() + "/"
                            + item.path("reason").asString());
                }
            }
        }
        assertThat(faults).as("the selections that are not in force").isEmpty();
    }

    // ---- reads under the activation -------------------------------------------------------------------------------

    /**
     * Reads one artifact under the activation as a client finds it: the activation's {@code artifacts} list of its
     * kind, then the item's {@code effective-artifact} link. The read must answer 200; it is the last answer.
     */
    @When("^the team reads the (agent profile|mission type|procedure) \"([^\"]*)\" under the activation$")
    public void read(String kind, String id) throws Exception {
        String token = Objects.requireNonNull(KINDS.get(kind));
        JsonNode item = effectiveList(token).get(id);
        assertThat(item).as("the activation lists %s:%s", token, id).isNotNull();
        readItem = item;
        http.get(CharterSteps.belowBase(CharterSteps.linkOf(Objects.requireNonNull(item), EFFECTIVE_ARTIFACT)));
        assertThat(http.response().getStatus())
                .as("the read of %s:%s under the activation: %s", token, id, http.body())
                .isEqualTo(200);
    }

    /** Reads one step of a mission type under the activation, by the mission type's step list. */
    @When("the team reads the step {string} of the mission type {string} under the activation")
    public void readStep(String step, String missionType) throws Exception {
        read("mission type", missionType);
        JsonNode summary = null;
        for (JsonNode candidate : body().at("/missionType/steps")) {
            if (step.equals(candidate.path("id").asString())) {
                summary = candidate;
            }
        }
        assertThat(summary)
                .as("the mission type %s lists the step %s: %s", missionType, step, http.body())
                .isNotNull();
        http.get(CharterSteps.belowBase(CharterSteps.linkOf(Objects.requireNonNull(summary), "self")));
        assertThat(http.response().getStatus())
                .as("the step read: %s", http.body())
                .isEqualTo(200);
    }

    /** The steps of the mission type just read, in the order it lists them, each in sequence or on demand. */
    @Then("its steps are, in order:")
    public void stepsInOrder(DataTable expected) throws Exception {
        List<List<String>> steps = new ArrayList<>();
        for (JsonNode step : body().at("/missionType/steps")) {
            steps.add(List.of(
                    step.path("id").asString(), step.path("onDemand").asBoolean() ? "on demand" : "in sequence"));
        }
        assertThat(steps).isEqualTo(expected.cells().subList(1, expected.height()));
    }

    @Then("the step names the role {string}")
    public void stepNamesRole(String role) throws Exception {
        assertThat(field("/role")).as("the step's role: %s", http.body()).isEqualTo(role);
    }

    /**
     * The artifact just read is in force under the activation: the read's own state where it carries one, else (a
     * mission type's read carries none) the state of its item in the activation's effective list.
     */
    @Then("it is in force")
    public void inForce() throws Exception {
        JsonNode answer = body();
        JsonNode stated = answer.has("state") ? answer : readItem;
        assertThat(stated)
                .as("the artifact was read from the activation's list")
                .isNotNull();
        assertThat(Objects.requireNonNull(stated).path("state").asString())
                .as("the artifact's state: %s", stated)
                .isEqualTo("active");
    }

    /**
     * The governance of the profile just read requires the artifact, which is in force; the entry links its read under
     * the activation, and that read says it is in force too.
     */
    @Then("its governance requires the {word} {string}, which is in force")
    public void governanceRequires(String kind, String id) throws Exception {
        JsonNode entry = null;
        for (JsonNode candidate : body().path("governance")) {
            if (kind.equals(candidate.path("kind").asString())
                    && id.equals(candidate.path("artifactId").asString())
                    && "requires".equals(candidate.path("relation").asString())) {
                entry = candidate;
            }
        }
        assertThat(entry)
                .as("the governance requires %s:%s: %s", kind, id, http.body())
                .isNotNull();
        JsonNode required = Objects.requireNonNull(entry);
        assertThat(required.path("state").asString())
                .as("the entry's state: %s", required)
                .isEqualTo("active");
        http.get(CharterSteps.belowBase(CharterSteps.linkOf(required, EFFECTIVE_ARTIFACT)));
        assertThat(http.response().getStatus())
                .as("the entry's read: %s", http.body())
                .isEqualTo(200);
        assertThat(field("/state"))
                .as("the required artifact's own state: %s", http.body())
                .isEqualTo("active");
    }

    // ---- the walk of every step ---------------------------------------------------------------------------------

    /**
     * For each step the mission type lists, in its order: the step read (its {@code self}), the action context (the
     * activation's templated {@code effective-context}) and the prompt (the context's {@code prompt} link).
     */
    @When("the team walks every step of the mission type {string}")
    public void walkEveryStep(String missionType) throws Exception {
        read("mission type", missionType);
        JsonNode steps = body().at("/missionType/steps");
        assertThat(steps.isArray() && !steps.isEmpty())
                .as("the mission type lists steps: %s", http.body())
                .isTrue();
        for (JsonNode summary : steps) {
            String step = summary.path("id").asString();
            http.get(CharterSteps.belowBase(CharterSteps.linkOf(summary, "self")));
            int stepStatus = http.response().getStatus();
            Map<String, String> values = new LinkedHashMap<>();
            values.put("missionType", missionType);
            values.put("action", step);
            http.get(CharterSteps.belowBase(
                    CharterSteps.expand(CharterSteps.linkOf(activation(), "effective-context"), values)));
            int contextStatus = http.response().getStatus();
            JsonNode context = body();
            int promptStatus = 0;
            JsonNode prompt = JSON.createObjectNode();
            if (context.path(LINKS).has("prompt")) {
                http.get(CharterSteps.belowBase(CharterSteps.linkOf(context, "prompt")));
                promptStatus = http.response().getStatus();
                prompt = body();
            }
            walked.add(new StepWalk(step, stepStatus, contextStatus, context, promptStatus, prompt));
        }
    }

    /**
     * Every step read answers, every action context answers, and each artifact a context lists in force links its read
     * under the activation, which answers 200.
     */
    @Then("every step has an action context that links each item in force to its body")
    public void everyStepHasContext() throws Exception {
        List<String> faults = new ArrayList<>();
        for (StepWalk walk : walked()) {
            if (walk.stepStatus() != 200) {
                faults.add(walk.step() + ": the step read answers " + walk.stepStatus());
            }
            if (walk.contextStatus() != 200) {
                faults.add(walk.step() + ": the action context answers " + walk.contextStatus() + " "
                        + walk.context().path("code").asString());
                continue;
            }
            JsonNode notInForce = walk.context().path("skipped").path("missionTypeNotInForce");
            if (!notInForce.isArray() || !notInForce.isEmpty()) {
                faults.add(walk.step() + ": the mission type is not in force: " + notInForce);
            }
            int inForce = 0;
            for (JsonNode item : walk.context().path("artifacts")) {
                String urn = item.path("kind").asString() + ":"
                        + item.path("artifactId").asString();
                if (!"active".equals(item.path("state").asString())) {
                    continue;
                }
                inForce++;
                if (!item.path(LINKS).path(EFFECTIVE_ARTIFACT).path("href").isString()) {
                    faults.add(walk.step() + ": " + urn + " has no " + EFFECTIVE_ARTIFACT + " link");
                    continue;
                }
                http.get(CharterSteps.belowBase(CharterSteps.linkOf(item, EFFECTIVE_ARTIFACT)));
                if (http.response().getStatus() != 200) {
                    faults.add(walk.step() + ": " + urn + " answers "
                            + http.response().getStatus());
                }
            }
            if (inForce == 0) {
                faults.add(walk.step() + ": the action context lists nothing in force");
            }
        }
        assertThat(faults).as("the steps without the context to act on").isEmpty();
    }

    /**
     * Every action context links its prompt, which answers 200 and lists every slot the prompt declares, in declaration
     * order, each with its fragments or none. Every fragment comes from a step enhancement the charter selects, and the
     * charter's enhancements fill at least one slot of the walk, so a service that lists every slot empty fails.
     */
    @Then("every step has a prompt that fills each slot it declares or lists the slot as empty")
    public void everyStepHasPrompt() {
        List<String> faults = new ArrayList<>();
        List<String> selectedEnhancements = new ArrayList<>();
        Objects.requireNonNull(document, "the project activated its charter")
                .path("selected")
                .path("step_enhancement")
                .forEach(id -> selectedEnhancements.add(id.asString()));
        int fragments = 0;
        for (StepWalk walk : walked()) {
            if (walk.promptStatus() != 200) {
                faults.add(walk.step() + ": the prompt answers " + walk.promptStatus() + " "
                        + walk.prompt().path("code").asString());
                continue;
            }
            List<String> declared = new ArrayList<>();
            Matcher start = SLOT_START.matcher(walk.prompt().path("prompt").asString());
            while (start.find()) {
                declared.add(start.group(1));
            }
            List<String> listed = new ArrayList<>();
            for (JsonNode slot : walk.prompt().path("slots")) {
                listed.add(slot.path("slot").asString());
                if (!slot.path("fragments").isArray()) {
                    faults.add(walk.step() + ": the slot " + slot.path("slot").asString() + " has no fragment list");
                }
                for (JsonNode fragment : slot.path("fragments")) {
                    fragments++;
                    String enhancement = fragment.path("enhancement").asString();
                    if (!selectedEnhancements.contains(enhancement)) {
                        faults.add(
                                walk.step() + ": the slot " + slot.path("slot").asString() + " holds a fragment of "
                                        + enhancement + ", which the charter does not select");
                    }
                }
            }
            if (!listed.equals(declared)) {
                faults.add(walk.step() + ": the prompt declares the slots " + declared + " and lists " + listed);
            }
        }
        if (fragments == 0) {
            faults.add("no slot of any step holds a fragment of the charter's enhancements " + selectedEnhancements);
        }
        assertThat(faults).as("the steps without a prompt to act on").isEmpty();
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    /**
     * The activation's effective list of one kind, by id: its templated {@code artifacts} link expanded with the kind,
     * then every {@code next} page.
     */
    private Map<String, JsonNode> effectiveList(String kind) throws Exception {
        Map<String, JsonNode> items = new LinkedHashMap<>();
        http.get(CharterSteps.belowBase(
                CharterSteps.expand(CharterSteps.linkOf(activation(), "artifacts"), Map.of("kind", kind))));
        for (int page = 1; ; page++) {
            assertThat(page).as("the %s list ends within 50 pages", kind).isLessThanOrEqualTo(50);
            assertThat(http.response().getStatus())
                    .as("the activation's %s list: %s", kind, http.body())
                    .isEqualTo(200);
            JsonNode answer = body();
            for (JsonNode item : answer.path("items")) {
                items.put(item.path("artifactId").asString(), item);
            }
            if (!answer.path(LINKS).has("next")) {
                return items;
            }
            http.get(CharterSteps.belowBase(CharterSteps.linkOf(answer, "next")));
        }
    }

    private JsonNode activation() {
        assertThat(activation).as("the project activated its charter").isNotNull();
        return Objects.requireNonNull(activation);
    }

    private List<StepWalk> walked() {
        assertThat(walked).as("the team walked the steps").isNotEmpty();
        return walked;
    }

    private JsonNode body() throws Exception {
        String text = http.body();
        return text.isEmpty() ? JSON.createObjectNode() : JSON.readTree(text);
    }

    private String field(String pointer) throws Exception {
        return body().at(pointer).asString("");
    }
}
