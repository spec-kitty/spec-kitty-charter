package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.ArtifactSummary;
import ai.speckitty.charter.api.model.Directive;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.MissionStepContract;
import ai.speckitty.charter.api.model.MissionStepContractGate;
import ai.speckitty.charter.api.model.MissionStepContractInput;
import ai.speckitty.charter.api.model.MissionStepContractStep;
import ai.speckitty.charter.api.model.MissionStepDelegation;
import ai.speckitty.charter.api.model.MissionStepDelegationCandidate;
import ai.speckitty.charter.api.model.Paradigm;
import ai.speckitty.charter.api.model.Styleguide;
import ai.speckitty.charter.api.model.StyleguideAntiPattern;
import ai.speckitty.charter.api.model.StyleguidePattern;
import ai.speckitty.charter.api.model.StyleguideToolEntry;
import ai.speckitty.charter.api.model.Tactic;
import ai.speckitty.charter.api.model.TacticStep;
import ai.speckitty.charter.api.model.Toolguide;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.ConfigValue;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ArtifactPosition;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.GuideFormat;
import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The domain-to-contract mappers of the served kinds, one place for the registration controllers and the generic
 * {@link ArtifactController}: each kind's representation, the {@code Artifact} envelope that holds exactly the member its
 * kind names, with the links of the item, and a list's {@code ArtifactSummary}. A storage DSO is never exposed (C-002).
 */
final class ArtifactDtos {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String KIND = "kind";
    private static final String MISSION_TYPE = "mission_type";
    private static final String STEPS = "steps";

    /**
     * The relations of a step contract's links to the missions slice's reads, keyed as strings, as the activations
     * slice keys its own: they are rows of the hypermedia guide's table and of {@code link-relations-are-known}, and
     * {@link Relation} stays the relations this slice links its own reads with.
     */
    private static final String MISSION_TYPE_RELATION = "mission-type";

    private static final String STEP_RELATION = "step";

    private ArtifactDtos() {}

    /**
     * The envelope of one artifact: its kind, id and pack, its links, and the one member its kind names.
     *
     * @param item the artifact read, typed by its kind
     * @param guides the guide format a toolguide's guide is written in
     * @param links the envelope's links
     * @param hypermedia the link builder, for the links inside a member (a step contract's delegation candidates)
     * @return the envelope; the other kind members are {@code null}, and left out of the JSON
     */
    static Artifact artifact(
            ArtifactReadService.Item item, GuideFormat guides, Map<String, Link> links, Hypermedia hypermedia) {
        CharterArtifact artifact = item.artifact();
        Artifact envelope = new Artifact(
                artifact.kind().token(), artifact.localId(), artifact.packId().value(), links);
        return switch (item) {
            case ArtifactReadService.Item.OfTactic tactic -> envelope.tactic(tactic(tactic.artifact()));
            case ArtifactReadService.Item.OfDirective directive -> envelope.directive(directive(directive.artifact()));
            case ArtifactReadService.Item.OfStyleguide styleguide ->
                envelope.styleguide(styleguide(styleguide.artifact()));
            case ArtifactReadService.Item.OfParadigm paradigm -> envelope.paradigm(paradigm(paradigm.artifact()));
            case ArtifactReadService.Item.OfToolguide toolguide ->
                envelope.toolguide(toolguide(toolguide.artifact(), guides));
            case ArtifactReadService.Item.OfMissionStepContract contract ->
                envelope.missionStepContract(stepContract(contract, hypermedia));
        };
    }

    /**
     * The envelope of one artifact before its kind's member is set: its kind, id and pack, and the links of its item,
     * {@code self} and {@code collection} (the list of its kind). A write answers it with the member it wrote, so its
     * links are the item read's (there is no {@code edit} link, R-24).
     *
     * @param kind the artifact's kind
     * @param pack the pack that holds it
     * @param id its identity within the kind and pack
     * @param links the link builder
     * @return the envelope, with no member set
     */
    static Artifact envelope(ArtifactKind kind, PackIdentifier pack, String id, Hypermedia links) {
        return new Artifact(kind.token(), id, pack.value(), itemLinks(kind.token(), pack.value(), id, links));
    }

    /**
     * The links of one artifact's item: {@code self}, its path, and {@code collection}, the list of its kind.
     *
     * @param kind the kind's token, as the path names it
     * @param packId the pack's identity, as the path names it
     * @param id the artifact's identity
     * @param links the link builder
     * @return the wire links
     */
    static Map<String, Link> itemLinks(String kind, String packId, String id, Hypermedia links) {
        return Hypermedia.wire(itemRelations(kind, packId, id, links));
    }

