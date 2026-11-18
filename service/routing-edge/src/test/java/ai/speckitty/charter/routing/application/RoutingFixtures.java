package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.routing.domain.AccessMethod;
import ai.speckitty.charter.routing.domain.CatalogSource;
import ai.speckitty.charter.routing.domain.ModelCost;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import ai.speckitty.charter.routing.domain.RoutingPolicy;
import ai.speckitty.charter.routing.domain.RoutingWeights;
import ai.speckitty.charter.routing.domain.TaskFit;
import ai.speckitty.charter.routing.domain.TaskType;
import ai.speckitty.charter.routing.domain.TierConstraint;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Org-base's default routing catalog of research R7, built in code, and the inputs of a read over it. */
public final class RoutingFixtures {

    /** The tag the fake inputs answer. */
    public static final String ETAG = "sha256:" + "a".repeat(64);
    /** The fingerprint the fake inputs answer. */
    public static final String FINGERPRINT = "sha256:" + "b".repeat(64);

    private RoutingFixtures() {}

    /**
     * Org-base's default: balanced, weights 1 and 0.2; design: model-b 0.1 low, model-a 0.3 premium; review capped at
     * medium: model-c 0.9 premium, model-d 0.6 medium, model-e 0.5 low; triage declared with no fit.
     *
     * @return the catalog
     */
    public static RoutingCatalog orgDefault() {
        return new RoutingCatalog(
                "default",
                new PackIdentifier("org-base"),
                Optional.empty(),
                "2026-11-18T00:00:00Z",
                List.of(taskType("design"), taskType("review"), taskType("triage")),
                List.of(
                        model("model-b", CostTier.LOW, "design", "0.1"),
                        model("model-a", CostTier.PREMIUM, "design", "0.3"),
                        model("model-c", CostTier.PREMIUM, "review", "0.9"),
                        model("model-d", CostTier.MEDIUM, "review", "0.6"),
                        model("model-e", CostTier.LOW, "review", "0.5")),
                new RoutingPolicy(
                        RoutingObjective.BALANCED,
                        new RoutingWeights(BigDecimal.ONE, new BigDecimal("0.2"), BigDecimal.ZERO, BigDecimal.ZERO),
                        List.of(new TierConstraint("review", CostTier.MEDIUM))),
                List.of(new CatalogSource(
                        "example",
                        "https://example.invalid/catalog",
                        AccessMethod.MANUAL,
                        "2026-11-18T00:00:00Z",
                        Optional.empty())));
    }

    /**
     * The inputs of a read over org-base's default, with java-jenny's model and the given step offer.
     *
     * @param profileModel the profile's model
     * @param stepOffer the step's tier
     * @return the inputs
     */
    public static RecommendationInputs.Inputs inputs(Optional<String> profileModel, Optional<CostTier> stepOffer) {
        return new RecommendationInputs.Inputs(
                new ModelRecommendationCatalog("default", "org-base", PackLevel.ORG),
                orgDefault(),
                profileModel,
                profileModel.map(any -> "high"),
                stepOffer,
                FINGERPRINT,
                ETAG);
    }

    private static TaskType taskType(String id) {
        return new TaskType(id, id, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static ModelEntry model(String id, CostTier tier, String taskType, String score) {
        return new ModelEntry(
                id,
                "example",
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new TaskFit(taskType, new BigDecimal(score), Optional.empty(), Optional.empty())),
                new ModelCost(tier, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty());
    }
}
