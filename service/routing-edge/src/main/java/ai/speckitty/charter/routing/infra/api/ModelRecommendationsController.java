package ai.speckitty.charter.routing.infra.api;

import ai.speckitty.charter.api.ModelRecommendationsApi;
import ai.speckitty.charter.api.model.ModelRecommendation;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.routing.application.ModelRecommendationOutcome;
import ai.speckitty.charter.routing.application.ModelRecommendationQuery;
import ai.speckitty.charter.routing.application.ModelRecommendationService;
import ai.speckitty.charter.web.InvalidParameter;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /activations/{activationId}/task-types/{taskType}/model-recommendation}: the models the routing catalog in
 * force recommends for a task type, beside an agent profile's model (ADR-0080 decisions 7 to 13), the routing slice's
 * inbound adapter. A parameter outside its pattern is refused by the generated interface's bean validation as a 400
 * without a code; a {@code modelTier} that is not a cost tier, and {@code missionType} without {@code step} or the
 * reverse, are refused here the same way, before the use case runs. A refusal of the read is answered by the shared
 * refusal handler. The answer carries the verified fingerprint and the tag of the representation, and a matching
 * {@code If-None-Match} is a {@code 304} that sends the tag alone.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ModelRecommendationsController implements ModelRecommendationsApi {

    private static final String MISSION_TYPE = "missionType";
    private static final String STEP = "step";

    private final ModelRecommendationService recommendations;
    private final ModelRecommendationDtos dtos;

    /**
     * A controller over the recommendation use case.
     *
     * @param recommendations the use case
     * @param dtos the wire mapper of its answer and its links
     */
    @Autowired
    public ModelRecommendationsController(ModelRecommendationService recommendations, ModelRecommendationDtos dtos) {
        this.recommendations = Objects.requireNonNull(recommendations, "recommendations");
        this.dtos = Objects.requireNonNull(dtos, "dtos");
    }

    @Override
    public ResponseEntity<ModelRecommendation> getModelRecommendation(
            String agentProfile,
            String activationId,
            String taskType,
            @Nullable String ifNoneMatch,
            @Nullable String missionType,
            @Nullable String step,
            @Nullable String modelTier) {
        ModelRecommendationQuery query =
                new ModelRecommendationQuery(taskType, agentProfile, step(missionType, step), tier(modelTier));
        return switch (recommendations.recommend(activationId, query, ifNoneMatch)) {
            case ModelRecommendationOutcome.NotModified notModified ->
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(EntityTags.quote(notModified.etag()))
                        .build();
            case ModelRecommendationOutcome.Found found ->
                ResponseEntity.ok()
                        .eTag(EntityTags.quote(found.etag()))
                        .header(HeaderNames.SOURCE_FINGERPRINT, found.sourceFingerprint())
                        .body(dtos.read(found.read()));
        };
    }

    /** The asked step: both of {@code missionType} and {@code step}, or neither (ADR-0080 decision 7). */
    static Optional<ModelRecommendationQuery.Step> step(@Nullable String missionType, @Nullable String step) {
        if (missionType != null && step != null) {
            return Optional.of(new ModelRecommendationQuery.Step(missionType, step));
        }
        if (missionType != null) {
            throw new InvalidParameter(
                    InvalidParameter.In.QUERY,
                    MISSION_TYPE,
                    missionType,
                    "which is sent without step, and the two go together");
        }
        if (step != null) {
            throw new InvalidParameter(
                    InvalidParameter.In.QUERY,
                    STEP,
                    step,
                    "which is sent without missionType, and the two go together");
        }
        return Optional.empty();
    }

    /** The asked tier, which must be a cost tier. */
    static Optional<CostTier> tier(@Nullable String modelTier) {
        if (modelTier == null) {
            return Optional.empty();
        }
        return Optional.of(CostTier.ofToken(modelTier)
                .orElseThrow(() -> new InvalidParameter(
                        InvalidParameter.In.QUERY, "modelTier", modelTier, "which is not a cost tier")));
    }
}
