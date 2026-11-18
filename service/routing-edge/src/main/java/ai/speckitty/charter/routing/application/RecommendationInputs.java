package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import java.util.Objects;
import java.util.Optional;

/**
 * Where {@code getModelRecommendation} reads what it ranks from: the activation's half of the read (ADR-0080 decisions
 * 7, 9 and 13). The adapter over the activations slice realizes it; the refusals and their order are that slice's.
 */
public interface RecommendationInputs {

    /**
     * The inputs of one recommendation under an activation.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param query what was asked
     * @return the catalog in force, the profile's model and effort, the step's offer and the tag
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code activation_not_found},
     *     {@code artifact_not_found} for the profile, {@code mission_type_not_found} or {@code action_not_found} for the
     *     step, the closure's own refusals, then {@code routing_catalog_not_in_force} or {@code routing_catalog_conflict}
     */
    Inputs read(String activationId, ModelRecommendationQuery query);

    /**
     * The activation's half of one recommendation.
     *
     * @param catalog the catalog in force, with the pack and level that hold it
     * @param routingCatalog the catalog as its winning pack declares it
     * @param profileModel the effective profile's model, when it declares one
     * @param profileEffort the effective profile's effort, when it declares one
     * @param stepOffer the asked step's model tier, when a step was asked and declares one
     * @param sourceFingerprint the source fingerprint of the read
     * @param etag the tag of the representation, unquoted
     */
    record Inputs(
            ModelRecommendationCatalog catalog,
            RoutingCatalog routingCatalog,
            Optional<String> profileModel,
            Optional<String> profileEffort,
            Optional<CostTier> stepOffer,
            String sourceFingerprint,
            String etag) {

        /**
         * Checks the fields.
         *
         * @param catalog the catalog's facts
         * @param routingCatalog the catalog
         * @param profileModel the profile's model
         * @param profileEffort the profile's effort
         * @param stepOffer the step's tier
         * @param sourceFingerprint the fingerprint
         * @param etag the tag
         */
        public Inputs {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(routingCatalog, "routingCatalog");
            Objects.requireNonNull(profileModel, "profileModel");
            Objects.requireNonNull(profileEffort, "profileEffort");
            Objects.requireNonNull(stepOffer, "stepOffer");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(etag, "etag");
        }
    }
}
