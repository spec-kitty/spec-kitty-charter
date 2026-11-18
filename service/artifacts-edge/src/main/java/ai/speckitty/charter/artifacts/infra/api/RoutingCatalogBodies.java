package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.EffectiveRoutingCatalogRead;
import ai.speckitty.charter.api.model.Link;
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
import ai.speckitty.charter.api.model.RoutingCatalogWeights;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.EffectiveRoutingCatalogReads;
import ai.speckitty.charter.artifacts.application.RoutingCatalogView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.routing.domain.CatalogSource;
import ai.speckitty.charter.routing.domain.ModelCost;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingPolicy;
import ai.speckitty.charter.routing.domain.RoutingWeights;
import ai.speckitty.charter.routing.domain.TaskFit;
import ai.speckitty.charter.routing.domain.TaskType;
import ai.speckitty.charter.routing.domain.TierConstraint;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
 * The wire form of the routing catalog reads (ADR-0080 decision 6), in both trees: the pack's list and item, and their
 * twins under an activation with the facts the activation gives the winner. A number goes to the wire as the exact
 * decimal the file writes, with its trailing zeros stripped, in plain notation; an optional member the file does not
 * declare is left unset, so the wire omits it.
 */
@Component
public class RoutingCatalogBodies {

    private static final String PACKS = "packs";
    private static final String ACTIVATIONS = "activations";
    private static final String ARTIFACTS = "artifacts";
    private static final String KIND = ArtifactKind.ROUTING_CATALOG.token();

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public RoutingCatalogBodies(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * A pack's catalogs.
     *
     * @param pack the pack, as the request named it
     * @param catalogs the catalogs, in id order
     * @return the {@code RoutingCatalogList}
     */
    public RoutingCatalogList list(String pack, List<RoutingCatalogView> catalogs) {
        List<RoutingCatalogSummary> items = catalogs.stream()
                .map(view -> summary(view, links("self", item(view.packId().value(), view.id()))))
                .toList();
        return new RoutingCatalogList(items, links("self", collection(pack)));
    }

    /**
     * One catalog of a pack, in the Artifact envelope.
     *
     * @param view the catalog
     * @return the envelope, its member {@code routingCatalog} set
     */
    public Artifact artifact(RoutingCatalogView view) {
        String pack = view.packId().value();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(item(pack, view.id())));
        links.put("collection", new Link(collection(pack)));
        Artifact envelope = new Artifact(KIND, view.id(), pack, links);
        envelope.setRoutingCatalog(catalog(view.catalog()));
        return envelope;
    }

