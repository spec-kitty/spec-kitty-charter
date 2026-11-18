package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.web.DecimalJson;
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
        envelope.setRoutingCatalog(catalog(view));
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
                catalog(read.catalog()),
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
     * @param view the catalog
     * @return the {@code RoutingCatalog}
     */
    static RoutingCatalog catalog(RoutingCatalogView view) {
        RoutingCatalog wire = new RoutingCatalog(
                view.id(),
                OffsetDateTime.parse(view.generatedAt()),
                view.taskTypes().stream().map(RoutingCatalogBodies::taskType).toList(),
                view.models().stream().map(RoutingCatalogBodies::model).toList(),
                policy(view.policy()),
                view.sources().stream().map(RoutingCatalogBodies::source).toList());
        wire.setDescription(view.description().orElse(null));
        return wire;
    }


    private RoutingCatalogSummary summary(RoutingCatalogView view, Map<String, Link> links) {
        RoutingCatalogSummary summary = new RoutingCatalogSummary(
                view.id(),
                RoutingCatalogSummary.ObjectiveEnum.fromValue(view.policy().objective()),
                view.taskTypes().size(),
                view.models().size(),
                links);
        summary.setDescription(view.description().orElse(null));
        return summary;
    }

    private static RoutingCatalogTaskType taskType(RoutingCatalogView.TaskTypeView type) {
        RoutingCatalogTaskType wire = new RoutingCatalogTaskType(type.id(), type.title());
        wire.setDescription(type.description().orElse(null));
        wire.setQualitySensitivity(type.qualitySensitivity()
                .map(RoutingCatalogTaskType.QualitySensitivityEnum::fromValue)
                .orElse(null));
        wire.setCostSensitivity(type.costSensitivity()
                .map(RoutingCatalogTaskType.CostSensitivityEnum::fromValue)
                .orElse(null));
        return wire;
    }

    private static RoutingCatalogModel model(RoutingCatalogView.ModelView model) {
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
                .map(RoutingCatalogModel.LatencyTierEnum::fromValue)
                .orElse(null));
        return wire;
    }

    private static RoutingCatalogTaskFit fit(RoutingCatalogView.FitView fit) {
        RoutingCatalogTaskFit wire = new RoutingCatalogTaskFit(fit.taskType(), DecimalJson.wire(fit.score()));
        wire.setConfidence(fit.confidence()
                .map(RoutingCatalogTaskFit.ConfidenceEnum::fromValue)
                .orElse(null));
        wire.setRationale(fit.rationale().orElse(null));
        return wire;
    }

    private static RoutingCatalogCost cost(RoutingCatalogView.CostView cost) {
        RoutingCatalogCost wire = new RoutingCatalogCost(RoutingCatalogCost.TierEnum.fromValue(cost.tier()));
        wire.setInputPer1mUsd(
                cost.inputPer1mUsd().map(DecimalJson::wire).orElse(null));
        wire.setOutputPer1mUsd(
                cost.outputPer1mUsd().map(DecimalJson::wire).orElse(null));
        wire.setCurrency(cost.currency().orElse(null));
        wire.setPricingSourceUrl(cost.pricingSourceUrl().orElse(null));
        return wire;
    }

    private static RoutingCatalogPolicy policy(RoutingCatalogView.PolicyView policy) {
        return new RoutingCatalogPolicy(
                RoutingCatalogPolicy.ObjectiveEnum.fromValue(policy.objective()),
                new RoutingCatalogWeights(
                        DecimalJson.wire(policy.quality()),
                        DecimalJson.wire(policy.cost()),
                        DecimalJson.wire(policy.risk()),
                        DecimalJson.wire(policy.latency())),
                policy.tierConstraints().stream()
                        .map(constraint -> new RoutingCatalogTierConstraint(
                                constraint.taskType(),
                                RoutingCatalogTierConstraint.MaxTierEnum.fromValue(constraint.maxTier())))
                        .toList());
    }

    private static RoutingCatalogSource source(RoutingCatalogView.SourceView source) {
        RoutingCatalogSource wire = new RoutingCatalogSource(
                source.name(),
                source.url(),
                RoutingCatalogSource.AccessMethodEnum.fromValue(source.accessMethod()),
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