    /**
     * The links of one artifact's item as the generic item read answers it: {@code self} and {@code collection}, and,
     * for a mission step contract, {@code mission-type} when its pack holds the mission type its {@code mission} names
     * and {@code step} when that type declares the step its {@code action} names.
     *
     * @param item the artifact read
     * @param kind the kind's token, as the path names it
     * @param packId the pack's identity, as the path names it
     * @param id the artifact's identity
     * @param links the link builder
     * @return the wire links
     */
    static Map<String, Link> itemLinks(
            ArtifactReadService.Item item, String kind, String packId, String id, Hypermedia links) {
        Map<String, Link> wire = Hypermedia.wire(itemRelations(kind, packId, id, links));
        if (item instanceof ArtifactReadService.Item.OfMissionStepContract contract) {
            String mission = contract.artifact().mission();
            if (contract.missionTypeHeld()) {
                wire.put(MISSION_TYPE_RELATION, wireLink(links.link(PACKS, packId, ARTIFACTS, MISSION_TYPE, mission)));
            }
            if (contract.stepHeld()) {
                wire.put(
                        STEP_RELATION,
                        wireLink(links.link(
                                PACKS,
                                packId,
                                ARTIFACTS,
                                MISSION_TYPE,
                                mission,
                                STEPS,
                                contract.artifact().action())));
            }
        }
        return wire;
    }

    private static Link wireLink(Hypermedia.LinkValue value) {
        return new Link(value.href());
    }

    private static Map<Relation, Hypermedia.LinkValue> itemRelations(
            String kind, String packId, String id, Hypermedia links) {
        Map<Relation, Hypermedia.LinkValue> item = new EnumMap<>(Relation.class);
        item.put(Relation.SELF, links.link(PACKS, packId, ARTIFACTS, kind, id));
        item.put(Relation.COLLECTION, links.link(Map.of(KIND, kind), PACKS, packId, ARTIFACTS));
        return item;
    }

    /**
     * Where a created artifact is: the current request's scheme and authority with the envelope's {@code self} path,
     * so the path of {@code Location} is the {@code _links.self.href} of the answer.
     *
     * @param envelope the created artifact's envelope
     * @return the absolute URI of the created artifact
     */
    static URI location(Artifact envelope) {
        return ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath(Objects.requireNonNull(envelope.getLinks().get(Relation.SELF.wireKey()), "self")
                        .getHref())
                .replaceQuery(null)
                .build(true)
                .toUri();
    }

    /**
     * One artifact as a list names it.
     *
     * @param summary the summary the list read
     * @param links its links
     * @return the contract's summary
     */
    static ArtifactSummary summary(ArtifactReadService.Summary summary, Map<String, Link> links) {
        return new ArtifactSummary(
                summary.position().kind().token(), summary.position().id(), summary.title(), links);
    }

    // ---- tactic -------------------------------------------------------------------------------------------------

    static Tactic tactic(ai.speckitty.charter.packs.domain.Tactic tactic) {
        return new Tactic(
                tactic.id().value(),
                tactic.schemaVersion().orElseThrow(),
                tactic.name(),
                tactic.purpose().orElse(null),
                tactic.notes().orElse(null),
                tactic.steps().stream().map(ArtifactDtos::tacticStep).toList(),
                tactic.failureModes(),
                tactic.appliesToLanguages());
    }

    private static TacticStep tacticStep(ai.speckitty.charter.packs.domain.TacticStep step) {
        return new TacticStep(step.title(), step.description().orElse(null), step.examples());
    }

    // ---- directive ----------------------------------------------------------------------------------------------

    static Directive directive(ai.speckitty.charter.packs.domain.Directive directive) {
        return new Directive(
                directive.id().value(),
                directive.schemaVersion().orElseThrow(),
                directive.title(),
                directive.intent(),
                directive.enforcement().token(),
                directive.scope().orElse(null),
                directive.integrityRules(),
                directive.validationCriteria(),
                directive.explicitAllowances());
    }

    // ---- styleguide ---------------------------------------------------------------------------------------------

    static Styleguide styleguide(ai.speckitty.charter.packs.domain.Styleguide styleguide) {
        return new Styleguide(
                styleguide.id().value(),
                styleguide.schemaVersion().orElseThrow(),
                styleguide.title(),
                styleguide.scope().token(),
                styleguide.principles(),
                styleguide.patterns().stream()
                        .map(ArtifactDtos::styleguidePattern)
                        .toList(),
                styleguide.antiPatterns().stream()
                        .map(ArtifactDtos::styleguideAntiPattern)
                        .toList(),
                styleguideTooling(styleguide.tooling()),
                styleguide.qualityTest().orElse(null),
                styleguide
                        .structuralLintConfig()
                        .map(ArtifactDtos::styleguideConfig)
                        .orElse(null),
                styleguide.appliesToLanguages());
    }

    private static StyleguidePattern styleguidePattern(ai.speckitty.charter.packs.domain.StyleguidePattern pattern) {
        return new StyleguidePattern(
                pattern.name(),
                pattern.description(),
                pattern.badExample().orElse(null),
                pattern.goodExample().orElse(null));
    }

