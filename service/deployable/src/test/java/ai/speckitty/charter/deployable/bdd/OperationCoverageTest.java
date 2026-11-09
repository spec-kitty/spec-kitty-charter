package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.bdd.ContractConformance.Success;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The operation-coverage check (R-20): every (method, path, 2xx) the contract declares is exercised by a scenario. */
class OperationCoverageTest {

    private static final ContractConformance CONTRACT = new ContractConformance();

    @Test
    void theContractDeclaresItsSuccessesByMethodTemplateAndStatus() {
        assertThat(CONTRACT.successResponses())
                .contains(
                        new Success("get", "/", 200),
                        new Success("get", "/openapi.yaml", 200),
                        new Success("put", "/packs/{packId}/relationships", 200),
                        new Success("put", "/packs/{packId}/relationships", 201),
                        new Success("post", "/packs/{packId}/artifacts/directive", 201),
                        new Success("get", "/packs/{packId}/artifacts/{kind}/{artifactId}", 200))
                .noneMatch(success -> success.status() < 200 || success.status() > 299);
    }

    @Test
    void theEffectiveGraphAndTheEffectiveActionContextAreBuiltSoTheirSuccessesAreDeclaredForCoverage() {
        // ADR-0020 D8: a reserved operation answers 501 whatever the request holds, so its 2xx cannot be exercised
        // until
        // it is built. PR C built the last two (IC-11): their marker is gone and their successes are declared like any
        // other operation's, so a scenario has to call each (effective-graph-and-context.feature).
        assertThat(CONTRACT.operation("get", "/activations/a-1/graph"))
                .hasValueSatisfying(
                        operation -> assertThat(operation.reserved()).isFalse());
        assertThat(CONTRACT.successResponses())
                .contains(
                        new Success("get", "/activations/{activationId}/graph", 200),
                        new Success(
                                "get",
                                "/activations/{activationId}/mission-types/{missionType}/actions/{action}/context",
                                200),
                        new Success("get", "/activations/{activationId}/skipped", 200),
                        new Success("get", "/activations/{activationId}/artifacts", 200),
                        new Success("put", "/activations/{activationId}/presets/{presetId}", 200),
                        new Success("post", "/activations", 201),
                        new Success("get", "/activations/{activationId}", 200),
                        new Success("put", "/activations/{activationId}", 201),
                        new Success("put", "/activations/{activationId}/selections/{kind}/{artifactId}", 200),
                        new Success("delete", "/activations/{activationId}/selections/{kind}/{artifactId}", 200),
                        new Success("put", "/activations/{activationId}/exclusions/{kind}/{artifactId}", 200),
                        new Success("delete", "/activations/{activationId}/exclusions/{kind}/{artifactId}", 200));
    }

    @Test
    void aReservedOperationsSuccessesAreNotDeclaredForCoverage(@TempDir Path fixture) throws IOException {
        // The control of the test above, on a planted contract: the rule still holds for a reservation to come.
        Files.createDirectories(fixture.resolve("charter/paths"));
        Files.writeString(fixture.resolve("charter/openapi.yaml"), """
                openapi: 3.1.0
                paths:
                  /reserved:
                    $ref: paths/reserved.yaml
                  /built:
                    $ref: paths/built.yaml
                """);
        Files.writeString(fixture.resolve("charter/paths/reserved.yaml"), """
                get:
                  x-reserved: {contractSet: activation, issue: '#1'}
                  responses:
                    '200': {description: d}
                    '501': {description: d}
                """);
        Files.writeString(fixture.resolve("charter/paths/built.yaml"), """
                get:
                  responses:
                    '200': {description: d}
                """);

        assertThat(new ContractConformance(fixture).successResponses())
                .containsExactly(new Success("get", "/built", 200));
    }

    @Test
    void anExchangeCountsForTheOperationThatAnswersItsMethod() {
        assertThat(OperationCoverage.exercised(CONTRACT, "put", "/packs/qa/artifacts/directive/GUARD", 201))
                .contains(new Success("put", "/packs/{packId}/artifacts/directive/{artifactId}", 201));
        assertThat(OperationCoverage.exercised(CONTRACT, "get", "/packs/qa/artifacts/directive/GUARD", 200))
                .contains(new Success("get", "/packs/{packId}/artifacts/{kind}/{artifactId}", 200));
        assertThat(OperationCoverage.exercised(CONTRACT, "get", "/packs/qa/artifacts/directive/GUARD", 404))
                .isEmpty();
        assertThat(OperationCoverage.exercised(CONTRACT, "get", "/packs/qa/tactics", 200))
                .isEmpty();
    }

    @Test
    void aSuccessNoScenarioExercisedIsAGapAndNamedAsSuch() {
        Set<Success> declared = CONTRACT.successResponses();
        Set<Success> exercised = new HashSet<>(declared);
        exercised.remove(new Success("put", "/packs/{packId}/relationships", 201));

        assertThat(OperationCoverage.gaps(declared, exercised))
                .containsExactly("PUT /packs/{packId}/relationships 201");
        assertThat(OperationCoverage.gaps(declared, declared)).isEmpty();
    }
}
