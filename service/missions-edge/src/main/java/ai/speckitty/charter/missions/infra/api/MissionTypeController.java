package ai.speckitty.charter.missions.infra.api;

import ai.speckitty.charter.api.MissionTypesApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.MissionExpectedArtifact;
import ai.speckitty.charter.api.model.MissionExpectedInput;
import ai.speckitty.charter.api.model.MissionPathConventions;
import ai.speckitty.charter.api.model.MissionSkeleton;
import ai.speckitty.charter.api.model.MissionSkeletonSummary;
import ai.speckitty.charter.api.model.MissionStep;
import ai.speckitty.charter.api.model.MissionStepSummary;
import ai.speckitty.charter.api.model.MissionType;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.missions.application.Fingerprinted;
import ai.speckitty.charter.missions.application.MissionTypeReadService;
import ai.speckitty.charter.missions.application.MissionTypeView;
import ai.speckitty.charter.missions.application.SkeletonView;
import ai.speckitty.charter.missions.application.StepView;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.PathConventions;
import java.util.EnumMap;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /packs/{packId}/artifacts/mission_type/{missionTypeId}} and its {@code steps/{stepId}} and
 * {@code skeletons/{skeletonName}} sub-resources (#99): the pack-scoped mission-type reads of
 * {@link MissionTypeReadService}. The literal paths are more specific than the generic
 * {@code /packs/{packId}/artifacts/{kind}/{artifactId}}, so Spring routes a {@code mission_type} item here. A refusal
 * the service throws is answered by the shared {@code RefusalHandler}; a path parameter that breaks the generated
 * interface's bounds is a 400 without a code. Each 200 carries the source fingerprint its read verified and no ETag:
 * neither a mission type nor its parts has a write here.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class MissionTypeController implements MissionTypesApi {

    private final MissionTypeReadService reads;
    private final MissionLinks links;

    /**
     * A controller over the mission-type reads.
     *
     * @param reads the mission-type reads
     * @param links the link builder
     */
    MissionTypeController(MissionTypeReadService reads, MissionLinks links) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.links = Objects.requireNonNull(links, "links");
    }

    @Override
    public ResponseEntity<Artifact> getPackMissionType(String packId, String missionTypeId) {
        Fingerprinted<MissionTypeView> read = reads.missionType(new PackIdentifier(packId), missionTypeId);
        MissionTypeView view = read.value();
        ai.speckitty.charter.missions.domain.MissionType type = view.type();
        MissionType wire = new MissionType(
                        type.localId(),
                        type.schemaVersion().orElseThrow(),
                        type.title(),
                        type.description().orElseThrow(),
                        type.requiresSubstantiveSpec(),
                        type.expectedOutputArtifacts().stream()
                                .map(MissionTypeController::output)
                                .toList(),
                        view.steps().stream()
                                .map(step -> new MissionStepSummary(
                                        step.id(),
                                        step.title(),
                                        step.onDemand(),
                                        self(links.step(packId, missionTypeId, step.id()))))
                                .toList(),
                        view.skeletons().stream()
                                .map(skeleton -> new MissionSkeletonSummary(
                                        skeleton.name(),
                                        skeleton.path(),
                                        self(links.skeleton(packId, missionTypeId, skeleton.name()))))
                                .toList())
                .content(type.content().orElse(null))
                .pathConventions(type.pathConventions()
                        .map(MissionTypeController::conventions)
                        .orElse(null));
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, links.missionType(packId, missionTypeId));
        own.put(MissionRelation.COLLECTION, links.missionTypes(packId));
        return ok(read).body(
                        new Artifact(ArtifactKind.MISSION_TYPE.token(), type.localId(), packId, MissionLinks.wire(own))
                                .missionType(wire));
    }

    @Override
    public ResponseEntity<MissionStep> getPackMissionStep(String packId, String missionTypeId, String stepId) {
        Fingerprinted<StepView> read = reads.step(new PackIdentifier(packId), missionTypeId, stepId);
        StepView view = read.value();
        ai.speckitty.charter.missions.domain.MissionStep step = view.step();
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, links.step(packId, missionTypeId, step.id()));
        own.put(MissionRelation.MISSION_TYPE, links.missionType(packId, missionTypeId));
        view.stepContractId()
                .ifPresent(contract -> own.put(MissionRelation.STEP_CONTRACT, links.stepContract(packId, contract)));
        MissionStep wire = new MissionStep(
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
                                            .ifPresent(name -> artifact.putLinksItem(
                                                    MissionRelation.SKELETON.wireKey(),
                                                    links.skeleton(packId, missionTypeId, name)));
                                    return artifact;
                                })
                                .toList(),
                        MissionLinks.wire(own))
                .description(step.description().orElse(null))
                .role(step.role().orElse(null))
                .prompt(view.prompt().orElse(null))
                .guidelines(view.guidelines().orElse(null));
        return ok(read).body(wire);
    }

    @Override
    public ResponseEntity<MissionSkeleton> getPackMissionSkeleton(
            String packId, String missionTypeId, String skeletonName) {
        Fingerprinted<SkeletonView> read = reads.skeleton(new PackIdentifier(packId), missionTypeId, skeletonName);
        SkeletonView view = read.value();
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, links.skeleton(packId, missionTypeId, view.name()));
        own.put(MissionRelation.MISSION_TYPE, links.missionType(packId, missionTypeId));
        return ok(read).body(new MissionSkeleton(
                view.name(),
                view.path(),
                view.missionTypeId(),
                view.encoding() == SkeletonView.Encoding.TEXT
                        ? MissionSkeleton.EncodingEnum.TEXT
                        : MissionSkeleton.EncodingEnum.BASE64,
                view.content(),
                view.sha256(),
                view.size(),
                MissionLinks.wire(own)));
    }

    /** A 200 that carries the one fingerprint its read verified. */
    private static ResponseEntity.BodyBuilder ok(Fingerprinted<?> read) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HeaderNames.SOURCE_FINGERPRINT, read.fingerprint());
        return ResponseEntity.ok().headers(headers);
    }

    private static java.util.Map<String, Link> self(Link link) {
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, link);
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
