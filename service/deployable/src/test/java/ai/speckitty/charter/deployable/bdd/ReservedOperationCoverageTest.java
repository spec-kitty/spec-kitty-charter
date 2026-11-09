package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The 501 pins and the operations the contract marks {@code x-reserved} are one to one, contract set by contract set
 * (ADR-0020 D8): every reserved operation of a set is pinned by a scenario of the set's feature that calls it, and every
 * pin calls a reserved operation of that set. A reserved operation without a pin, or a pin whose operation was built (its
 * {@code x-reserved} removed) but whose scenario was not, fails here. A set the contract reserves that no pin file is named
 * for fails too. Only the request lines of the {@code @reserved} scenarios count: a scenario of a pin file that is not a pin
 * may call a built operation.
 *
 * <p>No operation of the contract is reserved any more: the activation set reserved the effective graph and the effective
 * action context until PR C built them (IC-11), and the pack-registration set reserved nothing before that. So no set has a
 * pin file and no scenario carries {@code @reserved}. The machinery stays for a reservation to come, and a bad-fixture
 * control keeps it honest: the same check, run over a planted contract that reserves two operations, reports an operation
 * with no pin, a pin whose operation is built and a body that is not pinned before it is bound.
 */
class ReservedOperationCoverageTest {

    private static final String OPENAPI = "contract/charter/openapi.yaml";
    /** The pin file of each contract set; no set reserves an operation now, so none has one. */
    private static final Map<String, String> FEATURES = Map.of();

