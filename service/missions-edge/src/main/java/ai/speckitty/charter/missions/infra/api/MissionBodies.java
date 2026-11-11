package ai.speckitty.charter.missions.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.MissionExpectedArtifact;
import ai.speckitty.charter.api.model.MissionExpectedInput;
import ai.speckitty.charter.api.model.MissionPathConventions;
import ai.speckitty.charter.api.model.MissionSkeleton;
import ai.speckitty.charter.api.model.MissionSkeletonSummary;
import ai.speckitty.charter.api.model.MissionStep;
import ai.speckitty.charter.api.model.MissionStepContract;
import ai.speckitty.charter.api.model.MissionStepContractGate;
import ai.speckitty.charter.api.model.MissionStepContractInput;
import ai.speckitty.charter.api.model.MissionStepContractStep;
import ai.speckitty.charter.api.model.MissionStepDelegation;
import ai.speckitty.charter.api.model.MissionStepDelegationCandidate;
import ai.speckitty.charter.api.model.MissionStepSummary;
import ai.speckitty.charter.api.model.MissionType;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.missions.application.EffectiveStepContractView;
import ai.speckitty.charter.missions.application.Fingerprinted;
import ai.speckitty.charter.missions.application.HeldArtifact;
import ai.speckitty.charter.missions.application.MissionTypeView;
import ai.speckitty.charter.missions.application.PackRead;
import ai.speckitty.charter.missions.application.SkeletonView;
import ai.speckitty.charter.missions.application.StepContractView;
import ai.speckitty.charter.missions.application.StepView;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.PathConventions;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * The wire bodies of the mission-type reads, the same for a pack and for an activation (#99): a mission type in the
 * Artifact envelope, a step and a skeleton, each linked within its {@link MissionLinks.Scope}; and a step contract under
 * an activation in the Artifact envelope (D11). A controller adds the
 * links only its scope has (an activation's {@code artifact}) to the envelope's own.
 */
final class MissionBodies {

    private MissionBodies() {}

    /**
     * The envelope's own links: the mission type and the scope's artifacts of its kind.
     *
     * @param scope the scope of the read
     * @param missionTypeId the mission type's id
     * @return {@code self} and {@code collection}, which a controller may add to
     */
    static EnumMap<MissionRelation, Link> envelopeLinks(MissionLinks.Scope scope, String missionTypeId) {
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, scope.missionType(missionTypeId));
        own.put(MissionRelation.COLLECTION, scope.missionTypes());
        return own;
    }

    /**
     * A mission type in the Artifact envelope, its steps and skeletons linked within the scope.
     *
     * @param view the mission type's view
     * @param packId the pack the envelope names: the pack read, or the pack that holds the winner
     * @param scope the scope of the read
     * @param own the envelope's links
     * @return the envelope
     */
    static Artifact missionType(
            MissionTypeView view, String packId, MissionLinks.Scope scope, EnumMap<MissionRelation, Link> own) {
        ai.speckitty.charter.missions.domain.MissionType type = view.type();
        String missionTypeId = type.localId();
        MissionType wire = new MissionType(
                        missionTypeId,
                        type.schemaVersion().orElseThrow(),
                        type.title(),
                        type.description().orElseThrow(),
                        type.requiresSubstantiveSpec(),
                        type.expectedOutputArtifacts().stream()
                                .map(MissionBodies::output)
                                .toList(),
                        view.steps().stream()
                                .map(step -> new MissionStepSummary(
                                        step.id(),
                                        step.title(),
                                        step.onDemand(),
                                        self(Optional.of(scope.step(missionTypeId, step.id())))))
                                .toList(),
                        view.skeletons().stream()
                                .map(skeleton -> new MissionSkeletonSummary(
                                        skeleton.name(),
                                        skeleton.path(),
                                        self(scope.servedSkeleton(missionTypeId, skeleton.name()))))
                                .toList())
                .content(type.content().orElse(null))
                .pathConventions(
                        type.pathConventions().map(MissionBodies::conventions).orElse(null));
        return new Artifact(ArtifactKind.MISSION_TYPE.token(), missionTypeId, packId, MissionLinks.wire(own))
                .missionType(wire);
    }

    /**
     * A step, linked to its mission type, its step contract and the skeleton of each output that names one.
     *
     * @param view the step's view
     * @param scope the scope of the read
     * @return the step
     */
    static MissionStep step(StepView view, MissionLinks.Scope scope) {
        ai.speckitty.charter.missions.domain.MissionStep step = view.step();
        String missionTypeId = view.missionTypeId();
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, scope.step(missionTypeId, step.id()));
        own.put(MissionRelation.MISSION_TYPE, scope.missionType(missionTypeId));
        view.stepContractId()
                .ifPresent(contract -> own.put(MissionRelation.STEP_CONTRACT, scope.stepContract(contract)));
        return new MissionStep(
                        step.id(),
                        step.title(),
                        view.onDemand(),
                        step.expectedInputArtifacts().stream()
                                .map(input -> new MissionExpectedInput(
                                        input.artifactKey(), input.pathPattern(), input.blocking()))
                                .toList(),
                        view.outputs().stream()
                                .map(output -> {
                                    MissionExpectedArtifact artifact = output(output.artifact());
                                    output.skeletonName()
                                            .flatMap(name -> scope.servedSkeleton(missionTypeId, name))
                                            .ifPresent(link ->
                                                    artifact.putLinksItem(MissionRelation.SKELETON.wireKey(), link));
                                    return artifact;
                                })
                                .toList(),
                        MissionLinks.wire(own))
                .description(step.description().orElse(null))
                .role(step.role().orElse(null))
                .prompt(view.prompt().orElse(null))
                .guidelines(view.guidelines().orElse(null));
    }

    /**
     * A skeleton, linked to its mission type.
     *
     * @param view the skeleton's view
     * @param scope the scope of the read
     * @return the skeleton
     */
    static MissionSkeleton skeleton(SkeletonView view, MissionLinks.Scope scope) {
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, scope.skeleton(view.missionTypeId(), view.name()));
        own.put(MissionRelation.MISSION_TYPE, scope.missionType(view.missionTypeId()));
        return new MissionSkeleton(
                view.name(),
                view.path(),
                view.missionTypeId(),
                view.encoding() == SkeletonView.Encoding.TEXT
                        ? MissionSkeleton.EncodingEnum.TEXT
                        : MissionSkeleton.EncodingEnum.BASE64,
                view.content(),
                view.sha256(),
                view.size(),
                MissionLinks.wire(own));
    }

    /**
     * A step contract under an activation in the Artifact envelope (D11): its stored fields, and each delegation
     * candidate the closure holds linked {@code effective-artifact}, its read under the activation.
     *
     * @param view the step contract's view
     * @param packId the pack that holds the winner
     * @param scope the activation's scope
     * @param own the envelope's links
     * @return the envelope
     */
    static Artifact stepContract(
            EffectiveStepContractView view,
            String packId,
            MissionLinks.Scope scope,
            EnumMap<MissionRelation, Link> own) {
        StepContractView contract = view.contract();
        MissionStepContract wire = new MissionStepContract(
                contract.id(),
                contract.schemaVersion(),
                contract.action(),
                contract.mission(),
                contract.steps().stream()
                        .map(step -> contractStep(step, view, scope))
                        .toList(),
                contract.gates().stream().map(MissionBodies::gate).toList());
        return new Artifact(ArtifactKind.MISSION_STEP_CONTRACT.token(), contract.id(), packId, MissionLinks.wire(own))
                .missionStepContract(wire);
    }

    private static MissionStepContractStep contractStep(
            StepContractView.Step step, EffectiveStepContractView view, MissionLinks.Scope scope) {
        return new MissionStepContractStep(
                        step.id(),
                        step.description(),
                        step.inputs().stream()
                                .map(input -> new MissionStepContractInput(input.flag(), input.source())
                                        .optional(input.optional().orElse(null)))
                                .toList())
                .command(step.command().orElse(null))
                .delegatesTo(step.delegatesTo()
                        .map(delegation -> new MissionStepDelegation(
                                delegation.kind().token(),
                                delegation.candidates().stream()
                                        .map(candidate -> candidate(delegation.kind(), candidate, view, scope))
                                        .toList()))
                        .orElse(null))
                .guidance(step.guidance().orElse(null));
    }

    private static MissionStepDelegationCandidate candidate(
            ArtifactKind kind, String candidate, EffectiveStepContractView view, MissionLinks.Scope scope) {
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        if (view.heldCandidates().contains(new HeldArtifact(kind, candidate))) {
            own.put(MissionRelation.EFFECTIVE_ARTIFACT, scope.artifact(kind.token(), candidate));
        }
        return new MissionStepDelegationCandidate(candidate, MissionLinks.wire(own));
    }

    private static MissionStepContractGate gate(StepContractView.Gate gate) {
        return new MissionStepContractGate(gate.onTransition(), gate.handler(), gate.schemaVersion())
                .handlerKind(gate.handlerKind().orElse(null))
                .failOpen(gate.failOpen().orElse(null))
                .provenance(gate.provenance().orElse(null));
    }

    /**
     * A pack-scoped 200: the one fingerprint its read verified, and the pack revision it was read at as its
     * {@code ETag}.
     *
     * @param read the read
     * @return the response builder
     */
    static ResponseEntity.BodyBuilder ok(PackRead<?> read) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HeaderNames.SOURCE_FINGERPRINT, read.fingerprint());
        return ResponseEntity.ok().headers(headers).eTag(EntityTags.quote(read.revision()));
    }

    /**
     * A 200 that carries the one fingerprint its read verified; the caller sets its own tag.
     *
     * @param read the read
     * @return the response builder
     */
    static ResponseEntity.BodyBuilder ok(Fingerprinted<?> read) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HeaderNames.SOURCE_FINGERPRINT, read.fingerprint());
        return ResponseEntity.ok().headers(headers);
    }

    private static Map<String, Link> self(Optional<Link> link) {
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        link.ifPresent(self -> own.put(MissionRelation.SELF, self));
        return MissionLinks.wire(own);
    }

    /** An expected output as stored; its links are the step's to add. */
    private static MissionExpectedArtifact output(ExpectedArtifact artifact) {
        return new MissionExpectedArtifact(
                        artifact.artifactKey(),
                        artifact.artifactClass().token(),
                        artifact.pathPattern(),
                        artifact.blocking())
                .templatePath(artifact.templatePath().orElse(null));
    }

    private static MissionPathConventions conventions(PathConventions conventions) {
        return new MissionPathConventions()
                .workspace(conventions.workspace().orElse(null))
                .tests(conventions.tests().orElse(null))
                .deliverables(conventions.deliverables().orElse(null))
                .documentation(conventions.documentation().orElse(null))
                .data(conventions.data().orElse(null));
    }
}
