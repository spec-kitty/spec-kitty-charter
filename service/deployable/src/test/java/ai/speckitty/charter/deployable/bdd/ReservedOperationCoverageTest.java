package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The 501 pins and the operations the contract marks {@code x-reserved} are one to one, contract set by contract set
 * (ADR-0020 D8): every reserved operation of a set is pinned by a scenario of the set's feature that calls it, and every
 * pin calls a reserved operation of that set. A reserved operation without a pin, or a pin whose operation was built
 * (its {@code x-reserved} removed) but whose scenario was not, fails here. The set {@code activation} is pinned by
 * {@code activations.feature}. The set {@code pack-registration} reserves nothing any more: its pin file is gone.
 * A set the contract reserves that this class does not know fails too. Only the request lines of the {@code @reserved}
 * scenarios count: a scenario of the file that is not a pin may call a built operation.
 */
class ReservedOperationCoverageTest {

    private static final String OPENAPI = "contract/charter/openapi.yaml";
    /** The pin file of each contract set. */
    private static final Map<String, String> FEATURES = Map.of("activation", "scenarios/activations.feature");

    private static final Pattern REQUEST =
            Pattern.compile("^\\s*(?:When|And) I (?:send )?(GET|PUT|POST|DELETE|PATCH) \"([^\"?]+)(?:\\?[^\"]*)?\"");
    private static final Pattern RESERVED_TAG = Pattern.compile("\n\\s*@reserved\\s*\n");
    private static final Pattern NEXT_SCENARIO =
            Pattern.compile("^\\s*(?:@|Scenario|Rule:|Example:|Background:)", Pattern.MULTILINE);
    /** A pin that proves the 501 comes before the request body is bound is named so. */
    private static final Pattern BODY_PIN =
            Pattern.compile("^Scenario: .*before .*body is bound", Pattern.CASE_INSENSITIVE);

    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "patch");

    private final ContractConformance contract = new ContractConformance();

    @Test
    void everyContractSetThatReservesAnOperationHasAPinFile() {
        assertThat(allReserved().keySet()).isSubsetOf(FEATURES.keySet());
    }

    @Test
    void everyReservedOperationIsPinnedAndEveryPinCallsAReservedOperation() {
        FEATURES.forEach((set, feature) -> assertThat(pinnedOperations(feature))
                .as("the operations the 501 pins of %s call (contract set %s)", feature, set)
                .isNotEmpty()
                .isEqualTo(reservedOperations(set)));
    }

    @Test
    void theActivationSetReservesTwoOperations() {
        // #291 built the per-artifact writes and the preset write, and PR B (WP22) unreserved the effective artifact
        // list and the skipped report: only the effective graph and the effective action context stay reserved, until
        // PR C (IC-11) builds them.
        assertThat(reservedOperations("activation")).hasSize(2);
    }

    @Test
    void thePackRegistrationSetReservesNoOperation() {
        // Every operation of the set is built: the PUT, the stored-pack read and the status read (WP16), POST /packs
        // (WP17, WP18) and the two DELETEs of an artifact and of a relationship (WP19). Each removed its pin, and the
        // pin file went with the last.
        assertThat(reservedOperations("pack-registration")).isEmpty();
    }

    @Test
    void everyReservedOperationThatTakesABodyIsPinnedBeforeItsBodyIsBound() {
        // The interceptor answers a reserved operation 501 before its body is bound (ADR-0020 D8). A reserved operation
        // that declares a requestBody needs a pin that shows it: a scenario named "... before its body is bound" that
        // sends the operation a request whose body would be refused if it were bound.
        Map<String, ContractConformance.DeclaredOperation> withBody = new TreeMap<>();
        allReserved()
                .values()
                .forEach(operations -> operations.forEach((key, operation) -> {
                    if (operation.node().containsKey("requestBody")) {
                        withBody.put(key, operation);
                    }
                }));
        Set<String> bodyPins = new TreeSet<>();
        FEATURES.values().forEach(feature -> bodyPins.addAll(pinnedOperations(feature, BODY_PIN)));
        assertThat(withBody.keySet())
                .as("reserved operations that declare a requestBody, each pinned before its body is bound")
                .isSubsetOf(bodyPins);
    }

    private Set<String> reservedOperations(String set) {
        return allReserved().getOrDefault(set, Map.of()).keySet();
    }

    /** The reserved operations by contract set, each keyed {@code METHOD /template}. */
    private Map<String, Map<String, ContractConformance.DeclaredOperation>> allReserved() {
        Map<?, ?> paths = (Map<?, ?>) load(OPENAPI).get("paths");
        Map<String, Map<String, ContractConformance.DeclaredOperation>> reserved = new TreeMap<>();
        for (Object template : paths.keySet()) {
            for (String method : METHODS) {
                String concrete = String.valueOf(template).replaceAll("\\{[^}]+}", "x");
                contract.operation(method, concrete)
                        .filter(operation -> operation.pathTemplate().equals(template))
                        .filter(ContractConformance.DeclaredOperation::reserved)
                        .ifPresent(operation -> reserved.computeIfAbsent(contractSet(operation), set -> new TreeMap<>())
                                .put(key(method, operation.pathTemplate()), operation));
            }
        }
        return reserved;
    }

    private static String contractSet(ContractConformance.DeclaredOperation operation) {
        return operation.node().get("x-reserved") instanceof Map<?, ?> marker
                        && marker.get("contractSet") instanceof String set
                ? set
                : "";
    }

    private Set<String> pinnedOperations(String feature) {
        return pinnedOperations(feature, Pattern.compile(""));
    }

    /** The operations the pins of the feature call; those pins whose first line matches {@code only}. */
    private Set<String> pinnedOperations(String feature, Pattern only) {
        Set<String> pinned = new TreeSet<>();
        for (String scenario : reservedScenarios(feature)) {
            if (!only.matcher(scenario.lines().findFirst().orElse("")).find()) {
                continue;
            }
            assertThat(scenario)
                    .as("every reserved scenario of %s ends in the 501 step", feature)
                    .contains("Then the operation is refused as not implemented");
            scenario.lines().map(REQUEST::matcher).filter(Matcher::find).forEach(request -> pinned.add(pin(request)));
        }
        return pinned;
    }

    /**
     * Each scenario a {@code @reserved} tag marks: the text after the tag, from its {@code Scenario:} line up to the
     * next tag or scenario, so an untagged scenario that follows a pin is not read as part of it.
     */
    private static List<String> reservedScenarios(String feature) {
        List<String> blocks = RESERVED_TAG.splitAsStream(text(feature)).toList();
        return blocks.subList(1, blocks.size()).stream()
                .filter(block -> block.contains("Scenario:"))
                .map(ReservedOperationCoverageTest::firstScenario)
                .toList();
    }

    private static String firstScenario(String block) {
        String scenario = block.substring(block.indexOf("Scenario:"));
        Matcher next = NEXT_SCENARIO.matcher(scenario);
        return next.find(scenario.indexOf('\n') + 1) ? scenario.substring(0, next.start()) : scenario;
    }

    private String pin(Matcher request) {
        String method = request.group(1).toLowerCase(Locale.ROOT);
        String path = request.group(2);
        ContractConformance.DeclaredOperation operation = contract.operation(method, path)
                .orElseThrow(() -> new AssertionError(
                        "the pin calls " + method + " " + path + ", which the contract does not declare"));
        return key(method, operation.pathTemplate());
    }

    private static String key(String method, Object template) {
        return method.toUpperCase(Locale.ROOT) + " " + template;
    }

    private static Map<?, ?> load(String resource) {
        return (Map<?, ?>) new Load(LoadSettings.builder().build()).loadFromString(text(resource));
    }

    private static String text(String resource) {
        try (InputStream in =
                ReservedOperationCoverageTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError(resource + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