    private static StyleguideAntiPattern styleguideAntiPattern(
            ai.speckitty.charter.packs.domain.StyleguideAntiPattern antiPattern) {
        return new StyleguideAntiPattern(
                antiPattern.name(), antiPattern.description(), antiPattern.badExample(), antiPattern.goodExample());
    }

    private static List<StyleguideToolEntry> styleguideTooling(Optional<Map<String, String>> tooling) {
        return tooling.map(map -> map.entrySet().stream()
                        .map(entry -> new StyleguideToolEntry(entry.getKey(), entry.getValue()))
                        .toList())
                .orElse(List.of());
    }

    private static String styleguideConfig(ConfigValue config) {
        return JSON.writeValueAsString(config.toParsed());
    }

    // ---- paradigm -----------------------------------------------------------------------------------------------

    static Paradigm paradigm(ai.speckitty.charter.packs.domain.Paradigm paradigm) {
        return new Paradigm(
                paradigm.id().value(),
                paradigm.schemaVersion().orElseThrow(),
                paradigm.name(),
                paradigm.summary(),
                paradigm.principles().stream()
                        .map(item -> new ai.speckitty.charter.api.model.ParadigmPrinciple(
                                item.name(), item.description()))
                        .toList());
    }

    // ---- mission step contract ----------------------------------------------------------------------------------

    /**
     * A mission step contract as the wire names it: its steps and gates in stored order, a list the file leaves out
     * answered empty, and each delegation candidate with an {@code artifact} link to its pack-scoped read when its kind
     * is listed and its pack holds it ({@link ArtifactReadService.Item.OfMissionStepContract#heldCandidates()}).
     */
    static MissionStepContract stepContract(ArtifactReadService.Item.OfMissionStepContract item, Hypermedia links) {
        ai.speckitty.charter.packs.domain.MissionStepContract contract = item.artifact();
        String packId = contract.packId().value();
        return new MissionStepContract(
                contract.id().value(),
                contract.schemaVersion().orElseThrow(),
                contract.action(),
                contract.mission(),
                contract.steps().stream()
                        .map(step -> contractStep(step, item.heldCandidates(), packId, links))
                        .toList(),
                contract.gates().stream().map(ArtifactDtos::gate).toList());
    }

    private static MissionStepContractStep contractStep(
            ai.speckitty.charter.packs.domain.ContractStep step,
            Set<ArtifactPosition> held,
            String packId,
            Hypermedia links) {
        return new MissionStepContractStep(
                        step.id(),
                        step.description(),
                        step.inputs().stream()
                                .map(input -> new MissionStepContractInput(input.flag(), input.source())
                                        .optional(input.optional().orElse(null)))
                                .toList())
                .command(step.command().orElse(null))
                .delegatesTo(step.delegatesTo()
                        .map(delegation -> delegation(delegation, held, packId, links))
                        .orElse(null))
                .guidance(step.guidance().orElse(null));
    }

    private static MissionStepDelegation delegation(
            ai.speckitty.charter.packs.domain.StepDelegation delegation,
            Set<ArtifactPosition> held,
            String packId,
            Hypermedia links) {
        String kind = delegation.kind().token();
        return new MissionStepDelegation(
                kind,
                delegation.candidates().stream()
                        .map(candidate -> {
                            Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
                            // A position names a listed kind only, so the held set is searched, not probed.
                            if (held.stream()
                                    .anyMatch(position -> position.kind() == delegation.kind()
                                            && position.id().equals(candidate))) {
                                own.put(Relation.ARTIFACT, links.link(PACKS, packId, ARTIFACTS, kind, candidate));
                            }
                            return new MissionStepDelegationCandidate(candidate, Hypermedia.wire(own));
                        })
                        .toList());
    }

    private static MissionStepContractGate gate(ai.speckitty.charter.packs.domain.GateBinding gate) {
        return new MissionStepContractGate(gate.onTransition(), gate.handler(), gate.schemaVersion())
                .handlerKind(gate.handlerKind().orElse(null))
                .failOpen(gate.failOpen().orElse(null))
                .provenance(gate.provenance().orElse(null));
    }

    // ---- toolguide ----------------------------------------------------------------------------------------------

    static Toolguide toolguide(ai.speckitty.charter.packs.domain.Toolguide toolguide, GuideFormat guides) {
        return new Toolguide(
                toolguide.id().value(),
                toolguide.schemaVersion().orElseThrow(),
                toolguide.tool(),
                toolguide.title(),
                guides.write(toolguide.title(), toolguide.guide()),
                toolguide.summary(),
                toolguide.commands(),
                toolguide.appliesToLanguages(),
                toolguide.lastUpdated().orElse(null));
    }
}
