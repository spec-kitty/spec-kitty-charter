package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.EffectiveReadService.Page;
import ai.speckitty.charter.activations.application.EffectiveReadService.PageQuery;
import ai.speckitty.charter.activations.application.EffectiveReadService.Report;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.ActivationSummary;
import ai.speckitty.charter.api.model.DenyNotApplied;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.EffectiveArtifactSuggestion;
import ai.speckitty.charter.api.model.EnhancerWithoutTarget;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.NodeReference;
import ai.speckitty.charter.api.model.OverriddenArtifact;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.PageInfo;
import ai.speckitty.charter.api.model.RequiredNotInForce;
import ai.speckitty.charter.api.model.SkippedReport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 */
@Component
public class EffectiveReadMapper {

    private final ActivationMapper activations;

    /**
     * A mapper that names the activation through the document mapper's paths.
     *
     * @param activations the document mapper, whose {@code self} path is the activation's
     */
    @Autowired
    public EffectiveReadMapper(ActivationMapper activations) {
        this.activations = Objects.requireNonNull(activations, "activations");
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
                page.items().stream().map(EffectiveReadMapper::item).toList(),
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

    private static ActivationSummary summary(EffectiveSet.Summary counts) {
        return new ActivationSummary(
                counts.active(),
                counts.inactive(),
                counts.excluded(),
                counts.required(),
                counts.locked(),
                counts.skipped());
    }

    private static ai.speckitty.charter.api.model.EffectiveArtifact item(EffectiveArtifact artifact) {
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
        artifact.satisfiedBy().map(ActivationChangeMapper::reference).ifPresent(wire::setSatisfiedBy);
        artifact.suggestion().ifPresent(suggestion -> {
            EffectiveArtifactSuggestion carrier = new EffectiveArtifactSuggestion();
            suggestion.when().ifPresent(carrier::setWhen);
            suggestion.reason().ifPresent(carrier::setReason);
            wire.setSuggestion(carrier);
        });
        return wire;
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

    private static OverriddenArtifact overridden(ArtifactRef ref, ArtifactRef by) {
        return new OverriddenArtifact(
                OverriddenArtifact.KindEnum.fromValue(ref.kind().token()),
                ref.id(),
                ActivationChangeMapper.reference(by));
    }
}
