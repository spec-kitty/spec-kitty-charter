package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.routing.domain.ranking.Ranker;
import ai.speckitty.charter.routing.domain.ranking.Recommendation;
import ai.speckitty.charter.routing.domain.ranking.TaskTypeNotDeclared;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@code getModelRecommendation} (ADR-0080 decisions 7 to 13): the models the routing catalog in force recommends for a
 * task type, beside the model of an agent profile. The activation's half ({@link RecommendationInputs}) refuses the
 * activation, the profile, the step, the closure and the catalogs in force first; this service resolves the effective
 * tier, ranks with the domain's {@link Ranker}, refuses a task type the catalog does not declare
 * ({@code task_type_not_found}), appends the profile's candidate last and only then compares {@code If-None-Match}, so
 * no refusal is a {@code 304}. It decides nothing else and is deterministic: the same inputs answer the same read.
 */
@Service
public final class ModelRecommendationService {

    private final RecommendationInputs inputs;

    /**
     * A service over the inputs port.
     *
     * @param inputs where the activation's half of the read comes from
     */
    @Autowired
    public ModelRecommendationService(RecommendationInputs inputs) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    /**
     * Recommends models for a task type under an activation.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param query what was asked
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the recommendation, or that the client holds the current representation
     * @throws CharterRefusal as the port refuses, then with {@code TASK_TYPE_NOT_FOUND} when the catalog in force
     *     declares no such task type
     */
    public ModelRecommendationOutcome recommend(
            String activationId, ModelRecommendationQuery query, @Nullable String ifNoneMatch) {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(query, "query");
        RecommendationInputs.Inputs read = inputs.read(activationId, query);
        Optional<ModelRecommendationTier> tier = ModelRecommendationTier.resolve(query.modelTier(), read.stepOffer());
        Recommendation ranked = ranked(read, query.taskType(), tier.map(ModelRecommendationTier::effective));
        if (EntityTags.noneMatch(ifNoneMatch, read.etag())) {
            return new ModelRecommendationOutcome.NotModified(read.etag());
        }
        List<ModelRecommendationCandidate> candidates = new ArrayList<>();
        ranked.candidates().forEach(each -> candidates.add(new ModelRecommendationCandidate.FromCatalog(each)));
        read.profileModel()
                .ifPresent(model ->
                        candidates.add(new ModelRecommendationCandidate.FromProfile(model, read.profileEffort())));
        return new ModelRecommendationOutcome.Found(
                new ModelRecommendationRead(
                        activationId,
                        query,
                        read.catalog(),
                        ranked.objective(),
                        ranked.tierConstraint(),
                        tier,
                        candidates),
                read.sourceFingerprint(),
                read.etag());
    }

    /** The catalog's ranking of the task type, refused when the catalog does not declare it. */
    private static Recommendation ranked(RecommendationInputs.Inputs read, String taskType, Optional<CostTier> tier) {
        try {
            return Ranker.recommend(read.routingCatalog(), taskType, tier);
        } catch (TaskTypeNotDeclared notDeclared) {
            throw new CharterRefusal(
                    RefusalCode.TASK_TYPE_NOT_FOUND,
                    "The routing catalog " + notDeclared.catalogId() + " in force declares no task type "
                            + notDeclared.taskType() + ".");
        }
    }
}
