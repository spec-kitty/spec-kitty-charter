package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.model.AgentProfile;
import ai.speckitty.charter.api.model.AgentProfileArtifactReference;
import ai.speckitty.charter.api.model.AgentProfileCollaboration;
import ai.speckitty.charter.api.model.AgentProfileDirectiveReference;
import ai.speckitty.charter.api.model.AgentProfileExcluding;
import ai.speckitty.charter.api.model.AgentProfileFieldValues;
import ai.speckitty.charter.api.model.AgentProfileModeDefault;
import ai.speckitty.charter.api.model.AgentProfileSelfReviewProtocol;
import ai.speckitty.charter.api.model.AgentProfileSelfReviewStep;
import ai.speckitty.charter.api.model.AgentProfileSpecialization;
import ai.speckitty.charter.api.model.AgentProfileSpecializationContext;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.GovernanceEdgeWords;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.PackGovernanceEntry;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.profiles.application.EdgeWords;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.application.ProfileRead;
import ai.speckitty.charter.profiles.domain.ArtifactReference;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.ModeDefault;
import ai.speckitty.charter.profiles.domain.SelfReviewStep;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of the pack-scoped profile read: the {@code Artifact} envelope with the stored profile in
 * {@code agentProfile}, one member per field of {@code contracts/packs/schemas/agent-profile.schema.json} (the oracle of
 * the mapping, fold C10), and the governance entries with their links. It is the profiles edge's own: it builds its
 * links the way the activation mapper does and names neither the packs slice's link builder nor its DTO mapper.
 *
 * <p>A field the stored profile does not tell apart is one value on the wire, recorded here and in the contract: a list
 * the profile does not declare is {@code []}; a specialization context whose six lists are all empty, and a
 * self-review protocol with no steps, are {@code null}, as the absent ones are (fold P5). A field the pack schema
 * defaults is left out when the profile does not declare it, and a field it allows to be null is {@code null}.
 */
@Component
public class ProfileDtos {

    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String REFERENCES = "references";
    private static final String DEPTH = "depth";

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public ProfileDtos(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The envelope of a profile read.
     *
     * @param read what the application ring read
     * @param depth the depth the request named; empty when it named none, so that {@code self} is the request that was
     *     made
     * @return the {@code Artifact} with the {@code agentProfile} member and the {@code governance} list
     */
    public Artifact artifact(ProfileRead read, OptionalInt depth) {
        ai.speckitty.charter.profiles.domain.AgentProfile profile = read.profile();
        String pack = profile.packId().value();
        String id = profile.localId();
        Artifact envelope =
                new Artifact(ArtifactKind.AGENT_PROFILE.token(), id, pack, selfAndNeighbours(pack, id, depth));
        envelope.setAgentProfile(profile(profile));
        envelope.setGovernance(
                read.governance().stream().map(entry -> entry(entry, read)).toList());
        return envelope;
    }

    private Map<String, Link> selfAndNeighbours(String pack, String id, OptionalInt depth) {
        String self = href(PACKS, pack, ARTIFACTS, ArtifactKind.AGENT_PROFILE.token(), id);
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(depth.isPresent() ? self + "?" + DEPTH + "=" + depth.getAsInt() : self));
        links.put(
                "collection",
                new Link(href(PACKS, pack, ARTIFACTS) + "?kind="
                        + UriUtils.encodeQueryParam(ArtifactKind.AGENT_PROFILE.token(), StandardCharsets.UTF_8)));
        links.put("references", new Link(self + "/" + REFERENCES));
        return links;
    }

    private PackGovernanceEntry entry(GovernanceEntry entry, ProfileRead read) {
        Map<String, Link> links = new LinkedHashMap<>();
        if (read.linksArtifact(entry)) {
            links.put(
                    "artifact",
                    new Link(href(
                            PACKS,
                            entry.holder().orElseThrow().value(),
                            ARTIFACTS,
                            entry.urn().kind().token(),
                            entry.urn().id())));
        }
        PackGovernanceEntry wire = new PackGovernanceEntry(
                PackGovernanceEntry.KindEnum.fromValue(entry.urn().kind().token()),
                entry.urn().id(),
                entry.urn().toString(),
                PackGovernanceEntry.StepEnum.fromValue(entry.step().token()),
                PackGovernanceEntry.RelationEnum.fromValue(entry.relation().token()),
                entry.via().toString(),
                entry.hops(),
                edge(entry.edge()),
                entry.holder().map(holder -> holder.value()).orElse(null),
                entry.held(),
                links);
        return wire;
    }

    /**
     * The words of an edge as the contract's {@code GovernanceEdgeWords}: only the words the edge states, so an edge that
     * states neither is an empty object, never null.
     *
     * @param words the words of the edge
     * @return the closed schema
     */
    static GovernanceEdgeWords edge(EdgeWords words) {
        GovernanceEdgeWords carrier = new GovernanceEdgeWords();
        words.when().ifPresent(carrier::setWhen);
        words.reason().ifPresent(carrier::setReason);
        return carrier;
    }

