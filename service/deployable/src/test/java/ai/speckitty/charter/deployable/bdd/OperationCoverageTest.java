package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.bdd.ContractConformance.Success;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
    void aReservedOperationsSuccessesAreNotDeclaredForCoverage() {
        // ADR-0020 D8: a reserved operation answers 501 whatever the request holds, so no scenario can exercise its
        // 2xx until it is built; its 501 pin covers it until then.
        assertThat(CONTRACT.operation("get", "/activations/a-1"))
                .hasValueSatisfying(
                        operation -> assertThat(operation.reserved()).isTrue());
        assertThat(CONTRACT.successResponses())
                .doesNotContain(
                        new Success("post", "/activations", 201),
                        new Success("get", "/activations/{activationId}", 200),
                        new Success("put", "/activations/{activationId}", 201),
                        new Success("get", "/activations/{activationId}/skipped", 200))
                .noneMatch(success -> success.template().startsWith("/activations"));
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