    private static final Pattern REQUEST =
            Pattern.compile("^\\s*(?:When|And) I (?:send )?(GET|PUT|POST|DELETE|PATCH) \"([^\"?]+)(?:\\?[^\"]*)?\"");
    private static final Pattern RESERVED_TAG = Pattern.compile("\n\\s*@reserved\\s*\n");
    private static final Pattern NEXT_SCENARIO =
            Pattern.compile("^\\s*(?:@|Scenario|Rule:|Example:|Background:)", Pattern.MULTILINE);
    /** A pin that proves the 501 comes before the request body is bound is named so. */
    private static final Pattern BODY_PIN =
            Pattern.compile("^Scenario: .*before .*body is bound", Pattern.CASE_INSENSITIVE);

    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "patch");

    @TempDir
    Path fixture;

    /** A contract and the pin file text of each of its sets: the thing the checks read. */
    private record Contract(ContractConformance conformance, Map<?, ?> paths, Map<String, String> pinFiles) {}

    private static Contract shipped() {
        Map<String, String> pins = new TreeMap<>();
        FEATURES.forEach((set, feature) -> pins.put(set, text(feature)));
        return new Contract(
                new ContractConformance(), (Map<?, ?>) load(text(OPENAPI)).get("paths"), pins);
    }

    @Test
    void theShippedContractAgainstItsPinFilesHasNoProblem() {
        assertThat(problems(shipped())).isEmpty();
    }

    @Test
    void noScenarioPinsAnOperationAs501AndNoPinFileRemains() throws IOException, URISyntaxException {
        Path scenarios = Path.of(ReservedOperationCoverageTest.class
                .getClassLoader()
                .getResource("scenarios")
                .toURI());
        List<String> pins = new ArrayList<>();
        try (Stream<Path> files = Files.walk(scenarios)) {
            for (Path feature : files.filter(file -> file.toString().endsWith(".feature"))
                    .sorted()
                    .toList()) {
                String content = Files.readString(feature, StandardCharsets.UTF_8);
                if (RESERVED_TAG.matcher(content).find()
                        || content.contains("Then the operation is refused as not implemented")) {
                    pins.add(scenarios.relativize(feature).toString());
                }
            }
        }

        assertThat(pins)
                .as("a scenario of these files still pins a 501 for an operation the contract no longer reserves")
                .isEmpty();
    }

    @Test
    void aBadFixtureThatReservesTwoOperationsWithNoPinIsReportedByTheSameCheck() throws IOException {
        plant();

        List<String> problems = problems(planted(Map.of()));

        assertThat(problems)
                .as("the set is reserved and no pin file is named for it")
                .anySatisfy(
                        problem -> assertThat(problem).contains("activation").contains("no pin file"));
    }

    @Test
    void aReservedOperationWithoutAPinAndABodyThatIsNotPinnedBeforeItIsBoundAreReported() throws IOException {
        plant();
        String onlyTheReadPinned = """
                @reserved
                Scenario: Reading x is reserved
                  When I GET "/x"
                  Then the operation is refused as not implemented
                """;

        List<String> problems = problems(planted(Map.of("activation", onlyTheReadPinned)));

        assertThat(problems)
                .anySatisfy(problem -> assertThat(problem).contains("POST /y").contains("no pin"))
                .anySatisfy(problem -> assertThat(problem).contains("POST /y").contains("before its body is bound"))
                .noneSatisfy(problem -> assertThat(problem).contains("GET /x"));
    }

    @Test
    void aPinWhoseOperationWasBuiltIsReportedAndAFullyPinnedSetIsClean() throws IOException {
        plant();
        String everythingPinned = """
                @reserved
                Scenario: Reading x is reserved
                  When I GET "/x"
                  Then the operation is refused as not implemented

                @reserved
                Scenario: Writing y is reserved before its body is bound
                  When I POST "/y"
                  Then the operation is refused as not implemented
                """;
        String pinsABuiltOperation = everythingPinned + """

                @reserved
                Scenario: Reading z is reserved
                  When I GET "/z"
                  Then the operation is refused as not implemented
                """;

        assertThat(problems(planted(Map.of("activation", everythingPinned)))).isEmpty();
        assertThat(problems(planted(Map.of("activation", pinsABuiltOperation))))
                .singleElement()
                .satisfies(problem -> assertThat(problem).contains("GET /z").contains("not reserved"));
    }

    /** A contract that reserves {@code GET /x} and {@code POST /y} (which takes a body) and builds {@code GET /z}. */
    private void plant() throws IOException {
        write("charter/openapi.yaml", """
                openapi: 3.1.0
                paths:
                  /x:
                    $ref: paths/x.yaml
                  /y:
                    $ref: paths/y.yaml
                  /z:
                    $ref: paths/z.yaml
                """);
        write("charter/paths/x.yaml", """
                get:
                  x-reserved: {contractSet: activation, issue: '#1'}
                  responses:
                    '501': {description: d}
                """);
        write("charter/paths/y.yaml", """
                post:
                  x-reserved: {contractSet: activation, issue: '#2'}
                  requestBody:
                    content:
                      application/json:
                        schema: {type: object}
                  responses:
                    '501': {description: d}
                """);
        write("charter/paths/z.yaml", """
                get:
                  responses:
                    '200': {description: d}
                """);
    }

    private Contract planted(Map<String, String> pinFiles) throws IOException {
        Map<?, ?> openapi = load(Files.readString(fixture.resolve("charter/openapi.yaml"), StandardCharsets.UTF_8));
        return new Contract(new ContractConformance(fixture), (Map<?, ?>) openapi.get("paths"), pinFiles);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = fixture.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // ---- the checks: one set of rules over any contract and its pin files

    /** Every way the pins and the reservations of a contract part, one line each; empty when they are one to one. */
    private static List<String> problems(Contract contract) {
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, ContractConformance.DeclaredOperation>> reserved = allReserved(contract);
        reserved.keySet().stream()
                .filter(set -> !contract.pinFiles().containsKey(set))
                .forEach(set -> problems.add("the contract set " + set + " reserves operations and has no pin file"));
        contract.pinFiles().forEach((set, feature) -> {
            Set<String> reservedKeys = reserved.getOrDefault(set, Map.of()).keySet();
            Set<String> pinned = pinnedOperations(contract, feature, Pattern.compile(""));
            reservedKeys.stream()
                    .filter(key -> !pinned.contains(key))
                    .forEach(key -> problems.add(key + " is reserved and has no pin in " + set));
            pinned.stream()
                    .filter(key -> !reservedKeys.contains(key))
                    .forEach(key -> problems.add(key + " is pinned in " + set + " but is not reserved"));
            Set<String> bodyPins = pinnedOperations(contract, feature, BODY_PIN);
            reserved.getOrDefault(set, Map.of()).forEach((key, operation) -> {
                if (operation.node().containsKey("requestBody") && !bodyPins.contains(key)) {
                    problems.add(key + " takes a body and is not pinned before its body is bound in " + set);
                }
            });
        });
        return problems;
    }

    /** The reserved operations by contract set, each keyed {@code METHOD /template}. */
    private static Map<String, Map<String, ContractConformance.DeclaredOperation>> allReserved(Contract contract) {
        Map<String, Map<String, ContractConformance.DeclaredOperation>> reserved = new TreeMap<>();
        for (Object template : contract.paths().keySet()) {
            for (String method : METHODS) {
                String concrete = String.valueOf(template).replaceAll("\\{[^}]+}", "x");
                contract.conformance()
                        .operation(method, concrete)
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

    /** The operations the pins of the feature call; those pins whose first line matches {@code only}. */
    private static Set<String> pinnedOperations(Contract contract, String feature, Pattern only) {
        Set<String> pinned = new TreeSet<>();
        for (String scenario : reservedScenarios(feature)) {
            if (!only.matcher(scenario.lines().findFirst().orElse("")).find()) {
                continue;
            }
            assertThat(scenario)
                    .as("every reserved scenario ends in the 501 step")
                    .contains("Then the operation is refused as not implemented");
            scenario.lines()
                    .map(REQUEST::matcher)
                    .filter(Matcher::find)
                    .forEach(request -> pinned.add(pin(contract, request)));
        }
        return pinned;
    }

    /**
     * Each scenario a {@code @reserved} tag marks: the text after the tag, from its {@code Scenario:} line up to the
     * next tag or scenario, so an untagged scenario that follows a pin is not read as part of it.
     */
    private static List<String> reservedScenarios(String feature) {
        List<String> blocks = RESERVED_TAG.splitAsStream("\n" + feature).toList();
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

    private static String pin(Contract contract, Matcher request) {
        String method = request.group(1).toLowerCase(Locale.ROOT);
        String path = request.group(2);
        return contract.conformance()
                .operation(method, path)
                .map(operation -> key(method, operation.pathTemplate()))
                .orElseGet(() -> key(method, path) + " (declared by no operation of the contract; not reserved)");
    }

    private static String key(String method, Object template) {
        return method.toUpperCase(Locale.ROOT) + " " + template;
    }

    private static Map<?, ?> load(String yaml) {
        return (Map<?, ?>) new Load(LoadSettings.builder().build()).loadFromString(yaml);
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
