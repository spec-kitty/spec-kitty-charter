package ai.speckitty.charter.missions.infra.api;

import ai.speckitty.charter.api.MissionTypesApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.MissionSkeleton;
import ai.speckitty.charter.api.model.MissionStep;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.MissionTypeReadService;
import ai.speckitty.charter.missions.application.MissionTypeView;
import ai.speckitty.charter.missions.application.PackRead;
import ai.speckitty.charter.missions.application.SkeletonView;
import ai.speckitty.charter.missions.application.StepView;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /packs/{packId}/artifacts/mission_type/{missionTypeId}} and its {@code steps/{stepId}} and
 * {@code skeletons/{skeletonName}} sub-resources (#99): the pack-scoped mission-type reads of
 * {@link MissionTypeReadService}. The literal paths are more specific than the generic
 * {@code /packs/{packId}/artifacts/{kind}/{artifactId}}, so Spring routes a {@code mission_type} item here. A refusal
 * the service throws is answered by the shared {@code RefusalHandler}; a path parameter that breaks the generated
 * interface's bounds is a 400 without a code. Each 200 carries the source fingerprint its read verified and, as its
 * {@code ETag}, the pack revision read inside the same verified read; a refusal carries no {@code ETag}.
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
        PackRead<MissionTypeView> read = reads.missionType(new PackIdentifier(packId), missionTypeId);
        MissionLinks.Scope scope = links.pack(packId);
        return MissionBodies.ok(read)
                .body(MissionBodies.missionType(
                        read.value(), packId, scope, MissionBodies.envelopeLinks(scope, missionTypeId)));
    }

    @Override
    public ResponseEntity<MissionStep> getPackMissionStep(String packId, String missionTypeId, String stepId) {
        PackRead<StepView> read = reads.step(new PackIdentifier(packId), missionTypeId, stepId);
        return MissionBodies.ok(read).body(MissionBodies.step(read.value(), links.pack(packId)));
    }

    @Override
    public ResponseEntity<MissionSkeleton> getPackMissionSkeleton(
            String packId, String missionTypeId, String skeletonName) {
        PackRead<SkeletonView> read = reads.skeleton(new PackIdentifier(packId), missionTypeId, skeletonName);
        return MissionBodies.ok(read).body(MissionBodies.skeleton(read.value(), links.pack(packId)));
    }
}
