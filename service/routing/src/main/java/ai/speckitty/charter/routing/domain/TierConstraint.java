package ai.speckitty.charter.routing.domain;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.util.Objects;

/**
 * The highest cost tier a catalog advises for one task type (ADR-0080 decision 12). It advises: a model above it is
 * flagged, never hidden.
 *
 * @param taskType the id of the task type, which the catalog declares (a content rule)
 * @param maxTier the highest tier advised
 */
public record TierConstraint(String taskType, CostTier maxTier) {

    /**
     * Checks the task type id.
     *
     * @param taskType the task type id
     * @param maxTier the highest tier
     */
    public TierConstraint {
        Checks.id(Objects.requireNonNull(taskType, "taskType"));
        Objects.requireNonNull(maxTier, "maxTier");
    }
}
