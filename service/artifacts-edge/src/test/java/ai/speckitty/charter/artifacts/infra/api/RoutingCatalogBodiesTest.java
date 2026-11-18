package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.EffectiveRoutingCatalogRead;
import ai.speckitty.charter.api.model.RoutingCatalog;
import ai.speckitty.charter.api.model.RoutingCatalogCost;
import ai.speckitty.charter.api.model.RoutingCatalogList;
import ai.speckitty.charter.api.model.RoutingCatalogModel;
import ai.speckitty.charter.api.model.RoutingCatalogPolicy;
import ai.speckitty.charter.api.model.RoutingCatalogSource;
import ai.speckitty.charter.api.model.RoutingCatalogSummary;
import ai.speckitty.charter.api.model.RoutingCatalogTaskFit;
import ai.speckitty.charter.api.model.RoutingCatalogTaskType;
import ai.speckitty.charter.api.model.RoutingCatalogTierConstraint;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Facts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Words;
import ai.speckitty.charter.artifacts.application.EffectiveRoutingCatalogReads;
import ai.speckitty.charter.artifacts.application.RoutingCatalogView;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The routing catalog's wire form maps every member of the view, leaves unset what the file does not declare, answers
 * each number with its trailing zeros stripped and never a negative scale, and links each read of both trees as the
 * contract examples do.
 */
class RoutingCatalogBodiesTest {

    private static final PackIdentifier ORG_BASE = new PackIdentifier("org-base");
    private static final String AT = "2026-11-18T00:00:00Z";
    private static final Scope SCOPE = new Scope("a-1", "sha256:rev", "sha256:closure", "sha256:" + "f".repeat(64));

