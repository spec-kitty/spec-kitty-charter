package ai.speckitty.charter.routing.infra.activations;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.routing.application.ModelRecommendationQuery;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The activations slice's spelling of the query, which its tag names: every part carried over. */
class ActivationsRecommendationInputsTest {

    @Test
    void theQueryCarriesEveryPart() {
        EffectiveTag.RecommendationQuery query = ActivationsRecommendationInputs.queryOf(new ModelRecommendationQuery(
                "review",
                "java-jenny",
                Optional.of(new ModelRecommendationQuery.Step("software-dev", "review")),
                Optional.of(CostTier.PREMIUM)));

        assertThat(query)
                .isEqualTo(new EffectiveTag.RecommendationQuery(
                        "review",
                        "java-jenny",
                        Optional.of(new EffectiveTag.StepRef("software-dev", "review")),
                        Optional.of(CostTier.PREMIUM)));
    }

    @Test
    void anAbsentStepAndTierStayAbsent() {
        assertThat(ActivationsRecommendationInputs.queryOf(
                        new ModelRecommendationQuery("design", "generic-agent", Optional.empty(), Optional.empty())))
                .isEqualTo(new EffectiveTag.RecommendationQuery(
                        "design", "generic-agent", Optional.empty(), Optional.empty()));
    }
}
