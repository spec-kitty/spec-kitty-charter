package ai.speckitty.charter.routing.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.ModelRecommendation;
import ai.speckitty.charter.api.model.ModelRecommendationCandidate;
import ai.speckitty.charter.api.model.ModelRecommendationTier;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.routing.application.ModelRecommendationOutcome;
import ai.speckitty.charter.routing.application.ModelRecommendationQuery;
import ai.speckitty.charter.routing.application.ModelRecommendationService;
import ai.speckitty.charter.routing.application.RoutingFixtures;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The wire form of a recommendation: the members of each source, the number form and the links. */
class ModelRecommendationDtosTest {

    private final ModelRecommendationDtos dtos = new ModelRecommendationDtos("/api/v1/");

    private ModelRecommendation wire(Optional<ModelRecommendationQuery.Step> step, Optional<CostTier> tier) {
        ModelRecommendationService service = new ModelRecommendationService((activationId, query) ->
                RoutingFixtures.inputs(Optional.of("model-x"), step.map(asked -> CostTier.LOW)));
        ModelRecommendationOutcome outcome =
                service.recommend("a-1", new ModelRecommendationQuery("review", "java-jenny", step, tier), null);
        return dtos.read(((ModelRecommendationOutcome.Found) outcome).read());
    }

    @Test
    void rowSixWritesEveryMemberOfEachSourceAndTheTier() {
        ModelRecommendation wire = wire(
                Optional.of(new ModelRecommendationQuery.Step("software-dev", "review")),
                Optional.of(CostTier.PREMIUM));

        assertThat(wire.getTaskType()).isEqualTo("review");
        assertThat(wire.getObjective()).isEqualTo(ModelRecommendation.ObjectiveEnum.BALANCED);
        assertThat(wire.getTierConstraint()).isEqualTo(ModelRecommendation.TierConstraintEnum.MEDIUM);
        assertThat(wire.getCatalog().getLevel().getValue()).isEqualTo("org");
        ModelRecommendationTier tier = wire.getModelTier();
        assertThat(tier).isNotNull();
        assertThat(tier.getEffective()).isEqualTo(ModelRecommendationTier.EffectiveEnum.PREMIUM);
        assertThat(tier.getSource()).isEqualTo(ModelRecommendationTier.SourceEnum.REQUEST);
        assertThat(tier.getOffer()).isEqualTo(ModelRecommendationTier.OfferEnum.LOW);
        ModelRecommendationCandidate top = wire.getCandidates().get(0);
        assertThat(top.getSource()).isEqualTo(ModelRecommendationCandidate.SourceEnum.CATALOG);
        assertThat(top.getModelId()).isEqualTo("model-c");
        assertThat(top.getScore()).isEqualTo(new BigDecimal("0.9"));
        assertThat(top.getFitScore()).isEqualTo(new BigDecimal("0.9"));
        assertThat(top.getCostTier()).isEqualTo(ModelRecommendationCandidate.CostTierEnum.PREMIUM);
        assertThat(top.getWithinTierConstraint()).isFalse();
        assertThat(top.getWithinModelTier()).isTrue();
        assertThat(top.getRationale()).isNull();
        assertThat(top.getEffort()).isNull();
        assertThat(wire.getCandidates().get(1).getScore().toPlainString()).isEqualTo("0.732");
        ModelRecommendationCandidate profile = wire.getCandidates().get(2);
        assertThat(profile.getSource()).isEqualTo(ModelRecommendationCandidate.SourceEnum.PROFILE);
        assertThat(profile.getModelId()).isEqualTo("model-x");
        assertThat(profile.getEffort()).isEqualTo("high");
        assertThat(profile.getScore()).isNull();
        assertThat(profile.getCostTier()).isNull();
        assertThat(profile.getWithinTierConstraint()).isNull();
    }

    @Test
    void theLinksNameTheRequestTheCatalogTheProfileAndTheStep() {
        Map<String, Link> links = wire(
                        Optional.of(new ModelRecommendationQuery.Step("software-dev", "review")),
                        Optional.of(CostTier.PREMIUM))
                .getLinks();

        assertThat(links.keySet()).containsExactly("self", "catalog", "agent-profile", "step");
        assertThat(links.get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/task-types/review/model-recommendation"
                        + "?agentProfile=java-jenny&missionType=software-dev&step=review&modelTier=premium");
        assertThat(links.get("catalog").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/routing_catalog/default");
        assertThat(links.get("agent-profile").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/agent_profile/java-jenny");
        assertThat(links.get("step").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/review");
    }

    @Test
    void withNoStepAndNoTierTheStepLinkAndTheTierAreAbsent() {
        ModelRecommendation wire = wire(Optional.empty(), Optional.empty());

        assertThat(wire.getLinks().keySet()).containsExactly("self", "catalog", "agent-profile");
        assertThat(wire.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/task-types/review/model-recommendation?agentProfile=java-jenny");
        assertThat(wire.getModelTier()).isNull();
        assertThat(wire.getCandidates().get(0).getWithinModelTier()).isNull();
    }

    @Test
    void aNumberLosesItsTrailingZerosAndKeepsPlainNotation() {
        assertThat(ModelRecommendationDtos.number(new BigDecimal("0.300")).toString())
                .isEqualTo("0.3");
        assertThat(ModelRecommendationDtos.number(new BigDecimal("1.00")).toString())
                .isEqualTo("1");
        assertThat(ModelRecommendationDtos.number(new BigDecimal("1E+1")).toString())
                .isEqualTo("10");
        assertThat(ModelRecommendationDtos.number(new BigDecimal("0.12345678901234567890123"))
                        .toString())
                .isEqualTo("0.12345678901234567890123");
    }
}
