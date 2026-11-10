package ai.speckitty.charter.missions.infra.api;

import ai.speckitty.charter.api.EffectiveMissionTypesApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.MissionSkeleton;
import ai.speckitty.charter.api.model.MissionStep;
import ai.speckitty.charter.missions.application.EffectiveMissionTypeReadService;
import ai.speckitty.charter.missions.application.EffectiveStepContractView;
import ai.speckitty.charter.missions.application.Fingerprinted;
import ai.speckitty.charter.missions.application.MissionTypeView;
import ai.speckitty.charter.missions.application.SkeletonView;
import ai.speckitty.charter.missions.application.StepContractView;
import ai.speckitty.charter.missions.application.StepView;
import java.util.EnumMap;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}}, its {@code steps/{stepId}} and
 * {@code skeletons/{skeletonName}} sub-resources (#99, FR-007), and
 * {@code GET /activations/{activationId}/artifacts/mission_step_contract/{contractId}} (D11): the reads under an
 * activation of {@link EffectiveMissionTypeReadService}. It is a controller of its own, of its own tag
 * ({@code EffectiveMissionTypes}), beside {@link MissionTypeController}, as {@code EffectiveAgentProfilesController} is
 * for a profile. The literal paths are more specific than the activation set's templated read of an artifact, which
 * carries no body, so Spring routes a {@code mission_type} or {@code mission_step_contract} item here.
 *
 * <p>The bodies are the pack-scoped reads' ({@link MissionBodies}), linked under the activation; the envelope's
 * {@code packId} is the pack that holds the winner, and its {@code artifact} link is that pack's read of it. A refusal
 * the service throws is answered by the shared refusal handler. Each 200 carries the source fingerprint its read
 * verified and no ETag.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveMissionTypesController implements EffectiveMissionTypesApi {

    private final EffectiveMissionTypeReadService reads;
    private final MissionLinks links;

    /**
     * A controller over the mission-type reads under an activation.
     *
     * @param reads the mission-type reads under an activation
     * @param links the link builder
     */
    EffectiveMissionTypesController(EffectiveMissionTypeReadService reads, MissionLinks links) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.links = Objects.requireNonNull(links, "links");
    }

    @Override
    public ResponseEntity<Artifact> getEffectiveMissionType(String activationId, String missionTypeId) {
        EffectiveMissionTypeReadService.Held<MissionTypeView> held = reads.missionType(activationId, missionTypeId);
        String holder = held.holder().value();
        MissionLinks.Scope scope = links.activation(activationId);
        EnumMap<MissionRelation, Link> own = MissionBodies.envelopeLinks(scope, missionTypeId);
        own.put(MissionRelation.ARTIFACT, links.missionType(holder, missionTypeId));
        return MissionBodies.ok(held.read())
                .body(MissionBodies.missionType(held.read().value(), holder, scope, own));
    }

    @Override
    public ResponseEntity<Artifact> getEffectiveMissionStepContract(String activationId, String contractId) {
        EffectiveMissionTypeReadService.Held<EffectiveStepContractView> held =
                reads.stepContract(activationId, contractId);
        EffectiveStepContractView view = held.read().value();
        StepContractView contract = view.contract();
        String holder = held.holder().value();
        MissionLinks.Scope scope = links.activation(activationId);
        EnumMap<MissionRelation, Link> own = new EnumMap<>(MissionRelation.class);
        own.put(MissionRelation.SELF, scope.stepContract(contract.id()));
        own.put(MissionRelation.COLLECTION, scope.stepContracts());
        if (view.missionTypeHeld()) {
            own.put(MissionRelation.MISSION_TYPE, scope.missionType(contract.mission()));
        }
        if (view.stepHeld()) {
            own.put(MissionRelation.STEP, scope.step(contract.mission(), contract.action()));
        }
        own.put(MissionRelation.ARTIFACT, links.pack(holder).stepContract(contract.id()));
        return MissionBodies.ok(held.read()).body(MissionBodies.stepContract(view, holder, scope, own));
    }

    @Override
    public ResponseEntity<MissionStep> getEffectiveMissionStep(
            String activationId, String missionTypeId, String stepId) {
        Fingerprinted<StepView> read = reads.step(activationId, missionTypeId, stepId);
        return MissionBodies.ok(read).body(MissionBodies.step(read.value(), links.activation(activationId)));
    }

    @Override
    public ResponseEntity<MissionSkeleton> getEffectiveMissionSkeleton(
            String activationId, String missionTypeId, String skeletonName) {
        Fingerprinted<SkeletonView> read = reads.skeleton(activationId, missionTypeId, skeletonName);
        return MissionBodies.ok(read).body(MissionBodies.skeleton(read.value(), links.activation(activationId)));
    }
}