    /**
     * The stored profile as the contract's closed {@code AgentProfile}.
     *
     * @param profile the stored profile
     * @return one member per field of the pack schema
     */
    static AgentProfile profile(ai.speckitty.charter.profiles.domain.AgentProfile profile) {
        AgentProfile wire = new AgentProfile(
                profile.localId(),
                profile.name(),
                profile.roles(),
                profile.avatarImage().orElse(null),
                profile.capabilities(),
                profile.model().orElse(null),
                profile.effort().orElse(null),
                profile.purpose(),
                specialization(profile.specialization()),
                collaboration(profile.collaboration()),
                profile.modeDefaults().stream().map(ProfileDtos::modeDefault).toList(),
                specializationContext(profile.specializationContext()),
                profile.appliesToLanguages(),
                profile.directiveReferences().stream()
                        .map(ProfileDtos::directiveReference)
                        .toList(),
                artifactReferences(profile.tacticReferences()),
                artifactReferences(profile.toolguideReferences()),
                artifactReferences(profile.styleguideReferences()),
                selfReviewProtocol(profile.selfReviewSteps()),
                profile.excluding().map(ProfileDtos::excluding).orElse(null));
        profile.description().ifPresent(wire::setDescription);
        profile.schemaVersion().ifPresent(wire::setSchemaVersion);
        profile.routingPriority().ifPresent(wire::setRoutingPriority);
        profile.maxConcurrentTasks().ifPresent(wire::setMaxConcurrentTasks);
        profile.sentinel().ifPresent(wire::setSentinel);
        profile.initializationDeclaration().ifPresent(wire::setInitializationDeclaration);
        return wire;
    }

    private static AgentProfileSpecialization specialization(Specialization specialization) {
        AgentProfileSpecialization wire = new AgentProfileSpecialization(specialization.primaryFocus());
        specialization.secondaryAwareness().ifPresent(wire::setSecondaryAwareness);
        specialization.avoidanceBoundary().ifPresent(wire::setAvoidanceBoundary);
        specialization.successDefinition().ifPresent(wire::setSuccessDefinition);
        return wire;
    }

    private static AgentProfileCollaboration collaboration(Collaboration collaboration) {
        return new AgentProfileCollaboration(
                collaboration.handoffTo(),
                collaboration.handoffFrom(),
                collaboration.worksWith(),
                collaboration.outputArtifacts(),
                collaboration.operatingProcedures(),
                collaboration.canonicalVerbs());
    }

    private static AgentProfileModeDefault modeDefault(ModeDefault mode) {
        return new AgentProfileModeDefault(mode.mode(), mode.description(), mode.useCase());
    }

    /** The context, or {@code null} when it has nothing to say: an absent one and one of six empty lists are one. */
    private static @Nullable AgentProfileSpecializationContext specializationContext(SpecializationContext context) {
        if (context.equals(SpecializationContext.NONE)) {
            return null;
        }
        return new AgentProfileSpecializationContext(
                context.languages(),
                context.frameworks(),
                context.filePatterns(),
                context.domainKeywords(),
                context.writingStyle(),
                context.complexityPreference());
    }

    private static AgentProfileDirectiveReference directiveReference(DirectiveReference reference) {
        return new AgentProfileDirectiveReference(reference.code(), reference.name(), reference.rationale());
    }

    private static List<AgentProfileArtifactReference> artifactReferences(List<ArtifactReference> references) {
        return references.stream()
                .map(reference -> new AgentProfileArtifactReference(reference.id(), reference.rationale()))
                .toList();
    }

    /** The protocol, or {@code null} when it has no steps: an absent protocol and one with no steps are one. */
    private static @Nullable AgentProfileSelfReviewProtocol selfReviewProtocol(List<SelfReviewStep> steps) {
        if (steps.isEmpty()) {
            return null;
        }
        return new AgentProfileSelfReviewProtocol(steps.stream()
                .map(step -> new AgentProfileSelfReviewStep(
                        step.name(), step.command().orElse(null), step.gate()))
                .toList());
    }

    /** Exactly one of the two authored forms is set, the other is {@code null}, in the order the file wrote them. */
    private static AgentProfileExcluding excluding(Exclusion exclusion) {
        return switch (exclusion) {
            case Exclusion.WholeFields whole -> new AgentProfileExcluding(whole.fields(), null);
            case Exclusion.FieldValues values ->
                new AgentProfileExcluding(
                        null,
                        values.values().entrySet().stream()
                                .map(entry -> new AgentProfileFieldValues(entry.getKey(), entry.getValue()))
                                .toList());
        };
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
