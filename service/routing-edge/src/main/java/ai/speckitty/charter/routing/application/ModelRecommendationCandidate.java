package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.routing.domain.ranking.CatalogCandidate;
import java.util.Objects;
import java.util.Optional;

/**
 * One model a recommendation answers (ADR-0080 decisions 8 and 13): a model the catalog in force ranks, or the
 * effective profile's model.
 */
public sealed interface ModelRecommendationCandidate {

    /**
     * The model's id.
     *
     * @return the id
     */
    String modelId();

    /**
     * Where the candidate comes from, as the contract writes it: {@code catalog} or {@code profile}.
     *
     * @return the token
     */
    String source();

    /**
     * A model the catalog in force ranks, with its score, fit, tier and flags.
     *
     * @param candidate the ranker's candidate
     */
    record FromCatalog(CatalogCandidate candidate) implements ModelRecommendationCandidate {

        /**
         * Checks the candidate.
         *
         * @param candidate the candidate
         */
        public FromCatalog {
            Objects.requireNonNull(candidate, "candidate");
        }

        @Override
        public String modelId() {
            return candidate.modelId();
        }

        @Override
        public String source() {
            return "catalog";
        }
    }

    /**
     * The effective profile's model and effort, with no score.
     *
     * @param modelId the profile's model
     * @param effort the profile's effort, when it declares one
     */
    record FromProfile(String modelId, Optional<String> effort) implements ModelRecommendationCandidate {

        /**
         * Checks the fields.
         *
         * @param modelId the model
         * @param effort the effort
         */
        public FromProfile {
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(effort, "effort");
        }

        @Override
        public String source() {
            return "profile";
        }
    }
}
