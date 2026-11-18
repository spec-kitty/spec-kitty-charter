package ai.speckitty.charter.routing.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The effective tier: the request's, else the step's, else none (ADR-0080 decision 12). */
class ModelRecommendationTierTest {

    @Test
    void neitherGivesNone() {
        assertThat(ModelRecommendationTier.resolve(Optional.empty(), Optional.empty()))
                .isEmpty();
    }

    @Test
    void theStepAloneIsTheStepsTier() {
        assertThat(ModelRecommendationTier.resolve(Optional.empty(), Optional.of(CostTier.HIGH)))
                .contains(new ModelRecommendationTier(
                        CostTier.HIGH, ModelRecommendationTier.Source.STEP, Optional.of(CostTier.HIGH)));
    }

    @Test
    void theRequestAloneHasNoOffer() {
        assertThat(ModelRecommendationTier.resolve(Optional.of(CostTier.MEDIUM), Optional.empty()))
                .contains(new ModelRecommendationTier(
                        CostTier.MEDIUM, ModelRecommendationTier.Source.REQUEST, Optional.empty()));
    }

    @Test
    void theSourcesSpellTheContractsTokens() {
        assertThat(ModelRecommendationTier.Source.STEP.token()).isEqualTo("step");
        assertThat(ModelRecommendationTier.Source.REQUEST.token()).isEqualTo("request");
    }
}
