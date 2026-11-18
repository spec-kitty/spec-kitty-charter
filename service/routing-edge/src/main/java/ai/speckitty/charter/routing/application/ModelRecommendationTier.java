package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.util.Objects;
import java.util.Optional;

/**
 * The effective model tier of a recommendation (ADR-0080 decision 12): the request's tier, else the step's, with the
 * step's offer kept beside it.
 *
 * @param effective the effective tier
 * @param source where it comes from
 * @param offer the asked step's tier, when the step declares one
 */
public record ModelRecommendationTier(CostTier effective, Source source, Optional<CostTier> offer) {

    /**
     * Checks the fields.
     *
     * @param effective the effective tier
     * @param source its source
     * @param offer the step's offer
     */
    public ModelRecommendationTier {
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(offer, "offer");
    }

    /**
     * The tier in force, if any: the request's {@code modelTier} (source {@code request}), else the step's
     * {@code model_tier} (source {@code step}), else none.
     *
     * @param requested the request's tier
     * @param offer the step's tier
     * @return the effective tier, or empty when neither is given
     */
    public static Optional<ModelRecommendationTier> resolve(Optional<CostTier> requested, Optional<CostTier> offer) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(offer, "offer");
        if (requested.isPresent()) {
            return Optional.of(new ModelRecommendationTier(requested.get(), Source.REQUEST, offer));
        }
        return offer.map(tier -> new ModelRecommendationTier(tier, Source.STEP, offer));
    }

    /** Where the effective tier comes from. */
    public enum Source {
        /** The step's {@code model_tier}. */
        STEP("step"),
        /** The request's {@code modelTier}. */
        REQUEST("request");

        private final String token;

        Source(String token) {
            this.token = token;
        }

        /**
         * The source's token, as the contract writes it.
         *
         * @return the token
         */
        public String token() {
            return token;
        }
    }
}
