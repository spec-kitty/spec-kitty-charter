package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.util.Objects;
import java.util.Optional;

/**
 * What {@code getModelRecommendation} is asked (ADR-0080 decision 7): the task type, the profile whose model is set
 * beside the catalog's, the step whose model tier is offered and the tier that overrides it.
 *
 * @param taskType the task type of the path
 * @param agentProfile the profile's id, exactly as the request names it
 * @param step the step, when the request names one
 * @param modelTier the asked tier, when the request sends one
 */
public record ModelRecommendationQuery(
        String taskType, String agentProfile, Optional<Step> step, Optional<CostTier> modelTier) {

    /**
     * Checks the fields.
     *
     * @param taskType the task type
     * @param agentProfile the profile's id
     * @param step the step
     * @param modelTier the asked tier
     */
    public ModelRecommendationQuery {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(agentProfile, "agentProfile");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(modelTier, "modelTier");
    }

    /**
     * A step, by its mission type and its id.
     *
     * @param missionType the mission type's id
     * @param id the step's id
     */
    public record Step(String missionType, String id) {

        /**
         * Checks the fields.
         *
         * @param missionType the mission type
         * @param id the step
         */
        public Step {
            Objects.requireNonNull(missionType, "missionType");
            Objects.requireNonNull(id, "id");
        }
    }
}
