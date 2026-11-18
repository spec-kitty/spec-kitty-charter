package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.RoutingCatalogFacts;
import ai.speckitty.charter.packs.domain.RoutingCatalogFacts.ModelFacts;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0080 decision 5: the content rules of a routing catalog, each fault at the catalog's file and the member's place. */
@DisplayName("RoutingCatalogRules")
class RoutingCatalogRulesTest {

    private static final String FILE = "routing_catalogs/default.routing-catalog.yaml";

    private static RoutingCatalogFacts catalog(
            List<String> taskTypes, List<ModelFacts> models, List<String> constraints) {
        return new RoutingCatalogFacts(FILE, "default", taskTypes, models, constraints);
    }

    private static List<Reason> judge(RoutingCatalogFacts facts) {
        return RoutingCatalogRules.judge("main", facts);
    }

    @Test
    @DisplayName("a consistent catalog has no fault (the positive control of every rule below)")
    void aConsistentCatalogPasses() {
        RoutingCatalogFacts facts = catalog(
                List.of("design", "review"),
                List.of(
                        new ModelFacts("model-a", List.of("design", "review")),
                        new ModelFacts("model-b", List.of("design"))),
                List.of("review"));

        assertThat(judge(facts)).isEmpty();
    }

    @Test
    @DisplayName("a task type declared twice is refused at its second place")
    void aRepeatedTaskType() {
        List<Reason> faults =
                judge(catalog(List.of("design", "design"), List.of(new ModelFacts("m", List.of("design"))), List.of()));

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
            assertThat(fault.location().file()).isEqualTo(FILE);
            assertThat(fault.location().pointer()).isEqualTo("/task_types/1/id");
            assertThat(fault.detail()).contains("the task type design is declared twice");
        });
    }

    @Test
    @DisplayName("a model declared twice is refused at its second place")
    void aRepeatedModel() {
        List<Reason> faults = judge(catalog(
                List.of("design"),
                List.of(new ModelFacts("m", List.of("design")), new ModelFacts("m", List.of("design"))),
                List.of()));

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.location().pointer()).isEqualTo("/models/1/id");
            assertThat(fault.detail()).contains("the model m is declared twice");
        });
    }

    @Test
    @DisplayName("a fit for a task type the catalog does not declare is refused at the fit")
    void aFitForAnUndeclaredTaskType() {
        List<Reason> faults =
                judge(catalog(List.of("design"), List.of(new ModelFacts("m", List.of("design", "deploy"))), List.of()));

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.location().pointer()).isEqualTo("/models/0/task_fit/1/task_type");
            assertThat(fault.detail()).contains("a fit for the task type deploy, which the catalog does not declare");
        });
    }

    @Test
    @DisplayName("two fits for one task type in one model are refused at the second")
    void twoFitsForOneTaskType() {
        List<Reason> faults =
                judge(catalog(List.of("design"), List.of(new ModelFacts("m", List.of("design", "design"))), List.of()));

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.location().pointer()).isEqualTo("/models/0/task_fit/1/task_type");
            assertThat(fault.detail()).contains("two fits for the task type design");
        });
    }

    @Test
    @DisplayName("a tier constraint for a task type the catalog does not declare is refused at the constraint")
    void aConstraintForAnUndeclaredTaskType() {
        List<Reason> faults =
                judge(catalog(List.of("design"), List.of(new ModelFacts("m", List.of("design"))), List.of("deploy")));

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.location().pointer()).isEqualTo("/routing_policy/tier_constraints/0/task_type");
            assertThat(fault.detail()).contains("a tier constraint names the task type deploy");
        });
    }

    @Test
    @DisplayName("two tier constraints for one task type are refused at the second")
    void twoConstraintsForOneTaskType() {
        List<Reason> faults = judge(catalog(
                List.of("design"), List.of(new ModelFacts("m", List.of("design"))), List.of("design", "design")));

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.location().pointer()).isEqualTo("/routing_policy/tier_constraints/1/task_type");
            assertThat(fault.detail()).contains("the task type design has two tier constraints");
        });
    }

    @Test
    @DisplayName("every fault of a catalog is reported, in written order, not only the first")
    void everyFaultIsReported() {
        List<Reason> faults = judge(catalog(
                List.of("design", "design"), List.of(new ModelFacts("m", List.of("deploy"))), List.of("deploy")));

        assertThat(faults)
                .extracting(fault -> fault.location().pointer())
                .containsExactly(
                        "/task_types/1/id",
                        "/models/0/task_fit/0/task_type",
                        "/routing_policy/tier_constraints/0/task_type");
    }
}
