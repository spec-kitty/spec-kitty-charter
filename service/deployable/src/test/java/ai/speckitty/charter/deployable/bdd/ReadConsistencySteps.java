package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.PackLocation;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The steps of the read-consistency scenarios (D11): a pack file that changes while a read is answered, how many times
 * a header appears, and what the contract declares for each refusal code in {@code CharterRefusalCode}'s
 * {@code x-refusal} map. After each scenario, every refusal it observed is checked against that map.
 */
public class ReadConsistencySteps {

    private static final String REFUSAL_CODES = "contract/charter/schemas/CharterRefusalCode.yaml";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DRIFTED_NODE = "- urn: directive:DRIFTED\n  kind: directive\n  label: Drifted\n";

    private final ScenarioHttp http;
    private final ScenarioPacks packs;
    private final DriftingSources drift;

    public ReadConsistencySteps(ScenarioHttp http, ScenarioPacks packs, DriftingSources drift) {
        this.http = http;
        this.packs = packs;
        this.drift = drift;
    }

    @Before
    public void noDriftYet() {
        drift.disarm();
    }

    @Given("a pack file changes while the next read is answered")
    public void aPackFileChanges() {
        drift.arm(() -> rewrite(shard(), text -> text.replaceFirst("(?m)^nodes:.*\\R", "nodes:\n" + DRIFTED_NODE)));
    }

    @Given("a tactic file changes while the next read is answered")
    public void aTacticFileChanges() {
        Path tactic = tacticFile();
        drift.arm(() -> {
            String before = read(tactic);
            rewrite(tactic, text -> text.replaceFirst("(?m)^name:.*$", "name: Rehearse again"));
            assertThat(read(tactic))
                    .as("the drift changed %s", tactic.getFileName())
                    .isNotEqualTo(before);
        });
    }

    @Given("the directory of the pack {string} is missing")
    public void theDirectoryOfThePackIsMissing(String name) {
        PackLocation location = packs.locations().stream()
                .filter(l -> l.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no pack " + name + " is configured"));
        try (Stream<Path> walk = Files.walk(location.root())) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Given("a pack file is rewritten to invalid YAML while the next read is answered")
    public void aPackFileIsRewrittenToInvalidYaml() {
        drift.arm(() -> rewrite(shard(), text -> "nodes: [\n"));
    }

    @Given("no pack file changes while the next read is answered")
    public void noPackFileChanges() {
        drift.arm(() -> {});
    }

    @Then("the response header {string} appears once")
    public void headerAppearsOnce(String name) {
        assertThat(http.response().getHeaders(name)).hasSize(1);
    }

    @Then("the contract declares the refusal code {string}")
    public void theContractDeclares(String code) {
        assertThat(codes()).contains(code);
        assertThat(refusal(code)).as("x-refusal of %s", code).isNotEmpty();
    }

    @Then("the contract says {string} is answered with status {int}")
    public void theContractSaysStatus(String code, int status) {
        assertThat(refusal(code)).containsEntry("status", status);
    }

    @Then("the contract says {string} may not be retried")
    public void theContractSaysNotRetryable(String code) {
        assertThat(refusal(code)).containsEntry("retryable", false);
    }

    @Then("the contract says {string} is raised by no operation")
    public void theContractSaysNotRaised(String code) {
        assertThat(refusal(code)).containsEntry("raised", false);
    }

    @Then("the contract says {string} is raised by an operation")
    public void theContractSaysRaised(String code) {
        assertThat(refusal(code)).containsEntry("raised", true);
    }

    @Then("the contract says exactly these refusal codes are raised by an operation:")
    public void theContractSaysTheRaisedCodes(DataTable table) {
        assertThat(codes().stream()
                        .filter(code -> Boolean.TRUE.equals(refusal(code).get("raised")))
                        .toList())
                .containsExactlyInAnyOrderElementsOf(table.asList());
    }

    /** Each refusal this scenario saw has the status and the raised flag the contract declares for its code. */
    @After
    public void observedRefusalsAgreeWithTheContract() throws Exception {
        for (ScenarioHttp.Exchange exchange : http.exchanges()) {
            String text = exchange.result().getResponse().getContentAsString();
            String type = exchange.result().getResponse().getContentType();
            // A refusal is JSON; a text answer (the contract document's YAML) carries no code.
            boolean json = type != null && (type.startsWith("application/json") || type.contains("+json"));
            JsonNode body = text.isBlank() || !json ? JSON.createObjectNode() : JSON.readTree(text);
            if (!body.has("code")) {
                continue;
            }
            Map<String, Object> declared = refusal(body.path("code").asString());
            assertThat(declared)
                    .as("the contract declares %s", body.path("code"))
                    .containsEntry("status", exchange.result().getResponse().getStatus())
                    .containsEntry("raised", true);
        }
    }

    private Path shard() {
        List<PackLocation> locations = packs.locations();
        for (PackLocation location : locations) {
            try (Stream<Path> files = Files.walk(location.root())) {
                var found = files.filter(f -> f.getFileName().toString().endsWith(".graph.yaml"))
                        .sorted()
                        .findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        throw new IllegalStateException("the scenario has no graph shard to change");
    }

    /** The one {@code *.tactic.yaml} of the scenario's packs; never a shard, whatever it is named. */
    private Path tacticFile() {
        List<Path> found = new ArrayList<>();
        for (PackLocation location : packs.locations()) {
            try (Stream<Path> files = Files.walk(location.root())) {
                files.filter(f -> f.getFileName().toString().endsWith(".tactic.yaml"))
                        .sorted()
                        .forEach(found::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (found.size() != 1) {
            throw new IllegalStateException("the scenario needs exactly one tactic file to change, found " + found);
        }
        return found.getFirst();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void rewrite(Path file, UnaryOperator<String> change) {
        try {
            Files.writeString(file, change.apply(Files.readString(file)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<?, ?> schema() {
        try (var in = ReadConsistencySteps.class.getClassLoader().getResourceAsStream(REFUSAL_CODES)) {
            assertThat(in).as(REFUSAL_CODES).isNotNull();
            return (Map<?, ?>) new Load(LoadSettings.builder().build())
                    .loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> codes() {
        return ((List<?>) schema().get("enum")).stream().map(String::valueOf).toList();
    }

    /** The {@code x-refusal} entry of one code; empty when the contract has none. */
    private static Map<String, Object> refusal(String code) {
        Object all = schema().get("x-refusal");
        Map<String, Object> entry = new TreeMap<>();
        if (all instanceof Map<?, ?> map && map.get(code) instanceof Map<?, ?> declared) {
            declared.forEach((k, v) -> entry.put(String.valueOf(k), v));
        }
        return entry;
    }
}
