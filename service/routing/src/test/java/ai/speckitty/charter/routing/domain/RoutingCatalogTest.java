package ai.speckitty.charter.routing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The routing catalog's domain (ADR-0080 decision 5, #108): what it holds and the values it refuses. */
@DisplayName("A routing catalog holds its task types, models, policy and sources")
class RoutingCatalogTest {

    private static final TaskType DESIGN =
            new TaskType("design", "Design", Optional.empty(), Optional.of(Grade.HIGH), Optional.empty());

    private static TaskFit fit(String taskType, String score) {
        return new TaskFit(taskType, new BigDecimal(score), Optional.empty(), Optional.empty());
    }

    private static ModelEntry model(String id, TaskFit... fits) {
        return new ModelEntry(
                id,
                "example",
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fits),
                new ModelCost(CostTier.LOW, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty());
    }

    private static RoutingWeights weights(String quality) {
        return new RoutingWeights(new BigDecimal(quality), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static RoutingCatalog catalog(List<TaskType> taskTypes, List<ModelEntry> models) {
        return catalog("2026-11-18T00:00:00Z", taskTypes, models);
    }

    private static RoutingCatalog catalog(String generatedAt, List<TaskType> taskTypes, List<ModelEntry> models) {
        return new RoutingCatalog(
                "default",
                new PackIdentifier("org-base"),
                Optional.empty(),
                generatedAt,
                taskTypes,
                models,
                new RoutingPolicy(
                        RoutingObjective.QUALITY_FIRST,
                        weights("1"),
                        List.of(new TierConstraint("design", CostTier.MEDIUM))),
                List.of(new CatalogSource(
                        "example", "https://example.invalid", AccessMethod.MANUAL, "2026-11-18", Optional.empty())));
    }

    @Test
    @DisplayName("a catalog is a routing_catalog artifact named by its id, with no schema version")
    void aCatalogIsAnArtifact() {
        RoutingCatalog catalog = catalog(List.of(DESIGN), List.of(model("model-a", fit("design", "0.66"))));

        assertThat(catalog.kind()).isEqualTo(ArtifactKind.ROUTING_CATALOG);
        assertThat(catalog.localId()).isEqualTo("default");
        assertThat(catalog.label()).isEqualTo("default");
        assertThat(catalog.schemaVersion()).isEmpty();
        assertThat(catalog.taskType("design")).contains(DESIGN);
        assertThat(catalog.taskType("review")).isEmpty();
        assertThat(catalog.policy().maxTierFor("design")).contains(CostTier.MEDIUM);
        assertThat(catalog.policy().maxTierFor("review")).isEmpty();
        assertThat(catalog.models().getFirst().fitFor("design").map(TaskFit::score))
                .contains(new BigDecimal("0.66"));
        assertThat(catalog.models().getFirst().fitFor("review")).isEmpty();
    }

    @Test
    @DisplayName("a catalog without a task type, a model or a source is refused")
    void anEmptyListIsRefused() {
        ModelEntry model = model("model-a", fit("design", "1"));

        assertThatThrownBy(() -> catalog(List.of(), List.of(model))).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> catalog(List.of(DESIGN), List.of())).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> model("model-a")).isInstanceOf(ConstraintBreachException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"G", "2026-11-18", "2026-11-18T00:00:00", "2026-13-01T00:00:00Z", "2026-11-18T25:00:00Z"})
    @DisplayName("a generation time that is not an ISO 8601 date-time with its offset is refused")
    void aGeneratedAtThatIsNoDateTimeIsRefused(String generatedAt) {
        List<ModelEntry> models = List.of(model("model-a", fit("design", "1")));

        assertThatThrownBy(() -> catalog(generatedAt, List.of(DESIGN), models))
                .isInstanceOf(ConstraintBreachException.class);
        assertThat(catalog("2026-11-18T09:30:00.5+01:00", List.of(DESIGN), models)
                        .generatedAt())
                .isEqualTo("2026-11-18T09:30:00.5+01:00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.01"})
    @DisplayName("a fit score or a weight outside 0 to 1 is refused")
    void aValueOutsideTheUnitIsRefused(String value) {
        assertThatThrownBy(() -> fit("design", value)).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> weights(value)).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("the bounds of the unit are admitted, and neither price is negative")
    void theBoundsAreAdmitted() {
        Optional<BigDecimal> negative = Optional.of(new BigDecimal("-1"));
        Optional<BigDecimal> free = Optional.of(BigDecimal.ZERO);

        assertThat(fit("design", "0").score()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(weights("1").quality()).isEqualByComparingTo(BigDecimal.ONE);
        assertThatThrownBy(() ->
                        new ModelCost(CostTier.HIGH, negative, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() ->
                        new ModelCost(CostTier.HIGH, Optional.empty(), negative, Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThat(new ModelCost(CostTier.LOW, free, free, Optional.empty(), Optional.empty()).outputPer1mUsd())
                .isEqualTo(free);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Design", "1design", "de--sign", ""})
    @DisplayName("a task type id off the letter-first slug grammar is refused")
    void aBadTaskTypeIdIsRefused(String id) {
        assertThatThrownBy(() -> new TaskType(id, "Title", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank text is refused, whether the field is required or optional")
    void aBlankTextIsRefused() {
        Optional<String> blank = Optional.of(" ");
        ModelCost low =
                new ModelCost(CostTier.LOW, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> new TaskType("design", " ", Optional.empty(), Optional.empty(), Optional.empty()))
                .as("a task type's title")
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new TaskType("design", "Design", blank, Optional.empty(), Optional.empty()))
                .as("a task type's description")
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new TaskFit("design", BigDecimal.ONE, Optional.empty(), blank))
                .as("a fit's rationale")
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(
                        () -> new ModelCost(CostTier.LOW, Optional.empty(), Optional.empty(), blank, Optional.empty()))
                .as("a cost's currency")
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(
                        () -> new ModelCost(CostTier.LOW, Optional.empty(), Optional.empty(), Optional.empty(), blank))
                .as("a cost's pricing source")
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new ModelEntry(
                        "model-a",
                        "example",
                        blank,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(fit("design", "1")),
                        low,
                        Optional.empty()))
                .as("a model's family")
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new CatalogSource(
                        "example", "https://example.invalid", AccessMethod.MANUAL, "2026-11-18", blank))
                .as("a source's licence notes")
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new RoutingCatalog(
                        "default",
                        new PackIdentifier("org-base"),
                        blank,
                        "2026-11-18T00:00:00Z",
                        List.of(DESIGN),
                        List.of(model("model-a", fit("design", "1"))),
                        new RoutingPolicy(RoutingObjective.QUALITY_FIRST, weights("1"), List.of()),
                        List.of(new CatalogSource(
                                "example",
                                "https://example.invalid",
                                AccessMethod.MANUAL,
                                "2026-11-18",
                                Optional.empty()))))
                .as("a catalog's description")
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a grade and an access method round-trip their tokens, and an unknown token is empty")
    void tokensRoundTrip() {
        for (Grade grade : Grade.values()) {
            assertThat(Grade.ofToken(grade.token())).contains(grade);
        }
        for (AccessMethod method : AccessMethod.values()) {
            assertThat(AccessMethod.ofToken(method.token())).contains(method);
        }
        assertThat(Grade.ofToken("extreme")).isEmpty();
        assertThat(AccessMethod.ofToken("scrape")).isEmpty();
    }
}
