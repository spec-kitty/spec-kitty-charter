package ai.speckitty.charter.routing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The use case of {@code getModelRecommendation} over a fake activation half: the effective tier, the profile's
 * candidate placed last, the task type refused after the port and before the tag, and the {@code 304}.
 */
class ModelRecommendationServiceTest {

    private static final Optional<ModelRecommendationQuery.Step> NO_STEP = Optional.empty();
    private static final Optional<ModelRecommendationQuery.Step> REVIEW_STEP =
            Optional.of(new ModelRecommendationQuery.Step("software-dev", "review"));

    private final List<ModelRecommendationQuery> asked = new ArrayList<>();

    private ModelRecommendationService service(RecommendationInputs.Inputs inputs) {
        return new ModelRecommendationService((activationId, query) -> {
            asked.add(query);
            return inputs;
        });
    }

    private static ModelRecommendationQuery query(
            String taskType, Optional<ModelRecommendationQuery.Step> step, Optional<CostTier> tier) {
        return new ModelRecommendationQuery(taskType, "java-jenny", step, tier);
    }

    private static ModelRecommendationRead found(ModelRecommendationOutcome outcome) {
        assertThat(outcome).isInstanceOf(ModelRecommendationOutcome.Found.class);
        return ((ModelRecommendationOutcome.Found) outcome).read();
    }

    @Test
    void theExactTieIsAnsweredByModelIdAndTheProfileCandidateComesLast() {
        ModelRecommendationOutcome outcome = service(RoutingFixtures.inputs(Optional.of("model-x"), Optional.empty()))
                .recommend("a-1", query("design", NO_STEP, Optional.empty()), null);

        ModelRecommendationRead read = found(outcome);
        assertThat(read.objective()).isEqualTo(RoutingObjective.BALANCED);
        assertThat(read.tierConstraint()).isEmpty();
        assertThat(read.modelTier()).isEmpty();
        assertThat(read.candidates())
                .extracting(ModelRecommendationCandidate::source, ModelRecommendationCandidate::modelId)
                .containsExactly(tuple("catalog", "model-a"), tuple("catalog", "model-b"), tuple("profile", "model-x"));
        assertThat(read.candidates().get(2))
                .isEqualTo(new ModelRecommendationCandidate.FromProfile("model-x", Optional.of("high")));
        ModelRecommendationOutcome.Found answer = (ModelRecommendationOutcome.Found) outcome;
        assertThat(answer.etag()).isEqualTo(RoutingFixtures.ETAG);
        assertThat(answer.sourceFingerprint()).isEqualTo(RoutingFixtures.FINGERPRINT);
        assertThat(read.catalog().id()).isEqualTo("default");
        assertThat(read.activationId()).isEqualTo("a-1");
    }

    @Test
    void aProfileWithNoModelGivesNoProfileCandidate() {
        ModelRecommendationRead read = found(service(RoutingFixtures.inputs(Optional.empty(), Optional.empty()))
                .recommend("a-1", query("design", NO_STEP, Optional.empty()), null));

        assertThat(read.candidates())
                .extracting(ModelRecommendationCandidate::source)
                .containsOnly("catalog");
    }

    @Test
    void theProfileCandidateIsListedEvenWhenACatalogCandidateNamesTheSameModel() {
        ModelRecommendationRead read = found(service(RoutingFixtures.inputs(Optional.of("model-a"), Optional.empty()))
                .recommend("a-1", query("design", NO_STEP, Optional.empty()), null));

        assertThat(read.candidates())
                .extracting(ModelRecommendationCandidate::source, ModelRecommendationCandidate::modelId)
                .containsExactly(tuple("catalog", "model-a"), tuple("catalog", "model-b"), tuple("profile", "model-a"));
    }

    @Test
    void theStepsTierIsEffectiveWhenTheRequestAsksForNone() {
        ModelRecommendationRead read =
                found(service(RoutingFixtures.inputs(Optional.empty(), Optional.of(CostTier.LOW)))
                        .recommend("a-1", query("review", REVIEW_STEP, Optional.empty()), null));

        assertThat(read.modelTier())
                .contains(new ModelRecommendationTier(
                        CostTier.LOW, ModelRecommendationTier.Source.STEP, Optional.of(CostTier.LOW)));
        assertThat(read.tierConstraint()).contains(CostTier.MEDIUM);
        assertThat(read.candidates())
                .extracting(ModelRecommendationCandidate::modelId)
                .containsExactly("model-c", "model-e");
    }

    @Test
    void theRequestsTierOverridesTheStepsAndKeepsItsOffer() {
        ModelRecommendationRead read =
                found(service(RoutingFixtures.inputs(Optional.empty(), Optional.of(CostTier.LOW)))
                        .recommend("a-1", query("review", REVIEW_STEP, Optional.of(CostTier.PREMIUM)), null));

        assertThat(read.modelTier())
                .contains(new ModelRecommendationTier(
                        CostTier.PREMIUM, ModelRecommendationTier.Source.REQUEST, Optional.of(CostTier.LOW)));
        assertThat(read.candidates())
                .extracting(ModelRecommendationCandidate::modelId)
                .containsExactly("model-c", "model-d");
    }

    @Test
    void aTaskTypeTheCatalogDoesNotDeclareIsRefusedWhateverTheTag() {
        ModelRecommendationService service = service(RoutingFixtures.inputs(Optional.empty(), Optional.empty()));

        assertThatThrownBy(() -> service.recommend(
                        "a-1", query("deploy", NO_STEP, Optional.empty()), "\"" + RoutingFixtures.ETAG + "\""))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.TASK_TYPE_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("The routing catalog default in force declares no task type deploy.");
                });
    }

    @Test
    void aMatchingIfNoneMatchIsNotModifiedAndAnotherTagIsFound() {
        ModelRecommendationService service = service(RoutingFixtures.inputs(Optional.empty(), Optional.empty()));
        ModelRecommendationQuery query = query("design", NO_STEP, Optional.empty());

        assertThat(service.recommend("a-1", query, "\"" + RoutingFixtures.ETAG + "\""))
                .isEqualTo(new ModelRecommendationOutcome.NotModified(RoutingFixtures.ETAG));
        assertThat(service.recommend("a-1", query, "*"))
                .isEqualTo(new ModelRecommendationOutcome.NotModified(RoutingFixtures.ETAG));
        assertThat(service.recommend("a-1", query, "\"sha256:" + "c".repeat(64) + "\""))
                .isInstanceOf(ModelRecommendationOutcome.Found.class);
        assertThat(asked).containsOnly(query);
    }

    @Test
    void oneHundredIdenticalCallsAnswerEqualBodiesAndTags() {
        ModelRecommendationService service =
                service(RoutingFixtures.inputs(Optional.of("model-x"), Optional.of(CostTier.LOW)));
        ModelRecommendationQuery query = query("review", REVIEW_STEP, Optional.of(CostTier.PREMIUM));
        ModelRecommendationOutcome first = service.recommend("a-1", query, null);

        for (int call = 1; call < 100; call++) {
            assertThat(service.recommend("a-1", query, null)).isEqualTo(first);
        }
    }
}
