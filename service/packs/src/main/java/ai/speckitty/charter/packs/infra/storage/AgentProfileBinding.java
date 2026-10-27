package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.ArtifactReference;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.ModeDefault;
import ai.speckitty.charter.profiles.domain.SelfReviewStep;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.util.List;

/**
 * The {@link KindBinding} of the agent profile: {@link AgentProfileDso} to {@link AgentProfile} and back. The entity
 * belongs to the profiles slice; the pack store binds it here, depending inward on that slice's domain (ADR-0037
 * decision 6). Its files are {@code *.agent.yaml}, validated by the {@code agent-profile} schema, with the id under
 * {@code profile-id}.
 */
enum AgentProfileBinding implements KindBinding<AgentProfileDso, AgentProfile> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.AGENT_PROFILE;
    }

    @Override
    public Class<AgentProfile> entityType() {
        return AgentProfile.class;
    }

    @Override
    public String fileToken() {
        return "agent";
    }

    @Override
    public String schemaName() {
        return "agent-profile";
    }

    @Override
    public String idKey() {
        return "profile-id";
    }

    @Override
    public Class<AgentProfileDso> dsoType() {
        return AgentProfileDso.class;
    }

    @Override
    public String idOf(AgentProfileDso dso) {
        return dso.profileId();
    }

    @Override
    public AgentProfile toEntity(PackIdentifier pack, AgentProfileDso dso) {
        AgentProfileDso.Specialization specialization = dso.specialization();
        AgentProfileDso.Collaboration collaboration = dso.collaboration();
        AgentProfileDso.SpecializationContext context = dso.specializationContext();
        AgentProfile.Builder builder = AgentProfile.builder()
                .id(new AgentProfileIdentifier(dso.profileId()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .name(dso.name())
                .description(dso.description())
                .avatarImage(dso.avatarImage())
                .routingPriority(dso.routingPriority().map(value -> asInt("routing-priority", value)))
                .maxConcurrentTasks(dso.maxConcurrentTasks().map(value -> asInt("max-concurrent-tasks", value)))
                .model(dso.model())
                .effort(dso.effort())
                .purpose(dso.purpose())
                .specialization(new Specialization(
                        specialization.primaryFocus(),
                        specialization.secondaryAwareness(),
                        specialization.avoidanceBoundary(),
                        specialization.successDefinition()))
                .collaboration(new Collaboration(
                        collaboration.handoffTo(),
                        collaboration.handoffFrom(),
                        collaboration.worksWith(),
                        collaboration.outputArtifacts(),
                        collaboration.operatingProcedures(),
                        collaboration.canonicalVerbs()))
                .initializationDeclaration(dso.initializationDeclaration())
                .sentinel(dso.sentinel())
                .specializationContext(new SpecializationContext(
                        context.languages(),
                        context.frameworks(),
                        context.filePatterns(),
                        context.domainKeywords(),
                        context.writingStyle(),
                        context.complexityPreference()))
                .excluding(dso.excluding().map(AgentProfileBinding::exclusion));
        dso.roles().forEach(builder::role);
        dso.capabilities().forEach(builder::capability);
        dso.modeDefaults()
                .forEach(mode -> builder.modeDefault(new ModeDefault(mode.mode(), mode.description(), mode.useCase())));
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        dso.directiveReferences()
                .forEach(ref ->
                        builder.directiveReference(new DirectiveReference(ref.code(), ref.name(), ref.rationale())));
        dso.tacticReferences()
                .forEach(ref -> builder.tacticReference(new ArtifactReference(ref.id(), ref.rationale())));
        dso.toolguideReferences()
                .forEach(ref -> builder.toolguideReference(new ArtifactReference(ref.id(), ref.rationale())));
        dso.styleguideReferences()
                .forEach(ref -> builder.styleguideReference(new ArtifactReference(ref.id(), ref.rationale())));
        dso.selfReviewSteps()
                .forEach(step -> builder.selfReviewStep(new SelfReviewStep(step.name(), step.command(), step.gate())));
        return builder.build();
    }

    @Override
    public AgentProfileDso toDso(AgentProfile profile) {
        Specialization specialization = profile.specialization();
        Collaboration collaboration = profile.collaboration();
        SpecializationContext context = profile.specializationContext();
        return new AgentProfileDso(
                profile.id().value(),
                profile.name(),
                profile.description(),
                profile.schemaVersion(),
                profile.roles(),
                profile.avatarImage(),
                profile.capabilities(),
                profile.routingPriority().map(Integer::longValue),
                profile.maxConcurrentTasks().map(Integer::longValue),
                profile.model(),
                profile.effort(),
                profile.purpose(),
                new AgentProfileDso.Specialization(
                        specialization.primaryFocus(),
                        specialization.secondaryAwareness(),
                        specialization.avoidanceBoundary(),
                        specialization.successDefinition()),
                new AgentProfileDso.Collaboration(
                        collaboration.handoffTo(),
                        collaboration.handoffFrom(),
                        collaboration.worksWith(),
                        collaboration.outputArtifacts(),
                        collaboration.operatingProcedures(),
                        collaboration.canonicalVerbs()),
                profile.modeDefaults().stream()
                        .map(mode -> new AgentProfileDso.ModeDefault(mode.mode(), mode.description(), mode.useCase()))
                        .toList(),
                profile.initializationDeclaration(),
                profile.sentinel(),
                new AgentProfileDso.SpecializationContext(
                        context.languages(),
                        context.frameworks(),
                        context.filePatterns(),
                        context.domainKeywords(),
                        context.writingStyle(),
                        context.complexityPreference()),
                profile.appliesToLanguages(),
                profile.directiveReferences().stream()
                        .map(ref -> new AgentProfileDso.DirectiveRef(ref.code(), ref.name(), ref.rationale()))
                        .toList(),
                artifactRefs(profile.tacticReferences()),
                artifactRefs(profile.toolguideReferences()),
                artifactRefs(profile.styleguideReferences()),
                profile.selfReviewSteps().stream()
                        .map(step -> new AgentProfileDso.SelfReviewStep(step.name(), step.command(), step.gate()))
                        .toList(),
                profile.excluding().map(AgentProfileBinding::excluding));
    }

    /** An integer field as the domain holds it; a value beyond {@code int} has no domain counterpart. */
    private static int asInt(String field, long value) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException tooLarge) {
            throw new IllegalArgumentException("the " + field + " " + value + " is out of range", tooLarge);
        }
    }

    private static List<AgentProfileDso.ArtifactRef> artifactRefs(List<ArtifactReference> refs) {
        return refs.stream()
                .map(ref -> new AgentProfileDso.ArtifactRef(ref.id(), ref.rationale()))
                .toList();
    }

    private static Exclusion exclusion(AgentProfileDso.Excluding excluding) {
        return switch (excluding) {
            case AgentProfileDso.Excluding.Fields fields -> new Exclusion.WholeFields(fields.fields());
            case AgentProfileDso.Excluding.Values values -> new Exclusion.FieldValues(values.values());
        };
    }

    private static AgentProfileDso.Excluding excluding(Exclusion exclusion) {
        return switch (exclusion) {
            case Exclusion.WholeFields fields -> new AgentProfileDso.Excluding.Fields(fields.fields());
            case Exclusion.FieldValues values -> new AgentProfileDso.Excluding.Values(values.values());
        };
    }
}
