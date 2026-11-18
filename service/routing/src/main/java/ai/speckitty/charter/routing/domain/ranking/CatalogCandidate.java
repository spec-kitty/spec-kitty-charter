package ai.speckitty.charter.routing.domain.ranking;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One model the catalog recommends for a task type (ADR-0080 decisions 11 and 12): its score and fit, exact and
 * unrounded, its cost tier, and whether that tier is within the task type's constraint and within the effective tier.
 * A candidate outside a tier is answered and flagged, never hidden.
 *
 * @param modelId the model's id
 * @param score the weighted score (data-model rule 1)
 * @param fitScore the model's fit for the task type
 * @param rationale the fit's rationale, when the catalog states one
 * @param costTier the model's cost tier
 * @param withinTierConstraint {@code true} when the task type declares no constraint or the tier is at most it
 * @param withinModelTier present only when a tier is effective: whether the tier is at most it
 */
public record CatalogCandidate(
        String modelId,
        BigDecimal score,
        BigDecimal fitScore,
        Optional<String> rationale,
        CostTier costTier,
        boolean withinTierConstraint,
        Optional<Boolean> withinModelTier) {

    /**
     * Checks the members.
     *
     * @param modelId the model's id
     * @param score the score
     * @param fitScore the fit
     * @param rationale the rationale, possibly empty
     * @param costTier the cost tier
     * @param withinTierConstraint within the constraint
     * @param withinModelTier within the effective tier, possibly empty
     */
    public CatalogCandidate {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(fitScore, "fitScore");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(costTier, "costTier");
        Objects.requireNonNull(withinModelTier, "withinModelTier");
    }
}
