package ai.speckitty.charter.routing.infra.activations;

import ai.speckitty.charter.activations.application.EffectiveHeldWinner;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.routing.application.ModelRecommendationCatalog;
import ai.speckitty.charter.routing.application.ModelRecommendationQuery;
import ai.speckitty.charter.routing.application.RecommendationInputs;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link RecommendationInputs} over the activations slice: it asks {@link EffectiveReadService#modelRecommendation}
 * for the catalog in force, the profile's model and effort and the step's offer, and narrows the catalog to the slice's
 * own {@link RoutingCatalog}. It decides nothing: the refusals, their order and the tag are the activations slice's,
 * unchanged.
 */
@Component
public final class ActivationsRecommendationInputs implements RecommendationInputs {

    private final EffectiveReadService effective;

    /**
     * An adapter over the activations slice's effective reads.
     *
     * @param effective the reads under an activation
     */
    @Autowired
    public ActivationsRecommendationInputs(EffectiveReadService effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    @Override
    public Inputs read(String activationId, ModelRecommendationQuery query) {
        EffectiveReadService.RecommendationRead read =
                effective.modelRecommendation(new ActivationId(activationId), queryOf(query));
        EffectiveHeldWinner winner = read.catalogWinner();
        return new Inputs(
                new ModelRecommendationCatalog(winner.ref().id(), winner.packId(), winner.level()),
                RoutingCatalog.class.cast(read.catalog()),
                read.profileModel(),
                read.profileEffort(),
                read.stepOffer(),
                read.sourceFingerprint(),
                read.etag());
    }

    /** The activations slice's spelling of the query, which its tag names. */
    static EffectiveTag.RecommendationQuery queryOf(ModelRecommendationQuery query) {
        return new EffectiveTag.RecommendationQuery(
                query.taskType(),
                query.agentProfile(),
                query.step().map(step -> new EffectiveTag.StepRef(step.missionType(), step.id())),
                query.modelTier());
    }
}
