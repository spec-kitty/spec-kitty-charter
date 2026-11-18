package ai.speckitty.charter.routing.domain.ranking;

import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.routing.domain.Grade;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import ai.speckitty.charter.routing.domain.RoutingWeights;
import ai.speckitty.charter.routing.domain.TaskFit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ranks a routing catalog's models for a task type (ADR-0080 decisions 10 to 12; data-model rules 1 to 4, 6 and 7), in
 * exact decimal: every constant is built from its text, nothing passes through {@code double}, and nothing is rounded.
 *
 * <p>The objective chooses the rank key (rule 2). {@link RoutingObjective#QUALITY_FIRST} ranks by the fit, then by the
 * score; {@link RoutingObjective#BALANCED} and {@link RoutingObjective#COST_FIRST} rank by the score alone. Those two
 * compute alike today: they differ in meaning, which the catalog's weights carry (decision 10).
 *
 * <p>The candidates are {@code top(inside) ∪ top(fitting)} (rule 4): every top-ranked fitting model with all its ties,
 * and every top-ranked model inside the tiers in play with all its ties, each once. A tier flags a model, never hides
 * it (decision 12). The order is rank descending, then model id by code point, and never the catalog's own order.
 */
public final class Ranker {

    private static final BigDecimal ONE = new BigDecimal("1");
    private static final BigDecimal TWO_THIRDS = new BigDecimal("0.66");
    private static final BigDecimal ONE_THIRD = new BigDecimal("0.33");
    private static final BigDecimal NONE = new BigDecimal("0");

    /** The desirability of an absent confidence or latency tier (rule 1). */
    private static final BigDecimal ABSENT = new BigDecimal("0.5");

    private Ranker() {}

    /**
     * The catalog's recommendation for a task type.
     *
     * @param catalog the catalog in force
     * @param taskType the task type's id
     * @param modelTier the effective tier, when the request or the step names one
     * @return the recommendation; its candidates are empty when no model fits the task type
     * @throws TaskTypeNotDeclared when the catalog declares no such task type
     */
    public static Recommendation recommend(RoutingCatalog catalog, String taskType, Optional<CostTier> modelTier) {
        if (catalog.taskType(taskType).isEmpty()) {
            throw new TaskTypeNotDeclared(taskType, catalog.id());
        }
        RoutingObjective objective = catalog.policy().objective();
        TiersInPlay tiers = new TiersInPlay(catalog.policy().maxTierFor(taskType), modelTier);
        List<Ranked> fitting = new ArrayList<>();
        for (ModelEntry model : catalog.models()) {
            model.fitFor(taskType)
                    .ifPresent(fit -> fitting.add(new Ranked(
                            model, fit, score(model, fit, catalog.policy().weights()))));
        }
        Comparator<Ranked> byRank = rankKey(objective);
        List<Ranked> inside = fitting.stream()
                .filter(ranked -> tiers.inside(ranked.model().cost().tier()))
                .toList();
        Map<String, Ranked> answered = new LinkedHashMap<>();
        top(inside, byRank)
                .forEach(ranked -> answered.putIfAbsent(ranked.model().id(), ranked));
        top(fitting, byRank)
                .forEach(ranked -> answered.putIfAbsent(ranked.model().id(), ranked));
        List<CatalogCandidate> candidates = answered.values().stream()
                .sorted(byRank.reversed().thenComparing(ranked -> ranked.model().id(), Ranker::byCodePoint))
                .map(ranked -> candidate(ranked, tiers))
                .toList();
        return new Recommendation(taskType, objective, tiers.tierConstraint(), candidates);
    }

    /**
     * Two ids in code-point order (rule 6), which {@link String#compareTo} is not for a character outside the basic
     * multilingual plane: it compares UTF-16 code units, so a supplementary character would sort before U+E000 to
     * U+FFFF.
     */
    static int byCodePoint(String left, String right) {
        return Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());
    }

    /**
     * A model's score for a fit (rule 1): {@code w.quality·fit + w.cost·cost + w.risk·risk + w.latency·latency}.
     *
     * @param model the model
     * @param fit its fit for the task type
     * @param weights the catalog's weights
     * @return the exact, unrounded score
     */
    static BigDecimal score(ModelEntry model, TaskFit fit, RoutingWeights weights) {
        return weights.quality()
                .multiply(fit.score())
                .add(weights.cost().multiply(cost(model.cost().tier())))
                .add(weights.risk().multiply(risk(fit.confidence())))
                .add(weights.latency().multiply(latency(model.latencyTier())));
    }

    /** The desirability of a cost tier (rule 1): a cheaper model is the more desirable. */
    static BigDecimal cost(CostTier tier) {
        return switch (tier) {
            case LOW -> ONE;
            case MEDIUM -> TWO_THIRDS;
            case HIGH -> ONE_THIRD;
            case PREMIUM -> NONE;
        };
    }

    /** The desirability of a fit's confidence (rule 1): a surer fit is the more desirable; absent is 0.5. */
    static BigDecimal risk(Optional<Grade> confidence) {
        return confidence
                .map(grade -> switch (grade) {
                    case HIGH -> ONE;
                    case MEDIUM -> TWO_THIRDS;
                    case LOW -> ONE_THIRD;
                })
                .orElse(ABSENT);
    }

    /** The desirability of a latency tier (rule 1): a faster model is the more desirable; absent is 0.5. */
    static BigDecimal latency(Optional<Grade> latency) {
        return latency.map(grade -> switch (grade) {
                    case LOW -> ONE;
                    case MEDIUM -> TWO_THIRDS;
                    case HIGH -> ONE_THIRD;
                })
                .orElse(ABSENT);
    }

    /** The rank key of an objective (rule 2), compared with {@code compareTo}, so {@code 0.30} ranks as {@code 0.3}. */
    static Comparator<Ranked> rankKey(RoutingObjective objective) {
        Comparator<Ranked> byScore = Comparator.comparing(Ranked::score, BigDecimal::compareTo);
        return objective.ranksByFitFirst()
                ? Comparator.comparing((Ranked ranked) -> ranked.fit().score(), BigDecimal::compareTo)
                        .thenComparing(byScore)
                : byScore;
    }

    /** Every member of the set whose rank equals the set's highest, all ties included; none for an empty set. */
    private static List<Ranked> top(List<Ranked> set, Comparator<Ranked> byRank) {
        Optional<Ranked> best = set.stream().max(byRank);
        return best.map(max -> set.stream()
                        .filter(ranked -> byRank.compare(ranked, max) == 0)
                        .toList())
                .orElse(List.of());
    }

    private static CatalogCandidate candidate(Ranked ranked, TiersInPlay tiers) {
        CostTier tier = ranked.model().cost().tier();
        return new CatalogCandidate(
                ranked.model().id(),
                ranked.score(),
                ranked.fit().score(),
                ranked.fit().rationale(),
                tier,
                tiers.withinTierConstraint(tier),
                tiers.withinModelTier(tier));
    }

    /** A fitting model with its fit and score. */
    record Ranked(ModelEntry model, TaskFit fit, BigDecimal score) {}
}
