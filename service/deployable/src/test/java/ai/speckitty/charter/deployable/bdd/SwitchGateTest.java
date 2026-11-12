package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.deployable.bdd.ContractConformance.Operation;
import ai.speckitty.charter.deployable.bdd.SwitchGate.Entry;
import ai.speckitty.charter.deployable.bdd.SwitchGate.Exchange;
import ai.speckitty.charter.deployable.bdd.SwitchGate.Exercise;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The switch gate (ADR-0059 D1), judged on a planted contract and register: each failure it names has a control
 * that passes. The register shipped with the scenarios is checked against the real run by the suite itself.
 */
class SwitchGateTest {

    private static final ContractConformance CONTRACT = new ContractConformance();
    private static final String READ = "read.feature:3";
    private static final String GUARD = "every-operation.feature:9";

    private Map<String, Operation> operations;

    @BeforeEach
    void plantTheContract(@TempDir Path fixture) throws IOException {
        Files.createDirectories(fixture.resolve("charter/paths"));
        Files.writeString(fixture.resolve("charter/openapi.yaml"), """
                openapi: 3.1.0
                paths:
                  /things/{id}:
                    $ref: paths/things.yaml
                  /later:
                    $ref: paths/later.yaml
                """);
        Files.writeString(fixture.resolve("charter/paths/things.yaml"), """
                get:
                  operationId: getThing
                  responses:
                    '200': {description: d}
                    '401': {description: d}
                    '404': {description: d}
                    default: {description: d}
                """);
        Files.writeString(fixture.resolve("charter/paths/later.yaml"), """
                get:
                  operationId: getLater
                  x-reserved: {contractSet: later, issue: '#1'}
                  responses:
                    '200': {description: d}
                    '501': {description: d}
                """);
        operations = new ContractConformance(fixture).operations();
    }

    private static Map<String, Entry> register(String getThing) {
        return SwitchGate.register("""
                operations:
                  getThing: %s
                  getLater: {switch: held, reason: reserved until built}
                """.formatted(getThing));
    }

    private static Set<Exercise> everyAnswerOfGetThing() {
        return Set.of(
                new Exercise("get", "/things/{id}", 200, READ),
                new Exercise("get", "/things/{id}", 404, READ),
                new Exercise("get", "/things/{id}", 401, GUARD));
    }

    @Test
    void theContractDeclaresEachOperationWithItsNumberedStatuses() {
        assertThat(operations).containsOnlyKeys("getThing", "getLater");
        assertThat(operations.get("getThing").statuses()).containsExactlyInAnyOrder(200, 401, 404);
        assertThat(operations.get("getThing").reserved()).isFalse();
        assertThat(operations.get("getLater").reserved()).isTrue();
        assertThat(CONTRACT.operations().get("putPackRelationship").statuses()).contains(200, 201, 412, 428);
    }

    @Test
    void anOperationWithoutAnIdOrWithARangeKeyIsRefused(@TempDir Path fixture) throws IOException {
        Files.createDirectories(fixture.resolve("charter/paths"));
        Files.writeString(fixture.resolve("charter/openapi.yaml"), """
                openapi: 3.1.0
                paths:
                  /a:
                    $ref: paths/a.yaml
                """);
        Files.writeString(fixture.resolve("charter/paths/a.yaml"), """
                get:
                  responses:
                    '200': {description: d}
                """);
        assertThatThrownBy(() -> new ContractConformance(fixture).operations())
                .hasMessage("get /a declares no operationId");

        Files.writeString(fixture.resolve("charter/paths/a.yaml"), """
                get:
                  operationId: getA
                  responses:
                    '200': {description: d}
                    4XX: {description: d}
                """);
        assertThatThrownBy(() -> new ContractConformance(fixture).operations())
                .hasMessage("get /a declares the response key 4XX, which is neither a status nor default");
    }

    @Test
    void anExchangeCountsForTheOperationThatAnswersItsMethodAndPath() {
        assertThat(SwitchGate.exercised(
                        CONTRACT, new Exchange("put", "/packs/qa/artifacts/directive/GUARD", 412), true, READ))
                .contains(new Exercise("put", "/packs/{packId}/artifacts/directive/{artifactId}", 412, READ));
        assertThat(SwitchGate.exercised(
                        CONTRACT, new Exchange("get", "/packs/qa/artifacts/directive/GUARD", 200), false, READ))
                .contains(new Exercise("get", "/packs/{packId}/artifacts/{kind}/{artifactId}", 200, READ));
        assertThat(SwitchGate.exercised(CONTRACT, new Exchange("get", "/packs/qa/tactics", 200), true, READ))
                .isEmpty();
    }

    @Test
    void aRefusalCountsOnlyWhenAStepAssertedItsStatus() {
        Exchange refusal = new Exchange("get", "/packs/qa/artifacts/directive/GUARD", 404);

        assertThat(SwitchGate.exercised(CONTRACT, refusal, false, READ)).isEmpty();
        assertThat(SwitchGate.exercised(CONTRACT, refusal, true, READ)).isPresent();
    }

