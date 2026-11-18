package ai.speckitty.charter.routing.infra.api;

import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.ModelRecommendation;
import ai.speckitty.charter.api.model.ModelRecommendationCandidate;
import ai.speckitty.charter.api.model.ModelRecommendationCatalog;
import ai.speckitty.charter.api.model.ModelRecommendationTier;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.routing.application.ModelRecommendationQuery;
import ai.speckitty.charter.routing.application.ModelRecommendationRead;
import ai.speckitty.charter.routing.domain.ranking.CatalogCandidate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of a model recommendation ({@code getModelRecommendation}): the task type, the catalog in force, its
 * objective and constraint, the effective tier and the candidates, each score the exact decimal with its trailing zeros
 * stripped, in plain notation. The links are built under the base path the controllers are mapped under: {@code self}
 * is the request with the query it asked, in the contract's parameter order; {@code catalog}, {@code agent-profile} and,
 * with a step, {@code step} are their reads under the activation.
 */
@Component
public class ModelRecommendationDtos {

    private static final String ACTIVATIONS = "activations";
    private static final String ARTIFACTS = "artifacts";

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public ModelRecommendationDtos(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The answer of a recommendation.
     *
     * @param read what the application ring answered
     * @return the wire body
     */
    public ModelRecommendation read(ModelRecommendationRead read) {
        ModelRecommendation wire = new ModelRecommendation(
                read.query().taskType(),
                new ModelRecommendationCatalog(
                        read.catalog().id(),
                        read.catalog().packId(),
                        ModelRecommendationCatalog.LevelEnum.fromValue(
                                read.catalog().level().token())),
                ModelRecommendation.ObjectiveEnum.fromValue(read.objective().token()),
                read.candidates().stream()
                        .map(ModelRecommendationDtos::candidate)
                        .toList(),
                links(read));
        read.tierConstraint()
                .ifPresent(
                        tier -> wire.setTierConstraint(ModelRecommendation.TierConstraintEnum.fromValue(tier.token())));
        read.modelTier().ifPresent(tier -> {
            ModelRecommendationTier wireTier = new ModelRecommendationTier(
                    ModelRecommendationTier.EffectiveEnum.fromValue(
                            tier.effective().token()),
                    ModelRecommendationTier.SourceEnum.fromValue(tier.source().token()));
            tier.offer()
                    .ifPresent(offer -> wireTier.setOffer(ModelRecommendationTier.OfferEnum.fromValue(offer.token())));
            wire.setModelTier(wireTier);
        });
        return wire;
    }

    /** One candidate: a catalog candidate's members, or a profile candidate's, by an exhaustive switch. */
    static ModelRecommendationCandidate candidate(
            ai.speckitty.charter.routing.application.ModelRecommendationCandidate each) {
        ModelRecommendationCandidate wire = new ModelRecommendationCandidate(
                ModelRecommendationCandidate.SourceEnum.fromValue(each.source()), each.modelId());
        switch (each) {
            case ai.speckitty.charter.routing.application.ModelRecommendationCandidate.FromCatalog catalog -> {
                CatalogCandidate ranked = catalog.candidate();
                wire.setScore(number(ranked.score()));
                wire.setFitScore(number(ranked.fitScore()));
                wire.setRationale(ranked.rationale().orElse(null));
                wire.setCostTier(ModelRecommendationCandidate.CostTierEnum.fromValue(
                        ranked.costTier().token()));
                wire.setWithinTierConstraint(ranked.withinTierConstraint());
                wire.setWithinModelTier(ranked.withinModelTier().orElse(null));
            }
            case ai.speckitty.charter.routing.application.ModelRecommendationCandidate.FromProfile profile ->
                wire.setEffort(profile.effort().orElse(null));
        }
        return wire;
    }

    /** A number on the wire: the exact decimal, its trailing zeros stripped, so {@code 0.30} answers {@code 0.3}. */
    static BigDecimal number(BigDecimal decimal) {
        BigDecimal stripped = decimal.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private Map<String, Link> links(ModelRecommendationRead read) {
        String activation = read.activationId();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(self(read)));
        links.put(
                "catalog",
                new Link(href(
                        ACTIVATIONS,
                        activation,
                        ARTIFACTS,
                        ArtifactKind.ROUTING_CATALOG.token(),
                        read.catalog().id())));
        links.put(
                "agent-profile",
                new Link(href(
                        ACTIVATIONS,
                        activation,
                        ARTIFACTS,
                        ArtifactKind.AGENT_PROFILE.token(),
                        read.query().agentProfile())));
        read.query()
                .step()
                .ifPresent(step -> links.put(
                        "step",
                        new Link(href(
                                ACTIVATIONS,
                                activation,
                                ARTIFACTS,
                                ArtifactKind.MISSION_TYPE.token(),
                                step.missionType(),
                                "steps",
                                step.id()))));
        return links;
    }

    /** The request, with the query it asked in the contract's parameter order, each value percent-encoded. */
    private String self(ModelRecommendationRead read) {
        ModelRecommendationQuery query = read.query();
        List<String> parameters = new ArrayList<>();
        parameters.add("agentProfile=" + encoded(query.agentProfile()));
        query.step().ifPresent(step -> {
            parameters.add("missionType=" + encoded(step.missionType()));
            parameters.add("step=" + encoded(step.id()));
        });
        query.modelTier().ifPresent(tier -> parameters.add("modelTier=" + tier.token()));
        return href(ACTIVATIONS, read.activationId(), "task-types", query.taskType(), "model-recommendation") + "?"
                + String.join("&", parameters);
    }

    private static String encoded(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
