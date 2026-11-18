package ai.speckitty.charter.routing.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.routing.domain.Grade;
import ai.speckitty.charter.routing.domain.ModelCost;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingWeights;
import ai.speckitty.charter.routing.domain.TaskFit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The score of data-model rule 1: each desirability constant, alone, in exact decimal. */
@DisplayName("A model's score is the weighted sum of its desirabilities")
class ScoreTest {

    private static Optional<Grade> grade(String token) {
        return token.isEmpty() ? Optional.empty() : Grade.ofToken(token);
    }

    private static BigDecimal score(
            String fit, CostTier tier, String confidence, String latency, String q, String c, String r, String l) {
        ModelEntry model = new ModelEntry(
                "m",
                "example",
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new TaskFit("t", new BigDecimal(fit), grade(confidence), Optional.empty())),
                new ModelCost(tier, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                grade(latency));
        RoutingWeights weights =
                new RoutingWeights(new BigDecimal(q), new BigDecimal(c), new BigDecimal(r), new BigDecimal(l));
        return Ranker.score(model, model.taskFit().getFirst(), weights);
    }

    @ParameterizedTest(name = "cost {0} is {1}")
    @CsvSource({"LOW, 1", "MEDIUM, 0.66", "HIGH, 0.33", "PREMIUM, 0"})
    @DisplayName("the cost desirability of each tier, under the cost weight alone")
    void costDesirability(CostTier tier, String expected) {
        assertThat(score("0.9", tier, "", "", "0", "1", "0", "0")).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "confidence ''{0}'' is {1}")
    @CsvSource({"high, 1", "medium, 0.66", "low, 0.33", "'', 0.5"})
    @DisplayName("the risk desirability of each confidence, absent 0.5, under the risk weight alone")
    void riskDesirability(String confidence, String expected) {
        assertThat(score("0.9", CostTier.PREMIUM, confidence, "", "0", "0", "1", "0"))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "latency ''{0}'' is {1}")
    @CsvSource({"low, 1", "medium, 0.66", "high, 0.33", "'', 0.5"})
    @DisplayName("the latency desirability of each tier, absent 0.5, under the latency weight alone")
    void latencyDesirability(String latency, String expected) {
        assertThat(score("0.9", CostTier.PREMIUM, "", latency, "0", "0", "0", "1"))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "every part weighed, 0.6, MEDIUM, high, low, 1, 0.2, 0.1, 0.05, 0.882",
        "the fit alone, 0.73, LOW, '', '', 1, 0, 0, 0, 0.73",
        "half weights, 0.3, PREMIUM, '', '', 0.5, 0.5, 0, 0, 0.15"
    })
    @DisplayName("the sum of the four weighted parts, unrounded")
    void theWeightedSum(
            String name,
            String fit,
            CostTier tier,
            String confidence,
            String latency,
            String q,
            String c,
            String r,
            String l,
            String expected) {
        // 0.6 + 0.2 x 0.66 + 0.1 x 1 + 0.05 x 1 = 0.6 + 0.132 + 0.1 + 0.05 = 0.882
        assertThat(score(fit, tier, confidence, latency, q, c, r, l)).isEqualByComparingTo(expected);
    }
}
