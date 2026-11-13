package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.EffectiveReadService.ContextRead;
import ai.speckitty.charter.activations.application.EffectiveReadService.GraphRead;
import ai.speckitty.charter.activations.application.EffectiveReadService.Page;
import ai.speckitty.charter.activations.application.EffectiveReadService.PageQuery;
import ai.speckitty.charter.activations.application.EffectiveReadService.PromptRead;
import ai.speckitty.charter.activations.application.EffectiveReadService.Report;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveContext;
import ai.speckitty.charter.activations.domain.EffectivePrompt;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.api.model.ActionEnhancement;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.ActivationSummary;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.DenyNotApplied;
import ai.speckitty.charter.api.model.Diagnostic;
import ai.speckitty.charter.api.model.EffectiveActionContext;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.EffectiveArtifactSuggestion;
import ai.speckitty.charter.api.model.EffectiveContextArtifact;
import ai.speckitty.charter.api.model.EffectiveGraph;
import ai.speckitty.charter.api.model.EffectiveGraphEdge;
import ai.speckitty.charter.api.model.EffectiveGraphNode;
import ai.speckitty.charter.api.model.EffectivePromptFragment;
import ai.speckitty.charter.api.model.EffectivePromptSlot;
import ai.speckitty.charter.api.model.EffectiveStepPrompt;
import ai.speckitty.charter.api.model.EnhancerWithoutTarget;
import ai.speckitty.charter.api.model.LayerOverride;
import ai.speckitty.charter.api.model.LayerOverrideSide;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.NodeReference;
import ai.speckitty.charter.api.model.OverriddenArtifact;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.PageInfo;
import ai.speckitty.charter.api.model.RequiredNotInForce;
import ai.speckitty.charter.api.model.SkippedReport;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The wire form of the effective reads (IC-09): the page of effective artifacts with its summary, its paging state and
 * its {@code self}, {@code first} and {@code next} links, and the skipped report with its ten read buckets (the buckets
 * of a write's cascade stay empty at read). The page's links keep the filters and the page size of the request, as the
 * request wrote them, so {@code self} is the request that was made and a
 * followed {@code next} reads the next page of the same set; the cursor it carries is the position of the page's last
 * item and the tag of its set ({@link EffectiveCursor}).
 *
 * <p>An item of a kind the pack-scoped artifact routes serve ({@link EffectiveReadService#listedKinds()}, asked at each
 * read) links {@code effective-artifact}, its own read under the activation, and, where the item names the pack that holds
 * the version in force, {@code artifact}, the pack-scoped read of that pack. A graph node names no pack, so it links
 * {@code effective-artifact} only, and a node of a kind that is not served links nothing.
 *
 * <p>While mission types are served, a step links the read of the step under the activation
 * ({@code getEffectiveMissionStep}) as its {@code effective-artifact}: an action node of the graph
 * ({@code action:<type>/<step>}), which names no pack either, and the action context, whose step is the action it
 * answers; the context also links {@code artifact}, the step's read in the pack of the step's place
 * ({@code getPackMissionStep}): the winner of the mission type when its version declares the step, else the merged
 * winner of the action whose version does (research R8, A7), when the effective set lists the mission type and a place
 * is found. A step is linked when the closure holds it, as the graph and the context judge it, and the step read answers
 * it from the same place.
 */
@Component
public class EffectiveReadMapper {

    private final ActivationMapper activations;
    private final Supplier<List<ArtifactKind>> linkedKinds;

    /**
     * A mapper that names the activation through the document mapper's paths and links the items of the kinds the
     * service lists.
     *
     * @param activations the document mapper, whose {@code self} path is the activation's
     * @param effective the effective reads, which know the kinds the pack-scoped artifact routes serve
     */
    @Autowired
    public EffectiveReadMapper(ActivationMapper activations, EffectiveReadService effective) {
        this(activations, Objects.requireNonNull(effective, "effective")::listedKinds);
    }

    /**
     * A mapper over any answer of the served kinds, so that a test can name them.
     *
     * @param activations the document mapper, whose {@code self} path is the activation's
     * @param linkedKinds the kinds an item can be linked by, asked at each read
     */
    EffectiveReadMapper(ActivationMapper activations, Supplier<List<ArtifactKind>> linkedKinds) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.linkedKinds = Objects.requireNonNull(linkedKinds, "linkedKinds");
    }

    /**
     * A page of the effective artifacts.
     *
     * @param page the page the service read
     * @param pageSizeGiven whether the request named a {@code pageSize}: the links repeat it only then, so that
     *     {@code self} is the request that was made
     * @return its wire form
     */
    public EffectiveArtifactList toList(Page page, boolean pageSizeGiven) {
        String id = page.stored().id().value();
        Optional<String> next = page.next().map(after -> EffectiveCursor.encode(after.position(), after.setTag()));
        PageInfo info =
                new PageInfo(next.isPresent(), next.orElse(null), page.query().pageSize());
        return new EffectiveArtifactList(
                id,
                page.stored().revision().token(),
                page.governanceFingerprint(),
                summary(page.summary()),
                items(id, page.items()),
                info,
                pageLinks(id, page.query(), pageSizeGiven, next));
    }

    /**
     * The skipped report.
     *
     * @param report what the service read
     * @return its wire form
     */
    public ActivationSkippedReport toSkipped(Report report) {
        String id = report.stored().id().value();
        return new ActivationSkippedReport(
                id,
                report.stored().revision().token(),
                report.governanceFingerprint(),
                skipped(report.skipped()),
                Map.of("self", new Link(activations.self(id) + "/skipped")));
    }

    /**
     * The effective graph.
     *
     * @param read what the service read
     * @return its wire form
     */
    public EffectiveGraph toGraph(GraphRead read) {
        String id = read.stored().id().value();
        GraphSnapshot graph = read.graph();
        List<ArtifactKind> served = linkedKinds.get();
        return new EffectiveGraph(
                id,
                read.stored().revision().token(),
                read.governanceFingerprint(),
                graph.nodes().stream().map(node -> graphNode(id, node, served)).toList(),
                graph.edges().stream()
                        .map(edge -> new EffectiveGraphEdge(
                                edge.source().urn(),
                                EffectiveGraphEdge.RelationEnum.fromValue(
                                        edge.relation().token()),
                                edge.target().urn()))
                        .toList(),
                graph.layerOverrides().stream()
                        .map(replaced -> new LayerOverride(
                                replaced.ref().urn(), side(replaced.winner()), side(replaced.loser())))
                        .toList(),
                Map.of("self", new Link(activations.self(id) + "/graph")));
    }

    /**
     * The effective action context.
     *
     * @param read what the service read
     * @param depthGiven whether the request named a {@code depth}: {@code self} repeats it only then, so that it is the
     *     request that was made
     * @return its wire form
     */
    public EffectiveActionContext toContext(ContextRead read, boolean depthGiven) {
        String id = read.stored().id().value();
        EffectiveContext context = read.context();
        List<ArtifactKind> served = linkedKinds.get();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", contextLink(id, read.query(), depthGiven));
        if (served.contains(ArtifactKind.MISSION_TYPE)) {
            String type = read.query().missionType();
            String step = read.query().action();
            links.put("effective-artifact", new Link(activations.effectiveMissionStep(id, type, step)));
            read.stepPack()
                    .ifPresent(pack -> links.put("artifact", new Link(activations.packMissionStep(pack, type, step))));
        }
        if (read.missionTypeInForce()) {
            links.put(
                    "prompt",
                    new Link(activations.prompt(
                            id, read.query().missionType(), read.query().action())));
        }
        return new EffectiveActionContext(
                id,
                read.stored().revision().token(),
                read.governanceFingerprint(),
                read.query().missionType(),
                read.query().action(),
                context.artifacts().stream()
                        .map(item -> contextItem(id, item, served))
                        .toList(),
                context.enhancements().stream()
                        .map(enhancement -> new ActionEnhancement(
                                node(enhancement.target()),
                                ActivationChangeMapper.references(enhancement.enhancedBy())))
                        .toList(),
                skipped(context.skipped()),
                links);
    }

    /**
     * The effective prompt of a step: the prompt as stored, its slots with their fragments in the order the read gave
     * them, its diagnostics, and its links: {@code self}, {@code effective-context} (the action context of the same
     * action) and, while mission types are served, {@code effective-artifact} (the step under the activation). An
     * absent {@code promptPackId} stays absent.
     *
     * @param read what the service read
     * @return its wire form
     */
    public EffectiveStepPrompt toPrompt(PromptRead read) {
        String id = read.stored().id().value();
        String type = read.query().missionType();
        String step = read.query().action();
        EffectivePrompt prompt = read.prompt();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(activations.prompt(id, type, step)));
        if (linkedKinds.get().contains(ArtifactKind.MISSION_TYPE)) {
            links.put("effective-artifact", new Link(activations.effectiveMissionStep(id, type, step)));
        }
        links.put("effective-context", new Link(activations.effectiveContext(id, type, step)));
        EffectiveStepPrompt wire = new EffectiveStepPrompt(
                id,
                read.stored().revision().token(),
                read.governanceFingerprint(),
                type,
                step,
                prompt.prompt(),
                prompt.slots().stream().map(EffectiveReadMapper::slot).toList(),
                prompt.diagnostics().stream()
                        .map(diagnostic -> new Diagnostic(
                                Diagnostic.CodeEnum.fromValue(diagnostic.code().token()), diagnostic.detail()))
                        .toList(),
                links);
        prompt.promptPackId().ifPresent(wire::setPromptPackId);
        return wire;
    }

    private static EffectivePromptSlot slot(EffectivePrompt.Slot slot) {
        return new EffectivePromptSlot(
                slot.slot(),
                slot.fragments().stream()
                        .map(fragment -> new EffectivePromptFragment(
                                fragment.packId(),
                                fragment.packName(),
                                EffectivePromptFragment.LevelEnum.fromValue(
                                        fragment.level().token()),
                                fragment.enhancement(),
                                fragment.text()))
                        .toList());
    }

    /** The context's own request: its path, and the depth when the request named one. */
    private Link contextLink(String id, EffectiveTag.ContextQuery query, boolean depthGiven) {
        UriComponentsBuilder href = UriComponentsBuilder.fromPath(
                activations.self(id) + "/mission-types/{missionType}" + "/actions/{action}/context");
        if (depthGiven) {
            href.queryParam("depth", query.depth());
        }
        return new Link(href.buildAndExpand(query.missionType(), query.action())
                .encode()
                .toUriString());
    }

    private static LayerOverrideSide side(MergedSnapshot.LayerOverride.Side side) {
        return new LayerOverrideSide(
                side.packId(),
                LayerOverrideSide.LevelEnum.fromValue(side.level().token()));
    }

    private static ActivationSummary summary(EffectiveSet.Summary counts) {
        return new ActivationSummary(
                counts.active(),
                counts.inactive(),
                counts.excluded(),
                counts.required(),
                counts.locked(),
                counts.skipped());
    }

    private List<ai.speckitty.charter.api.model.EffectiveArtifact> items(String id, List<EffectiveArtifact> artifacts) {
        return items(artifacts, id, linkedKinds.get());
    }

    private List<ai.speckitty.charter.api.model.EffectiveArtifact> items(
            List<EffectiveArtifact> artifacts, String id, List<ArtifactKind> served) {
        return artifacts.stream().map(artifact -> item(id, artifact, served)).toList();
    }

    /**
     * A node of the graph, with its effective read when it is an artifact of a served kind, or the read of its step
     * under the activation when it is a step ({@code action:<type>/<step>}) and mission types are served.
     */
    private EffectiveGraphNode graphNode(String id, NodeRef node, List<ArtifactKind> served) {
        EffectiveGraphNode wire = new EffectiveGraphNode(
                node.urn(), EffectiveGraphNode.KindEnum.fromValue(node.kind().token()));
        node.kind()
                .artifactKind()
                .filter(served::contains)
                .ifPresent(kind -> wire.setLinks(Map.of(
                        "effective-artifact", new Link(activations.effectiveArtifact(id, kind.token(), node.id())))));
        int slash = node.id().indexOf('/');
        if (node.kind() == NodeKind.ACTION && slash > 0 && served.contains(ArtifactKind.MISSION_TYPE)) {
            wire.setLinks(Map.of(
                    "effective-artifact",
                    new Link(activations.effectiveMissionStep(
                            id, node.id().substring(0, slash), node.id().substring(slash + 1)))));
        }
        return wire;
    }

    private ai.speckitty.charter.api.model.EffectiveArtifact item(
            String id, EffectiveArtifact artifact, List<ArtifactKind> served) {
        ai.speckitty.charter.api.model.EffectiveArtifact wire = new ai.speckitty.charter.api.model.EffectiveArtifact(
                ai.speckitty.charter.api.model.EffectiveArtifact.KindEnum.fromValue(
                        artifact.kind().token()),
                artifact.artifactId(),
                artifact.packId(),
                ai.speckitty.charter.api.model.EffectiveArtifact.LevelEnum.fromValue(
                        artifact.level().token()),
                ai.speckitty.charter.api.model.EffectiveArtifact.StateEnum.fromValue(
                        artifact.state().token()),
                artifact.required(),
                ai.speckitty.charter.api.model.EffectiveArtifact.PolicyEnum.fromValue(
                        artifact.policy().token()),
                ai.speckitty.charter.api.model.EffectiveArtifact.ReasonEnum.fromValue(
                        artifact.reason().token()));
        references(artifact.satisfiedBy()).ifPresent(wire::setSatisfiedBy);
        suggestion(artifact).ifPresent(wire::setSuggestion);
        itemLinks(id, artifact, served).ifPresent(wire::setLinks);
        return wire;
    }

    /**
     * The overriders of a {@code satisfiedBy} as the wire spells them, in the order the resolution gives; absent when
     * there is none, since the member is absent rather than empty ({@code minItems: 1}).
     *
     * @param overriders the artifacts in force that satisfy a requirement
     * @return their references; empty when there is none
     */
    static Optional<List<ArtifactReference>> references(List<ArtifactRef> overriders) {
        return overriders.isEmpty() ? Optional.empty() : Optional.of(ActivationChangeMapper.references(overriders));
    }

    /**
     * An item of the action context: the members of the effective list's item, copied (the contract's
     * {@code EffectiveContextArtifact} copies {@code EffectiveArtifact}'s), and the part of the ownership rule that lists
     * it.
     */
    private EffectiveContextArtifact contextItem(String id, EffectiveContext.Item item, List<ArtifactKind> served) {
        EffectiveArtifact artifact = item.artifact();
        EffectiveContextArtifact wire = new EffectiveContextArtifact(
                EffectiveContextArtifact.KindEnum.fromValue(artifact.kind().token()),
                artifact.artifactId(),
                artifact.packId(),
                EffectiveContextArtifact.LevelEnum.fromValue(artifact.level().token()),
                EffectiveContextArtifact.StateEnum.fromValue(artifact.state().token()),
                artifact.required(),
                EffectiveContextArtifact.PolicyEnum.fromValue(artifact.policy().token()),
                EffectiveContextArtifact.ReasonEnum.fromValue(artifact.reason().token()),
                EffectiveContextArtifact.AppliesThroughEnum.fromValue(
                        item.appliesThrough().token()));
        references(artifact.satisfiedBy()).ifPresent(wire::setSatisfiedBy);
        suggestion(artifact).ifPresent(wire::setSuggestion);
        itemLinks(id, artifact, served).ifPresent(wire::setLinks);
        return wire;
    }

    private static Optional<EffectiveArtifactSuggestion> suggestion(EffectiveArtifact artifact) {
        return artifact.suggestion().map(suggestion -> {
            EffectiveArtifactSuggestion carrier = new EffectiveArtifactSuggestion();
            suggestion.when().ifPresent(carrier::setWhen);
            suggestion.reason().ifPresent(carrier::setReason);
            return carrier;
        });
    }

    /** The links of an item: its effective read and its pack-scoped read, for a kind the pack-scoped routes serve. */
    private Optional<Map<String, Link>> itemLinks(String id, EffectiveArtifact artifact, List<ArtifactKind> served) {
        if (!served.contains(artifact.kind())) {
            return Optional.empty();
        }
        String kind = artifact.kind().token();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("effective-artifact", new Link(activations.effectiveArtifact(id, kind, artifact.artifactId())));
        links.put("artifact", new Link(activations.packArtifact(artifact.packId(), kind, artifact.artifactId())));
        return Optional.of(links);
    }

    private Map<String, Link> pageLinks(String id, PageQuery query, boolean pageSizeGiven, Optional<String> next) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", link(id, query, pageSizeGiven, query.after().map(EffectiveReadService.After::cursor)));
        links.put("first", link(id, query, pageSizeGiven, Optional.empty()));
        next.ifPresent(cursor -> links.put("next", link(id, query, pageSizeGiven, Optional.of(cursor))));
        return links;
    }

    /** The page's own request, with the filters and, when the request named one, the page size, and the given cursor. */
    private Link link(String id, PageQuery query, boolean pageSizeGiven, Optional<String> cursor) {
        UriComponentsBuilder href = UriComponentsBuilder.fromPath(activations.self(id) + "/artifacts");
        query.kind().ifPresent(kind -> href.queryParam("kind", kind.token()));
        query.state().ifPresent(state -> href.queryParam("state", state.token()));
        if (pageSizeGiven) {
            href.queryParam("pageSize", query.pageSize());
        }
        cursor.ifPresent(value -> href.queryParam("pageCursor", value));
        return new Link(href.build().encode().toUriString());
    }

    /** The report in the contract's shape: the write's four lists as a write maps them, then the ten read buckets. */
    private static SkippedReport skipped(EffectiveSkippedReport report) {
        SkippedReport wire = ActivationChangeMapper.skipped(report.base());
        wire.setStaleIds(ActivationChangeMapper.references(report.staleIds()));
        wire.setDenyNotApplied(report.denyNotApplied().stream()
                .map(entry -> new DenyNotApplied(
                        DenyNotApplied.KindEnum.fromValue(entry.ref().kind().token()),
                        entry.ref().id(),
                        DenyNotApplied.ReasonEnum.fromValue(entry.reason().token())))
                .toList());
        wire.setLanguageFiltered(ActivationChangeMapper.references(report.languageFiltered()));
        wire.setOverridden(report.overridden().stream()
                .map(entry -> overridden(entry.ref(), entry.overriddenBy()))
                .toList());
        wire.setMissionTypeNotInForce(ActivationChangeMapper.references(report.missionTypeNotInForce()));
        wire.setOverrideNotApplied(report.overrideNotApplied().stream()
                .map(entry -> overridden(entry.ref(), entry.overriddenBy()))
                .toList());
        wire.setEnhancedTargetNotInForce(report.enhancedTargetNotInForce().stream()
                .map(entry -> new EnhancerWithoutTarget(
                        EnhancerWithoutTarget.KindEnum.fromValue(
                                entry.ref().kind().token()),
                        entry.ref().id(),
                        entry.targets().stream().map(EffectiveReadMapper::node).toList()))
                .toList());
        wire.setPinNotHeld(report.pinNotHeld().stream()
                .map(pin -> new PackPin(pin.packId()).version(pin.version()))
                .toList());
        wire.setRequiredNotHeld(ActivationChangeMapper.references(report.requiredNotHeld()));
        wire.setRequiredNotInForce(report.requiredNotInForce().stream()
                .map(entry -> new RequiredNotInForce(
                        RequiredNotInForce.KindEnum.fromValue(entry.ref().kind().token()),
                        entry.ref().id(),
                        RequiredNotInForce.ReasonEnum.fromValue(entry.reason().token())))
                .toList());
        return wire;
    }

    /** A node of the enhancer's targets as the wire spells it: a kind of NodeKind and an id. */
    private static NodeReference node(NodeRef ref) {
        return new NodeReference(NodeReference.KindEnum.fromValue(ref.kind().token()), ref.id());
    }

    private static OverriddenArtifact overridden(ArtifactRef ref, List<ArtifactRef> by) {
        return new OverriddenArtifact(
                OverriddenArtifact.KindEnum.fromValue(ref.kind().token()),
                ref.id(),
                ActivationChangeMapper.references(by));
    }
}
