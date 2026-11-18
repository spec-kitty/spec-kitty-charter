package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The answer of {@code getModelRecommendation} (ADR-0080 decision 8): what was asked, the catalog in force, its
 * objective and the task type's constraint, the effective tier, and the candidates in presentation order, the profile's
 * last.
 *
 * @param activationId the activation's id
 * @param query what was asked
 * @param catalog the catalog in force
 * @param objective the catalog's objective
 * @param tierConstraint the task type's {@code max_tier}, when declared
 * @param modelTier the effective tier, when one is asked or offered
 * @param candidates the candidates, in order
 */
public record ModelRecommendationRead(
        String activationId,
        ModelRecommendationQuery query,
        ModelRecommendationCatalog catalog,
        RoutingObjective objective,
        Optional<CostTier> tierConstraint,
        Optional<ModelRecommendationTier> modelTier,
        List<ModelRecommendationCandidate> candidates) {

    /**
     * Checks the fields and copies the list.
     *
     * @param activationId the activation
     * @param query the query
     * @param catalog the catalog
     * @param objective the objective
     * @param tierConstraint the constraint
     * @param modelTier the effective tier
     * @param candidates the candidates
     */
    public ModelRecommendationRead {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(tierConstraint, "tierConstraint");
        Objects.requireNonNull(modelTier, "modelTier");
        candidates = List.copyOf(candidates);
    }
}
