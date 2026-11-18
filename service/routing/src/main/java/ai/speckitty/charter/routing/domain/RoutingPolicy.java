package ai.speckitty.charter.routing.domain;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How a catalog ranks its models (ADR-0080 decisions 10 to 12): the objective, the weights, and the highest tier it
 * advises per task type. No override policy and no freshness policy: the advice is advisory only, and never depends on
 * the clock (decisions 4 and 14).
 *
 * @param objective what the ranking favours
 * @param weights the weights of the weighted sum
 * @param tierConstraints the highest tier advised per task type, at most one per task type (a content rule)
 */
public record RoutingPolicy(RoutingObjective objective, RoutingWeights weights, List<TierConstraint> tierConstraints) {

    /**
     * Checks the members and copies the list.
     *
     * @param objective the objective
     * @param weights the weights
     * @param tierConstraints the tier constraints
     */
    public RoutingPolicy {
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(weights, "weights");
        tierConstraints = List.copyOf(tierConstraints);
    }

    /**
     * The highest tier the catalog advises for a task type.
     *
     * @param taskTypeId the task type's id
     * @return the tier, or empty when the catalog advises none for it
     */
    public Optional<CostTier> maxTierFor(String taskTypeId) {
        return tierConstraints.stream()
                .filter(constraint -> constraint.taskType().equals(taskTypeId))
                .map(TierConstraint::maxTier)
                .findFirst();
    }
}
