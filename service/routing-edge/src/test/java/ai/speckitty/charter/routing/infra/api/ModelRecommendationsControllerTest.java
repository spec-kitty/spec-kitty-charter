package ai.speckitty.charter.routing.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.ModelRecommendation;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.routing.application.ModelRecommendationQuery;
import ai.speckitty.charter.routing.application.ModelRecommendationService;
import ai.speckitty.charter.routing.application.RoutingFixtures;
import ai.speckitty.charter.web.InvalidParameter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The recommendation controller: the parameters it judges itself, and the headers of a 200 and a 304. */
class ModelRecommendationsControllerTest {

    private final ModelRecommendationsController controller = new ModelRecommendationsController(
            new ModelRecommendationService(
                    (activationId, query) -> RoutingFixtures.inputs(Optional.of("model-x"), Optional.of(CostTier.LOW))),
            new ModelRecommendationDtos("/api/v1"));

    @Test
    void aFoundAnswerCarriesTheQuotedTagAndTheFingerprint() {
        ResponseEntity<ModelRecommendation> answer =
                controller.getModelRecommendation("java-jenny", "a-1", "design", null, null, null, null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getHeaders().getETag()).isEqualTo("\"" + RoutingFixtures.ETAG + "\"");
        assertThat(answer.getHeaders().getFirst(HeaderNames.SOURCE_FINGERPRINT)).isEqualTo(RoutingFixtures.FINGERPRINT);
        assertThat(answer.getBody()).isNotNull();
    }

    @Test
    void aMatchingTagIsNotModifiedWithTheTagAlone() {
        ResponseEntity<ModelRecommendation> answer = controller.getModelRecommendation(
                "java-jenny", "a-1", "design", "\"" + RoutingFixtures.ETAG + "\"", null, null, null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(answer.getHeaders().getETag()).isEqualTo("\"" + RoutingFixtures.ETAG + "\"");
        assertThat(answer.getHeaders().getFirst(HeaderNames.SOURCE_FINGERPRINT)).isNull();
        assertThat(answer.getBody()).isNull();
    }

    @Test
    void theStepIsBothOrNeither() {
        assertThat(ModelRecommendationsController.step(null, null)).isEmpty();
        assertThat(ModelRecommendationsController.step("software-dev", "review"))
                .contains(new ModelRecommendationQuery.Step("software-dev", "review"));
        assertThatThrownBy(() -> ModelRecommendationsController.step("software-dev", null))
                .isInstanceOfSatisfying(
                        InvalidParameter.class,
                        refused -> assertThat(refused.detail())
                                .isEqualTo(
                                        "The query parameter missionType is \"software-dev\", which is sent without step,"
                                                + " and the two go together."));
        assertThatThrownBy(() -> ModelRecommendationsController.step(null, "review"))
                .isInstanceOfSatisfying(
                        InvalidParameter.class,
                        refused -> assertThat(refused.detail())
                                .isEqualTo(
                                        "The query parameter step is \"review\", which is sent without missionType, and"
                                                + " the two go together."));
    }

    @Test
    void theTierIsACostTierOrNone() {
        assertThat(ModelRecommendationsController.tier(null)).isEmpty();
        assertThat(ModelRecommendationsController.tier("premium")).contains(CostTier.PREMIUM);
        assertThatThrownBy(() -> ModelRecommendationsController.tier("cheap"))
                .isInstanceOfSatisfying(
                        InvalidParameter.class,
                        refused -> assertThat(refused.detail())
                                .isEqualTo("The query parameter modelTier is \"cheap\", which is not a cost tier."));
    }
}
