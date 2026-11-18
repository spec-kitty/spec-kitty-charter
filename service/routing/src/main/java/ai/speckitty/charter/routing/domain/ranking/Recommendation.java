package ai.speckitty.charter.routing.domain.ranking;

import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The catalog's recommendation for one task type (ADR-0080 decisions 10 to 12): the objective it ranked by, the task
 * type's constraint, and the candidates in presentation order (data-model rule 6), rank descending then model id.
 *
 * @param taskType the task type's id
 * @param objective the catalog's objective
 * @param tierConstraint the task type's {@code max_tier}, when declared
 * @param candidates the candidates, in order; empty when no model fits the task type
 */
public record Recommendation(
        String taskType,
        RoutingObjective objective,
        Optional<CostTier> tierConstraint,
        List<CatalogCandidate> candidates) {

    /**
     * Checks the members and copies the list.
     *
     * @param taskType the task type
     * @param objective the objective
     * @param tierConstraint the constraint, possibly empty
     * @param candidates the candidates
     */
    public Recommendation {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(tierConstraint, "tierConstraint");
        candidates = List.copyOf(candidates);
    }
}
