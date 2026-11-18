package ai.speckitty.charter.routing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The weights of a catalog's weighted sum (ADR-0080 decision 10). Each is from 0 to 1, and the four need not sum to 1.
 *
 * @param quality the weight of the fit score
 * @param cost the weight of the cost tier's desirability
 * @param risk the weight of the fit's confidence
 * @param latency the weight of the latency tier's desirability
 */
public record RoutingWeights(BigDecimal quality, BigDecimal cost, BigDecimal risk, BigDecimal latency) {

    /**
     * Checks each weight's range.
     *
     * @param quality the quality weight
     * @param cost the cost weight
     * @param risk the risk weight
     * @param latency the latency weight
     */
    public RoutingWeights {
        Checks.unit(Objects.requireNonNull(quality, "quality"));
        Checks.unit(Objects.requireNonNull(cost, "cost"));
        Checks.unit(Objects.requireNonNull(risk, "risk"));
        Checks.unit(Objects.requireNonNull(latency, "latency"));
    }
}
