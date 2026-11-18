package ai.speckitty.charter.routing.domain.ranking;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.util.Objects;
import java.util.Optional;

/**
 * The tiers in play for one recommendation (data-model rule 3): the task type's {@code max_tier}, when the catalog
 * declares one, and the effective tier, when the request or the step names one. A cost tier is inside when it is at most
 * every tier in play, so the two combine and neither replaces the other: a {@code premium} effective tier never lifts a
 * {@code medium} cap. With no tier in play, every tier is inside.
 *
 * @param tierConstraint the task type's {@code max_tier}, when declared
 * @param modelTier the effective tier, when one is named
 */
public record TiersInPlay(Optional<CostTier> tierConstraint, Optional<CostTier> modelTier) {

    /**
     * Checks the members.
     *
     * @param tierConstraint the cap, possibly empty
     * @param modelTier the effective tier, possibly empty
     */
    public TiersInPlay {
        Objects.requireNonNull(tierConstraint, "tierConstraint");
        Objects.requireNonNull(modelTier, "modelTier");
    }

    /**
     * Whether a cost tier is within the task type's constraint.
     *
     * @param tier the model's cost tier
     * @return {@code true} when no constraint is declared, or the tier is at most it
     */
    public boolean withinTierConstraint(CostTier tier) {
        return tierConstraint.map(tier::atMost).orElse(true);
    }

    /**
     * Whether a cost tier is within the effective tier.
     *
     * @param tier the model's cost tier
     * @return empty when no tier is effective, else whether the tier is at most it
     */
    public Optional<Boolean> withinModelTier(CostTier tier) {
        return modelTier.map(tier::atMost);
    }

    /**
     * Whether a cost tier is inside every tier in play.
     *
     * @param tier the model's cost tier
     * @return {@code true} when it is within the constraint and within the effective tier
     */
    public boolean inside(CostTier tier) {
        return withinTierConstraint(tier) && withinModelTier(tier).orElse(true);
    }
}