    /** A catalog that declares every optional member, its numbers written with trailing zeros. */
    private static final RoutingCatalogView FULL = new RoutingCatalogView(
            "cost-led",
            ORG_BASE,
            Optional.of("A cost-led catalog for the example packs."),
            AT,
            List.of(new RoutingCatalogView.TaskTypeView(
                    "review", "Review", Optional.of("Reviews a change."), Optional.of("high"), Optional.of("low"))),
            List.of(
                    new RoutingCatalogView.ModelView(
                            "model-a",
                            "example",
                            Optional.of("family-a"),
                            List.of("Bash", "Edit"),
                            List.of("careful"),
                            List.of("slow"),
                            List.of(new RoutingCatalogView.FitView(
                                    "review",
                                    new BigDecimal("0.90"),
                                    Optional.of("medium"),
                                    Optional.of("Reads well."))),
                            new RoutingCatalogView.CostView(
                                    "premium",
                                    Optional.of(new BigDecimal("10.00")),
                                    Optional.of(new BigDecimal("15.50")),
                                    Optional.of("USD"),
                                    Optional.of("https://example.invalid/pricing")),
                            Optional.of("medium")),
                    new RoutingCatalogView.ModelView(
                            "model-b",
                            "example",
                            Optional.empty(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(new RoutingCatalogView.FitView(
                                    "review", new BigDecimal("0.5"), Optional.empty(), Optional.empty())),
                            new RoutingCatalogView.CostView(
                                    "low", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                            Optional.empty())),
            new RoutingCatalogView.PolicyView(
                    "cost_first",
                    new BigDecimal("1.0"),
                    new BigDecimal("0.20"),
                    new BigDecimal("0.10"),
                    new BigDecimal("0.000"),
                    List.of(new RoutingCatalogView.ConstraintView("review", "medium"))),
            List.of(new RoutingCatalogView.SourceView(
                    "example", "https://example.invalid/catalog", "api", AT, Optional.of("CC-BY-4.0"))));

    /** A catalog that declares no optional member. */
    private static final RoutingCatalogView MINIMAL = new RoutingCatalogView(
            "default",
            ORG_BASE,
            Optional.empty(),
            AT,
            List.of(
                    new RoutingCatalogView.TaskTypeView(
                            "design", "Design", Optional.empty(), Optional.empty(), Optional.empty()),
                    new RoutingCatalogView.TaskTypeView(
                            "review", "Review", Optional.empty(), Optional.empty(), Optional.empty())),
            List.of(new RoutingCatalogView.ModelView(
                    "model-b",
                    "example",
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new RoutingCatalogView.FitView(
                            "design", new BigDecimal("0.1"), Optional.empty(), Optional.empty())),
                    new RoutingCatalogView.CostView(
                            "low", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                    Optional.empty())),
            new RoutingCatalogView.PolicyView(
                    "balanced",
                    new BigDecimal("1"),
                    new BigDecimal("0.2"),
                    new BigDecimal("0"),
                    new BigDecimal("0"),
                    List.of()),
            List.of(new RoutingCatalogView.SourceView(
                    "example", "https://example.invalid/catalog", "manual", AT, Optional.empty())));

    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private final RoutingCatalogBodies bodies = new RoutingCatalogBodies("/api/v1/");

    @Test
    void aFullCatalogMapsEveryMemberOfTheView() {
        RoutingCatalog wire = RoutingCatalogBodies.catalog(FULL);

        assertThat(wire.getId()).isEqualTo("cost-led");
        assertThat(wire.getDescription()).isEqualTo("A cost-led catalog for the example packs.");
        assertThat(wire.getGeneratedAt()).isEqualTo(OffsetDateTime.parse(AT));
        RoutingCatalogTaskType type = wire.getTaskTypes().get(0);
        assertThat(wire.getTaskTypes()).hasSize(1);
        assertThat(type.getId()).isEqualTo("review");
        assertThat(type.getTitle()).isEqualTo("Review");
        assertThat(type.getDescription()).isEqualTo("Reviews a change.");
        assertThat(type.getQualitySensitivity()).isEqualTo(RoutingCatalogTaskType.QualitySensitivityEnum.HIGH);
        assertThat(type.getCostSensitivity()).isEqualTo(RoutingCatalogTaskType.CostSensitivityEnum.LOW);

        assertThat(wire.getModels()).extracting(RoutingCatalogModel::getId).containsExactly("model-a", "model-b");
        RoutingCatalogModel model = wire.getModels().get(0);
        assertThat(model.getProvider()).isEqualTo("example");
        assertThat(model.getFamily()).isEqualTo("family-a");
        assertThat(model.getTools()).containsExactly("Bash", "Edit");
        assertThat(model.getStrengths()).containsExactly("careful");
        assertThat(model.getWeaknesses()).containsExactly("slow");
        assertThat(model.getLatencyTier()).isEqualTo(RoutingCatalogModel.LatencyTierEnum.MEDIUM);
        RoutingCatalogTaskFit fit = model.getTaskFit().get(0);
        assertThat(model.getTaskFit()).hasSize(1);
        assertThat(fit.getTaskType()).isEqualTo("review");
        assertThat(fit.getConfidence()).isEqualTo(RoutingCatalogTaskFit.ConfidenceEnum.MEDIUM);
        assertThat(fit.getRationale()).isEqualTo("Reads well.");
        RoutingCatalogCost cost = model.getCost();
        assertThat(cost.getTier()).isEqualTo(RoutingCatalogCost.TierEnum.PREMIUM);
        assertThat(cost.getCurrency()).isEqualTo("USD");
        assertThat(cost.getPricingSourceUrl()).isEqualTo("https://example.invalid/pricing");

        RoutingCatalogPolicy policy = wire.getRoutingPolicy();
        assertThat(policy.getObjective()).isEqualTo(RoutingCatalogPolicy.ObjectiveEnum.COST_FIRST);
        assertThat(policy.getTierConstraints()).singleElement().satisfies(constraint -> {
            assertThat(constraint.getTaskType()).isEqualTo("review");
            assertThat(constraint.getMaxTier()).isEqualTo(RoutingCatalogTierConstraint.MaxTierEnum.MEDIUM);
        });

        assertThat(wire.getSources()).singleElement().satisfies(source -> {
            assertThat(source.getName()).isEqualTo("example");
            assertThat(source.getUrl()).isEqualTo("https://example.invalid/catalog");
            assertThat(source.getAccessMethod()).isEqualTo(RoutingCatalogSource.AccessMethodEnum.API);
            assertThat(source.getSnapshotAt()).isEqualTo(AT);
            assertThat(source.getLicenseNotes()).isEqualTo("CC-BY-4.0");
        });
    }

    @Test
    void eachNumberIsTheDecimalWithItsTrailingZerosStrippedAndNoNegativeScale() {
        RoutingCatalog wire = RoutingCatalogBodies.catalog(FULL);
        RoutingCatalogModel model = wire.getModels().get(0);
        RoutingCatalogPolicy policy = wire.getRoutingPolicy();

        assertThat(model.getTaskFit().get(0).getScore().toPlainString()).isEqualTo("0.9");
        assertThat(String.valueOf(model.getCost().getInputPer1mUsd())).isEqualTo("10");
        assertThat(String.valueOf(model.getCost().getOutputPer1mUsd())).isEqualTo("15.5");
        assertThat(policy.getWeights().getQuality().toString()).isEqualTo("1");
        assertThat(policy.getWeights().getCost().toString()).isEqualTo("0.2");
        assertThat(policy.getWeights().getRisk().toString()).isEqualTo("0.1");
        assertThat(policy.getWeights().getLatency().toString()).isEqualTo("0");
        assertThat(wire.getModels().get(1).getTaskFit().get(0).getScore().toString())
                .isEqualTo("0.5");
    }

    @Test
    void aMinimalCatalogLeavesUnsetEveryOptionalMemberItsFileDoesNotDeclare() {
        RoutingCatalog wire = RoutingCatalogBodies.catalog(MINIMAL);

        assertThat(wire.getDescription()).isNull();
        assertThat(wire.getTaskTypes()).allSatisfy(type -> {
            assertThat(type.getDescription()).isNull();
            assertThat(type.getQualitySensitivity()).isNull();
            assertThat(type.getCostSensitivity()).isNull();
        });
        RoutingCatalogModel model = wire.getModels().get(0);
        assertThat(model.getFamily()).isNull();
        assertThat(model.getLatencyTier()).isNull();
        assertThat(model.getTaskFit().get(0).getConfidence()).isNull();
        assertThat(model.getTaskFit().get(0).getRationale()).isNull();
        assertThat(model.getCost().getInputPer1mUsd()).isNull();
        assertThat(model.getCost().getOutputPer1mUsd()).isNull();
        assertThat(model.getCost().getCurrency()).isNull();
        assertThat(model.getCost().getPricingSourceUrl()).isNull();
        assertThat(wire.getSources().get(0).getLicenseNotes()).isNull();

        JsonNode written = JSON.valueToTree(wire);
        assertThat(written.has("description")).isFalse();
        assertThat(written.get("taskTypes").get(0).has("qualitySensitivity")).isFalse();
        assertThat(written.get("models").get(0).has("family")).isFalse();
        assertThat(written.get("models").get(0).get("cost").has("inputPer1mUsd"))
                .isFalse();
        assertThat(written.get("models").get(0).get("tools").isEmpty()).isTrue();
        assertThat(written.get("routingPolicy").get("tierConstraints").isEmpty())
                .isTrue();
        assertThat(written.get("sources").get(0).has("licenseNotes")).isFalse();
    }

    @Test
    void theEnvelopeIsKindRoutingCatalogWithTheMemberAndLinksItselfAndItsCollection() {
        Artifact envelope = bodies.artifact(MINIMAL);

        assertThat(envelope.getKind()).isEqualTo("routing_catalog");
        assertThat(envelope.getId()).isEqualTo("default");
        assertThat(envelope.getPackId()).isEqualTo("org-base");
        assertThat(envelope.getLinks()).containsOnlyKeys("self", "collection");
        assertThat(envelope.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog/default");
        assertThat(envelope.getLinks().get("collection").getHref())
                .isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog");
        assertThat(envelope.getRoutingCatalog()).isEqualTo(RoutingCatalogBodies.catalog(MINIMAL));
    }

    @Test
    void theListSummarisesEachCatalogInTheGivenOrderAndLinksItself() {
        RoutingCatalogList list = bodies.list("org-base", List.of(FULL, MINIMAL));

        assertThat(list.getItems()).extracting(RoutingCatalogSummary::getId).containsExactly("cost-led", "default");
        RoutingCatalogSummary costLed = list.getItems().get(0);
        assertThat(costLed.getDescription()).isEqualTo("A cost-led catalog for the example packs.");
        assertThat(costLed.getObjective()).isEqualTo(RoutingCatalogSummary.ObjectiveEnum.COST_FIRST);
        assertThat(costLed.getTaskTypeCount()).isEqualTo(1);
        assertThat(costLed.getModelCount()).isEqualTo(2);
        assertThat(costLed.getLinks()).containsOnlyKeys("self");
        assertThat(costLed.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog/cost-led");
        RoutingCatalogSummary byDefault = list.getItems().get(1);
        assertThat(byDefault.getDescription()).isNull();
        assertThat(byDefault.getObjective()).isEqualTo(RoutingCatalogSummary.ObjectiveEnum.BALANCED);
        assertThat(byDefault.getTaskTypeCount()).isEqualTo(2);
        assertThat(byDefault.getModelCount()).isEqualTo(1);
        assertThat(list.getLinks()).containsOnlyKeys("self");
        assertThat(list.getLinks().get("self").getHref()).isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog");
    }

    @Test
    void theListLinksThePackAsTheRequestNamedItAndEachItemByItsOwnPack() {
        RoutingCatalogList list = bodies.list("by name", List.of(MINIMAL));

        assertThat(list.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/by%20name/artifacts/routing_catalog");
        assertThat(list.getItems().get(0).getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog/default");
    }

    @Test
    void theEffectiveListLinksEachWinnerUnderTheActivationAndInItsPack() {
        Held costLed = new Held("cost-led", ORG_BASE, PackLevel.ORG, "open");
        Held byDefault = new Held("default", ORG_BASE, PackLevel.ORG, "open");

        RoutingCatalogList list = bodies.effectiveList(new EffectiveRoutingCatalogReads.Listed(
                SCOPE,
                List.of(
                        new EffectiveRoutingCatalogReads.Item(costLed, FULL),
                        new EffectiveRoutingCatalogReads.Item(byDefault, MINIMAL))));

        assertThat(list.getItems()).extracting(RoutingCatalogSummary::getId).containsExactly("cost-led", "default");
        assertThat(list.getItems().get(0).getObjective()).isEqualTo(RoutingCatalogSummary.ObjectiveEnum.COST_FIRST);
        assertThat(list.getItems().get(0).getModelCount()).isEqualTo(2);
        assertThat(list.getItems().get(0).getLinks()).containsOnlyKeys("effective-artifact", "artifact");
        assertThat(list.getItems().get(0).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/routing_catalog/cost-led");
        assertThat(list.getItems().get(0).getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog/cost-led");
        assertThat(list.getItems().get(1).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/routing_catalog/default");
        assertThat(list.getItems().get(1).getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog/default");
        assertThat(list.getLinks()).containsOnlyKeys("self", "up");
        assertThat(list.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/routing_catalog");
        assertThat(list.getLinks().get("up").getHref()).isEqualTo("/api/v1/activations/a-1");
    }

    @Test
    void theEffectiveItemCarriesTheScopeTheFactsTheCatalogAndItsLinks() {
        Facts facts = new Facts(
                new Held("default", ORG_BASE, PackLevel.ORG, "open"),
                "active",
                false,
                "selected",
                List.of(),
                Optional.empty());

        EffectiveRoutingCatalogRead wire =
                bodies.effective(new EffectiveRoutingCatalogReads.Read(SCOPE, facts, MINIMAL));

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getRevision()).isEqualTo("sha256:rev");
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        assertThat(wire.getKind()).isEqualTo(EffectiveRoutingCatalogRead.KindEnum.ROUTING_CATALOG);
        assertThat(wire.getArtifactId()).isEqualTo("default");
        assertThat(wire.getPackId()).isEqualTo("org-base");
        assertThat(wire.getLevel()).isEqualTo(EffectiveRoutingCatalogRead.LevelEnum.ORG);
        assertThat(wire.getState()).isEqualTo(EffectiveRoutingCatalogRead.StateEnum.ACTIVE);
        assertThat(wire.getRequired()).isFalse();
        assertThat(wire.getPolicy()).isEqualTo(EffectiveRoutingCatalogRead.PolicyEnum.OPEN);
        assertThat(wire.getReason()).isEqualTo(EffectiveRoutingCatalogRead.ReasonEnum.SELECTED);
        assertThat(wire.getRoutingCatalog()).isEqualTo(RoutingCatalogBodies.catalog(MINIMAL));
        assertThat(wire.getSatisfiedBy()).isNull();
        assertThat(wire.getSuggestion()).isNull();
        assertThat(wire.getLinks()).containsOnlyKeys("self", "artifact", "collection", "up");
        assertThat(wire.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/routing_catalog/default");
        assertThat(wire.getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/org-base/artifacts/routing_catalog/default");
        assertThat(wire.getLinks().get("collection").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/routing_catalog");
        assertThat(wire.getLinks().get("up").getHref()).isEqualTo("/api/v1/activations/a-1");
    }

    @Test
    void theEffectiveItemOfARequiredSuggestionCarriesWhatSatisfiesItAndTheSuggestionsWords() {
        Facts facts = new Facts(
                new Held("default", ORG_BASE, PackLevel.BUILT_IN, "locked"),
                "inactive",
                true,
                "overridden",
                List.of(new Urn(NodeKind.ROUTING_CATALOG, "cost-led")),
                Optional.of(new Words(Optional.of("when cost matters"), Optional.empty())));

        EffectiveRoutingCatalogRead wire =
                bodies.effective(new EffectiveRoutingCatalogReads.Read(SCOPE, facts, MINIMAL));

        assertThat(wire.getLevel()).isEqualTo(EffectiveRoutingCatalogRead.LevelEnum.BUILT_IN);
        assertThat(wire.getState()).isEqualTo(EffectiveRoutingCatalogRead.StateEnum.INACTIVE);
        assertThat(wire.getRequired()).isTrue();
        assertThat(wire.getPolicy()).isEqualTo(EffectiveRoutingCatalogRead.PolicyEnum.LOCKED);
        assertThat(wire.getReason()).isEqualTo(EffectiveRoutingCatalogRead.ReasonEnum.OVERRIDDEN);
        JsonNode written = JSON.valueToTree(wire);
        assertThat(written.get("satisfiedBy").size()).isEqualTo(1);
        assertThat(written.get("satisfiedBy").get(0).get("kind").asString()).isEqualTo("routing_catalog");
        assertThat(written.get("satisfiedBy").get(0).get("artifactId").asString())
                .isEqualTo("cost-led");
        assertThat(written.get("suggestion").get("when").asString()).isEqualTo("when cost matters");
        assertThat(written.get("suggestion").has("reason")).isFalse();
    }
}
