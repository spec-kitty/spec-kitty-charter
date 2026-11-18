package ai.speckitty.charter.routing.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.routing.domain.AccessMethod;
import ai.speckitty.charter.routing.domain.CatalogSource;
import ai.speckitty.charter.routing.domain.Grade;
import ai.speckitty.charter.routing.domain.ModelCost;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import ai.speckitty.charter.routing.domain.RoutingPolicy;
import ai.speckitty.charter.routing.domain.RoutingWeights;
import ai.speckitty.charter.routing.domain.TaskFit;
import ai.speckitty.charter.routing.domain.TaskType;
import ai.speckitty.charter.routing.domain.TierConstraint;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ranker against research R7's catalogs, built in code (ADR-0080 decisions 10 to 12; data-model rules 1 to 4, 6
 * and 7). Each row asserts the exact ordered candidate list with every flag, never containment.
 */
@DisplayName("The ranker answers the R7 rows exactly")
class RankerTest {

    private static final Optional<CostTier> NO_TIER = Optional.empty();

    // ---- fixtures ---------------------------------------------------------------------------------------------------

    private static TaskType taskType(String id) {
        return new TaskType(id, id, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static ModelEntry model(String id, CostTier tier, TaskFit... fits) {
        return model(id, tier, Optional.empty(), fits);
    }

    private static ModelEntry model(String id, CostTier tier, Optional<Grade> latency, TaskFit... fits) {
        return new ModelEntry(
                id,
                "example",
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fits),
                new ModelCost(tier, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                latency);
    }

    private static TaskFit fit(String taskType, String score) {
        return new TaskFit(taskType, new BigDecimal(score), Optional.empty(), Optional.empty());
    }

    private static RoutingWeights weights(String quality, String cost) {
        return new RoutingWeights(new BigDecimal(quality), new BigDecimal(cost), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static RoutingCatalog catalog(
            String id,
            RoutingObjective objective,
            RoutingWeights weights,
            List<TierConstraint> constraints,
            List<TaskType> taskTypes,
            List<ModelEntry> models) {
        return new RoutingCatalog(
                id,
                new PackIdentifier("org-base"),
                Optional.empty(),
                "2026-11-18T00:00:00Z",
                taskTypes,
                models,
                new RoutingPolicy(objective, weights, constraints),
                List.of(new CatalogSource(
                        "example",
                        "https://example.invalid/catalog",
                        AccessMethod.MANUAL,
                        "2026-11-18T00:00:00Z",
                        Optional.empty())));
    }

    /** Built-in's default (R7): quality_first; design: model-a fit 0.3 premium, model-b fit 0.1 low. */
    private static RoutingCatalog builtInDefault() {
        return catalog(
                "default",
                RoutingObjective.QUALITY_FIRST,
                weights("1", "0.2"),
                List.of(),
                List.of(taskType("design")),
                List.of(
                        model("model-a", CostTier.PREMIUM, fit("design", "0.3")),
                        model("model-b", CostTier.LOW, fit("design", "0.1"))));
    }

    /** Org-base's default (R7): balanced; model-b listed before model-a; review capped at medium; triage unfitted. */
    private static RoutingCatalog orgDefault() {
        return orgDefault(false);
    }

    private static RoutingCatalog orgDefault(boolean reversed) {
        List<ModelEntry> models = new ArrayList<>(List.of(
                model("model-b", CostTier.LOW, fit("design", "0.1")),
                model("model-a", CostTier.PREMIUM, fit("design", "0.3")),
                model("model-c", CostTier.PREMIUM, fit("review", "0.9")),
                model("model-d", CostTier.MEDIUM, fit("review", "0.6")),
                model("model-e", CostTier.LOW, fit("review", "0.5"))));
        if (reversed) {
            Collections.reverse(models);
        }
        return catalog(
                "default",
                RoutingObjective.BALANCED,
                weights("1", "0.2"),
                List.of(new TierConstraint("review", CostTier.MEDIUM)),
                List.of(taskType("design"), taskType("review"), taskType("triage")),
                models);
    }

    /** Org-base's cost-led (R7): cost_first; weights 0.5 and 0.5; design: model-a and model-b. */
    private static RoutingCatalog costLed() {
        return catalog(
                "cost-led",
                RoutingObjective.COST_FIRST,
                weights("0.5", "0.5"),
                List.of(),
                List.of(taskType("design")),
                List.of(
                        model("model-a", CostTier.PREMIUM, fit("design", "0.3")),
                        model("model-b", CostTier.LOW, fit("design", "0.1"))));
    }

    /** Each candidate as (model, score, fit, cost tier, within the constraint, within the effective tier). */
    private static List<Tuple> rows(Recommendation recommendation) {
        return recommendation.candidates().stream()
                .map(c -> tuple(
                        c.modelId(),
                        c.score().stripTrailingZeros().toPlainString(),
                        c.fitScore().stripTrailingZeros().toPlainString(),
                        c.costTier(),
                        c.withinTierConstraint(),
                        c.withinModelTier()))
                .toList();
    }

    // ---- the R7 ranking rows ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("row 1: quality_first ranks by fit first, so model-a is the unique top though the scores tie")
    void row1QualityFirstUniqueTop() {
        Recommendation answer = Ranker.recommend(builtInDefault(), "design", NO_TIER);

        assertThat(answer.objective()).isEqualTo(RoutingObjective.QUALITY_FIRST);
        assertThat(answer.tierConstraint()).isEmpty();
        assertThat(rows(answer))
                .containsExactly(tuple("model-a", "0.3", "0.3", CostTier.PREMIUM, true, Optional.empty()));
    }

    @Test
    @DisplayName(
            "row 2: balanced ties model-a and model-b at exactly 0.3, both answered by id, whatever the file order")
    void row2BalancedExactTie() {
        Recommendation answer = Ranker.recommend(orgDefault(), "design", NO_TIER);

        assertThat(rows(answer))
                .containsExactly(
                        tuple("model-a", "0.3", "0.3", CostTier.PREMIUM, true, Optional.empty()),
                        tuple("model-b", "0.3", "0.1", CostTier.LOW, true, Optional.empty()));
    }

    @Test
    @DisplayName("row 3: cost_first answers model-b alone at 0.55, over model-a at 0.15")
    void row3CostFirst() {
        Recommendation answer = Ranker.recommend(costLed(), "design", NO_TIER);

        assertThat(answer.objective()).isEqualTo(RoutingObjective.COST_FIRST);
        assertThat(rows(answer)).containsExactly(tuple("model-b", "0.55", "0.1", CostTier.LOW, true, Optional.empty()));
    }

    @Test
    @DisplayName("row 4: the medium cap flags model-c and answers model-d, the top inside it, beside it")
    void row4TierConstraintFlagsNeverFilters() {
        Recommendation answer = Ranker.recommend(orgDefault(), "review", NO_TIER);

        assertThat(answer.tierConstraint()).contains(CostTier.MEDIUM);
        assertThat(rows(answer))
                .containsExactly(
                        tuple("model-c", "0.9", "0.9", CostTier.PREMIUM, false, Optional.empty()),
                        tuple("model-d", "0.732", "0.6", CostTier.MEDIUM, true, Optional.empty()));
    }

    @Test
    @DisplayName("row 5: a low effective tier makes model-e the top inside; model-c stays, flagged twice")
    void row5StepTier() {
        Recommendation answer = Ranker.recommend(orgDefault(), "review", Optional.of(CostTier.LOW));

        assertThat(rows(answer))
                .containsExactly(
                        tuple("model-c", "0.9", "0.9", CostTier.PREMIUM, false, Optional.of(false)),
                        tuple("model-e", "0.7", "0.5", CostTier.LOW, true, Optional.of(true)));
    }

    @Test
    @DisplayName("row 6: a premium effective tier does not lift the medium cap: model-d is the top inside both")
    void row6TiersCombine() {
        Recommendation answer = Ranker.recommend(orgDefault(), "review", Optional.of(CostTier.PREMIUM));

        assertThat(rows(answer))
                .containsExactly(
                        tuple("model-c", "0.9", "0.9", CostTier.PREMIUM, false, Optional.of(true)),
                        tuple("model-d", "0.732", "0.6", CostTier.MEDIUM, true, Optional.of(true)));
    }

    @Test
    @DisplayName("row 7: a declared task type no model fits answers no candidate")
    void row7NoCandidate() {
        Recommendation answer = Ranker.recommend(orgDefault(), "triage", NO_TIER);

        assertThat(answer.taskType()).isEqualTo("triage");
        assertThat(answer.candidates()).isEmpty();
    }

    // ---- the rules, each alone
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("exact decimal: the org design tie holds in BigDecimal, where double would split it")
    void exactDecimalAgainstDouble() {
        double a = 1 * 0.3 + 0.2 * 0;
        double b = 1 * 0.1 + 0.2 * 1;
        assertThat(b).as("the control: in double the two scores differ").isNotEqualTo(a);
        assertThat(Double.toString(b)).isEqualTo("0.30000000000000004");

        assertThat(Ranker.recommend(orgDefault(), "design", NO_TIER).candidates())
                .extracting(CatalogCandidate::modelId)
                .containsExactly("model-a", "model-b");
    }

    @Test
    @DisplayName("all-zero weights: balanced answers every fitting model, quality_first lets the fit decide")
    void allZeroWeights() {
        List<ModelEntry> models = List.of(
                model("model-b", CostTier.LOW, fit("design", "0.1")),
                model("model-a", CostTier.PREMIUM, fit("design", "0.3")),
                model("model-c", CostTier.MEDIUM, fit("design", "0.2")));
        RoutingWeights zero = weights("0", "0");
        RoutingCatalog balanced =
                catalog("zero", RoutingObjective.BALANCED, zero, List.of(), List.of(taskType("design")), models);
        RoutingCatalog quality =
                catalog("zero", RoutingObjective.QUALITY_FIRST, zero, List.of(), List.of(taskType("design")), models);

        assertThat(Ranker.recommend(balanced, "design", NO_TIER).candidates())
                .extracting(CatalogCandidate::modelId, c -> c.score().signum())
                .containsExactly(tuple("model-a", 0), tuple("model-b", 0), tuple("model-c", 0));
        assertThat(Ranker.recommend(quality, "design", NO_TIER).candidates())
                .extracting(CatalogCandidate::modelId)
                .containsExactly("model-a");
    }

    @Test
    @DisplayName("file order: the catalog's models reversed answer the same, in the same order")
    void fileOrderDoesNotMatter() {
        for (String taskType : List.of("design", "review")) {
            for (Optional<CostTier> tier : List.of(NO_TIER, Optional.of(CostTier.LOW))) {
                assertThat(Ranker.recommend(orgDefault(true), taskType, tier))
                        .as("%s %s", taskType, tier)
                        .isEqualTo(Ranker.recommend(orgDefault(false), taskType, tier));
            }
        }
    }

    @Test
    @DisplayName("overlap: when the top inside is the top of all, the model is answered once")
    void overlapIsAnsweredOnce() {
        Recommendation answer = Ranker.recommend(orgDefault(), "review", Optional.of(CostTier.PREMIUM));
        RoutingCatalog uncapped = catalog(
                "default",
                RoutingObjective.BALANCED,
                weights("1", "0.2"),
                List.of(),
                List.of(taskType("review")),
                List.of(
                        model("model-d", CostTier.MEDIUM, fit("review", "0.6")),
                        model("model-e", CostTier.LOW, fit("review", "0.5"))));

        assertThat(answer.candidates()).extracting(CatalogCandidate::modelId).doesNotHaveDuplicates();
        assertThat(rows(Ranker.recommend(uncapped, "review", Optional.of(CostTier.HIGH))))
                .containsExactly(tuple("model-d", "0.732", "0.6", CostTier.MEDIUM, true, Optional.of(true)));
    }

    @Test
    @DisplayName("ties straddling a tier: both tied models are answered, and the top inside is ranked on its own")
    void tiesStraddlingATier() {
        RoutingCatalog catalog = catalog(
                "straddle",
                RoutingObjective.BALANCED,
                weights("1", "0"),
                List.of(new TierConstraint("review", CostTier.MEDIUM)),
                List.of(taskType("review")),
                List.of(
                        model("model-x", CostTier.PREMIUM, fit("review", "0.8")),
                        model("model-y", CostTier.MEDIUM, fit("review", "0.8")),
                        model("model-z", CostTier.LOW, fit("review", "0.5")),
                        model("model-w", CostTier.PREMIUM, fit("review", "0.7"))));

        assertThat(rows(Ranker.recommend(catalog, "review", NO_TIER)))
                .containsExactly(
                        tuple("model-x", "0.8", "0.8", CostTier.PREMIUM, false, Optional.empty()),
                        tuple("model-y", "0.8", "0.8", CostTier.MEDIUM, true, Optional.empty()));
        assertThat(rows(Ranker.recommend(catalog, "review", Optional.of(CostTier.LOW))))
                .as("the top inside low is model-z, below the tied top of all")
                .containsExactly(
                        tuple("model-x", "0.8", "0.8", CostTier.PREMIUM, false, Optional.of(false)),
                        tuple("model-y", "0.8", "0.8", CostTier.MEDIUM, true, Optional.of(false)),
                        tuple("model-z", "0.5", "0.5", CostTier.LOW, true, Optional.of(true)));
    }

    @Test
    @DisplayName("an empty inside contributes nothing: the top of all is answered alone, flagged")
    void anEmptyInsideContributesNothing() {
        RoutingCatalog catalog = catalog(
                "dear",
                RoutingObjective.BALANCED,
                weights("1", "0"),
                List.of(new TierConstraint("review", CostTier.LOW)),
                List.of(taskType("review")),
                List.of(
                        model("model-x", CostTier.PREMIUM, fit("review", "0.8")),
                        model("model-y", CostTier.HIGH, fit("review", "0.6"))));

        assertThat(rows(Ranker.recommend(catalog, "review", NO_TIER)))
                .containsExactly(tuple("model-x", "0.8", "0.8", CostTier.PREMIUM, false, Optional.empty()));
    }

    @Test
    @DisplayName("an unknown task type is the typed refusal, naming the task type and the catalog")
    void anUnknownTaskTypeIsRefused() {
        assertThatThrownBy(() -> Ranker.recommend(orgDefault(), "deploy", NO_TIER))
                .isInstanceOfSatisfying(TaskTypeNotDeclared.class, refusal -> {
                    assertThat(refusal.taskType()).isEqualTo("deploy");
                    assertThat(refusal.catalogId()).isEqualTo("default");
                })
                .hasMessage("the routing catalog default declares no task type deploy");
    }

    @Test
    @DisplayName("a fit's rationale is carried, and the ids tie-break by code point, not by locale")
    void rationaleAndCodePointOrder() {
        RoutingCatalog catalog = catalog(
                "order",
                RoutingObjective.BALANCED,
                weights("1", "0"),
                List.of(),
                List.of(taskType("design")),
                List.of(
                        model("model-b", CostTier.LOW, fit("design", "0.5")),
                        model("Model-Z", CostTier.LOW, fit("design", "0.5")),
                        new ModelEntry(
                                "model-a",
                                "example",
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(new TaskFit(
                                        "design", new BigDecimal("0.50"), Optional.empty(), Optional.of("Why."))),
                                new ModelCost(
                                        CostTier.LOW,
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty()),
                                Optional.empty())));

        List<CatalogCandidate> candidates =
                Ranker.recommend(catalog, "design", NO_TIER).candidates();

        assertThat(candidates)
                .as("0.50 ties 0.5 by compareTo; uppercase sorts before lowercase by code point")
                .extracting(CatalogCandidate::modelId)
                .containsExactly("Model-Z", "model-a", "model-b");
        assertThat(candidates.get(1).rationale()).contains("Why.");
        assertThat(candidates.get(0).rationale()).isEmpty();
    }
}