    /**
     * The catalog winners of an activation.
     *
     * @param listed what the application ring read
     * @return the {@code RoutingCatalogList}
     */
    public RoutingCatalogList effectiveList(EffectiveRoutingCatalogReads.Listed listed) {
        String activation = listed.scope().activationId();
        List<RoutingCatalogSummary> items = listed.items().stream()
                .map(item -> {
                    Map<String, Link> links = new LinkedHashMap<>();
                    links.put(
                            "effective-artifact",
                            new Link(href(
                                    ACTIVATIONS,
                                    activation,
                                    ARTIFACTS,
                                    KIND,
                                    item.winner().id())));
                    links.put(
                            "artifact",
                            new Link(href(
                                    PACKS,
                                    item.winner().packId().value(),
                                    ARTIFACTS,
                                    KIND,
                                    item.winner().id())));
                    return summary(item.catalog(), links);
                })
                .toList();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(href(ACTIVATIONS, activation, ARTIFACTS, KIND)));
        links.put("up", new Link(href(ACTIVATIONS, activation)));
        return new RoutingCatalogList(items, links);
    }

    /**
     * One catalog under an activation, with its facts.
     *
     * @param read what the application ring read
     * @return the {@code EffectiveRoutingCatalogRead}
     */
    public EffectiveRoutingCatalogRead effective(EffectiveRoutingCatalogReads.Read read) {
        ActivationArtifacts.Scope scope = read.scope();
        ActivationArtifacts.Facts facts = read.facts();
        ActivationArtifacts.Held winner = facts.held();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(href(ACTIVATIONS, scope.activationId(), ARTIFACTS, KIND, winner.id())));
        links.put("artifact", new Link(href(PACKS, winner.packId().value(), ARTIFACTS, KIND, winner.id())));
        links.put("collection", new Link(href(ACTIVATIONS, scope.activationId(), ARTIFACTS, KIND)));
        links.put("up", new Link(href(ACTIVATIONS, scope.activationId())));
        EffectiveRoutingCatalogRead wire = new EffectiveRoutingCatalogRead(
                scope.activationId(),
                scope.revision(),
                scope.governanceFingerprint(),
                EffectiveRoutingCatalogRead.KindEnum.ROUTING_CATALOG,
                winner.id(),
                winner.packId().value(),
                EffectiveRoutingCatalogRead.LevelEnum.fromValue(winner.level().token()),
                EffectiveRoutingCatalogRead.StateEnum.fromValue(facts.state()),
                facts.required(),
                EffectiveRoutingCatalogRead.PolicyEnum.fromValue(winner.policy()),
                EffectiveRoutingCatalogRead.ReasonEnum.fromValue(facts.reason()),
                catalog(read.catalog().catalog()),
                links);
        if (!facts.satisfiedBy().isEmpty()) {
            wire.setSatisfiedBy(
                    facts.satisfiedBy().stream().map(EffectiveBodies::reference).toList());
        }
        facts.suggestion().map(EffectiveBodies::suggestion).ifPresent(wire::setSuggestion);
        return wire;
    }

    /**
     * The catalog as its pack declares it, in camel case.
     *
     * @param catalog the catalog
     * @return the {@code RoutingCatalog}
     */
    static RoutingCatalog catalog(ai.speckitty.charter.routing.domain.RoutingCatalog catalog) {
        RoutingCatalog wire = new RoutingCatalog(
                catalog.id(),
                OffsetDateTime.parse(catalog.generatedAt()),
                catalog.taskTypes().stream().map(RoutingCatalogBodies::taskType).toList(),
                catalog.models().stream().map(RoutingCatalogBodies::model).toList(),
                policy(catalog.policy()),
                catalog.sources().stream().map(RoutingCatalogBodies::source).toList());
        wire.setDescription(catalog.description().orElse(null));
        return wire;
    }

    /** A number on the wire: the exact decimal, its trailing zeros stripped, so {@code 0.30} answers {@code 0.3}. */
    static BigDecimal number(BigDecimal decimal) {
        BigDecimal stripped = decimal.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private RoutingCatalogSummary summary(RoutingCatalogView view, Map<String, Link> links) {
        ai.speckitty.charter.routing.domain.RoutingCatalog catalog = view.catalog();
        RoutingCatalogSummary summary = new RoutingCatalogSummary(
                catalog.id(),
                RoutingCatalogSummary.ObjectiveEnum.fromValue(
                        catalog.policy().objective().token()),
                catalog.taskTypes().size(),
                catalog.models().size(),
                links);
        summary.setDescription(catalog.description().orElse(null));
        return summary;
    }

    private static RoutingCatalogTaskType taskType(TaskType type) {
        RoutingCatalogTaskType wire = new RoutingCatalogTaskType(type.id(), type.title());
        wire.setDescription(type.description().orElse(null));
        wire.setQualitySensitivity(type.qualitySensitivity()
                .map(grade -> RoutingCatalogTaskType.QualitySensitivityEnum.fromValue(grade.token()))
                .orElse(null));
        wire.setCostSensitivity(type.costSensitivity()
                .map(grade -> RoutingCatalogTaskType.CostSensitivityEnum.fromValue(grade.token()))
                .orElse(null));
        return wire;
    }

    private static RoutingCatalogModel model(ModelEntry model) {
        RoutingCatalogModel wire = new RoutingCatalogModel(
                model.id(),
                model.provider(),
                model.tools(),
                model.strengths(),
                model.weaknesses(),
                model.taskFit().stream().map(RoutingCatalogBodies::fit).toList(),
                cost(model.cost()));
        wire.setFamily(model.family().orElse(null));
        wire.setLatencyTier(model.latencyTier()
                .map(grade -> RoutingCatalogModel.LatencyTierEnum.fromValue(grade.token()))
                .orElse(null));
        return wire;
    }

    private static RoutingCatalogTaskFit fit(TaskFit fit) {
        RoutingCatalogTaskFit wire = new RoutingCatalogTaskFit(fit.taskType(), number(fit.score()));
        wire.setConfidence(fit.confidence()
                .map(grade -> RoutingCatalogTaskFit.ConfidenceEnum.fromValue(grade.token()))
                .orElse(null));
        wire.setRationale(fit.rationale().orElse(null));
        return wire;
    }

    private static RoutingCatalogCost cost(ModelCost cost) {
        RoutingCatalogCost wire = new RoutingCatalogCost(
                RoutingCatalogCost.TierEnum.fromValue(cost.tier().token()));
        wire.setInputPer1mUsd(
                cost.inputPer1mUsd().map(RoutingCatalogBodies::number).orElse(null));
        wire.setOutputPer1mUsd(
                cost.outputPer1mUsd().map(RoutingCatalogBodies::number).orElse(null));
        wire.setCurrency(cost.currency().orElse(null));
        wire.setPricingSourceUrl(cost.pricingSourceUrl().orElse(null));
        return wire;
    }

    private static RoutingCatalogPolicy policy(RoutingPolicy policy) {
        RoutingWeights weights = policy.weights();
        return new RoutingCatalogPolicy(
                RoutingCatalogPolicy.ObjectiveEnum.fromValue(policy.objective().token()),
                new RoutingCatalogWeights(
                        number(weights.quality()),
                        number(weights.cost()),
                        number(weights.risk()),
                        number(weights.latency())),
                policy.tierConstraints().stream()
                        .map(RoutingCatalogBodies::constraint)
                        .toList());
    }

    private static RoutingCatalogTierConstraint constraint(TierConstraint constraint) {
        return new RoutingCatalogTierConstraint(
                constraint.taskType(),
                RoutingCatalogTierConstraint.MaxTierEnum.fromValue(
                        constraint.maxTier().token()));
    }

    private static RoutingCatalogSource source(CatalogSource source) {
        RoutingCatalogSource wire = new RoutingCatalogSource(
                source.name(),
                source.url(),
                RoutingCatalogSource.AccessMethodEnum.fromValue(
                        source.accessMethod().token()),
                source.snapshotAt());
        wire.setLicenseNotes(source.licenseNotes().orElse(null));
        return wire;
    }

    private String collection(String pack) {
        return href(PACKS, pack, ARTIFACTS, KIND);
    }

    private String item(String pack, String id) {
        return href(PACKS, pack, ARTIFACTS, KIND, id);
    }

    private static Map<String, Link> links(String relation, String href) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(relation, new Link(href));
        return links;
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