    @Test
    void aReadyOperationWithEveryAnswerExercisedPasses() {
        assertThat(SwitchGate.violations(operations, everyAnswerOfGetThing(), register("{switch: ready}")))
                .isEmpty();
    }

    @Test
    void aSuccessNoScenarioExercisedFailsWithTheR20WordingWhateverTheVerdict() {
        Set<Exercise> exercised = Set.of(new Exercise("get", "/things/{id}", 401, GUARD));

        assertThat(SwitchGate.violations(operations, exercised, register("{switch: held, reason: gaps}")))
                .containsExactly(
                        "No scenario exercises this success answer of the contract (R-20): GET /things/{id} 200");
    }

    @Test
    void aReadyOperationWithAnUnexercisedRefusalFailsNamingTheStatus() {
        Set<Exercise> exercised = new HashSet<>(everyAnswerOfGetThing());
        exercised.remove(new Exercise("get", "/things/{id}", 404, READ));

        assertThat(SwitchGate.violations(operations, exercised, register("{switch: ready}")))
                .containsExactly("getThing: is ready, but no scenario asserts its declared 404 on GET /things/{id};"
                        + " add a scenario, or hold it with a reason");
        assertThat(SwitchGate.violations(operations, exercised, register("{switch: held, reason: no 404 yet}")))
                .isEmpty();
    }

    @Test
    void aHeldOperationThatWouldPassMustBeMarkedReady() {
        assertThat(SwitchGate.violations(
                        operations, everyAnswerOfGetThing(), register("{switch: held, reason: stale}")))
                .containsExactly("getThing: is held (stale), but every declared answer is exercised; mark it ready");
    }

    @Test
    void aHeldReservedOperationIsNotStaleThoughNothingElseIsMissing() {
        Set<Exercise> exercised = new HashSet<>(everyAnswerOfGetThing());
        exercised.add(new Exercise("get", "/later", 200, READ));
        exercised.add(new Exercise("get", "/later", 501, READ));

        assertThat(SwitchGate.violations(operations, exercised, register("{switch: ready}")))
                .isEmpty();
    }

    @Test
    void aReservedOperationIsNeverReady() {
        Map<String, Entry> register = SwitchGate.register("""
                operations:
                  getThing: {switch: ready}
                  getLater: {switch: ready}
                """);

        assertThat(SwitchGate.violations(operations, everyAnswerOfGetThing(), register))
                .containsExactly("getLater: is ready, but it is reserved (x-reserved): a 501 pin does not count"
                        + " toward the gate (ADR-0059 D1)");
    }

    @Test
    void anOperationTheRegisterLacksOrAnEntryForNoOperationFails() {
        Map<String, Entry> register = SwitchGate.register("""
                operations:
                  getLater: {switch: held, reason: reserved until built}
                  getGone: {switch: ready}
                """);
        Set<Exercise> exercised = Set.of(new Exercise("get", "/things/{id}", 200, READ));

        assertThat(SwitchGate.violations(operations, exercised, register))
                .containsExactly(
                        "getThing: the contract declares it and the register has no entry for it; an operation the"
                                + " gate has no verdict for is not switched",
                        "getGone: the register names it and the contract declares no such operation");
    }

    @Test
    void aMalformedEntryIsRefused() {
        assertThatThrownBy(() -> register("{switch: held}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation getThing: an entry is");
        assertThatThrownBy(() -> register("{switch: ready, reason: why}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation getThing: an entry is");
        assertThatThrownBy(() -> register("{switch: maybe}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register("{switch: held, reason: '  '}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation getThing: an entry is");
        assertThatThrownBy(() -> register("{switch: held, reason: 7}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation getThing: an entry is");
        assertThatThrownBy(() ->
                        SwitchGate.register("operations:\n  getThing: {switch: ready}\n  getThing: {switch: ready}\n"))
                .as("a key stated twice")
                .isInstanceOf(RuntimeException.class);
        assertThat(register("{switch: held, reason: why}")).containsEntry("getThing", new Entry(false, "why"));
        assertThatThrownBy(() -> SwitchGate.register("operations: []\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operations must be a mapping");
        assertThatThrownBy(() -> SwitchGate.register("shared: {}\noperations: {}\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("the register holds exactly one key, operations");
    }

    @Test
    void theReportNamesTheScenariosOfEachStatus() {
        Set<Exercise> exercised = Set.of(new Exercise("get", "/things/{id}", 200, READ));

        String report = SwitchGate.report(operations, exercised, register("{switch: ready}"));

        assertThat(report)
                .contains("## getThing — ready")
                .contains("| 200 | read.feature:3 |")
                .contains("| 401 | **none** |")
                .contains("| 404 | **none** |")
                .contains("## getLater — held: reserved until built");
    }

    @Test
    void theShippedRegisterNamesExactlyTheContractsOperations() throws IOException {
        Map<String, Entry> shipped;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("scenarios/switch-gate.yaml")) {
            assertThat(in)
                    .as("scenarios/switch-gate.yaml on the test classpath")
                    .isNotNull();
            shipped = SwitchGate.register(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        assertThat(shipped.keySet()).isEqualTo(CONTRACT.operations().keySet());
    }
}
