package ai.speckitty.charter.missions.infra.activations;

import ai.speckitty.charter.activations.application.EffectiveMissionTypeWinner;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.EffectiveStepContractWinner;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.ActivationMissionTypes;
import ai.speckitty.charter.missions.application.EffectiveMissionType;
import ai.speckitty.charter.missions.application.EffectiveStepContract;
import ai.speckitty.charter.missions.application.EffectiveStepPlace;
import ai.speckitty.charter.missions.application.HeldArtifact;
import ai.speckitty.charter.missions.application.MissionTypeNarrowing;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The one bean of {@link ActivationMissionTypes}: it asks the activations slice's application ring
 * ({@link EffectiveReadService#missionTypeWinner}, {@link EffectiveReadService#stepContractWinner},
 * {@link EffectiveReadService#stepPlace}) and turns its answers into the missions slice's own. A winning type is narrowed to the missions domain's
 * {@link ai.speckitty.charter.missions.domain.MissionType}; a step contract stays a kernel artifact, which only the
 * packs adapter may narrow. It decides nothing: the refusals and their order are the activations slice's, unchanged.
 */
@Component
public final class ActivationsMissionTypes implements ActivationMissionTypes {

    private final EffectiveReadService effective;

    /**
     * An adapter over the activations slice's effective reads.
     *
     * @param effective the reads of what an activation puts in force
     */
    @Autowired
    public ActivationsMissionTypes(EffectiveReadService effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    @Override
    public EffectiveMissionType missionType(String activationId, String missionTypeId) {
        EffectiveMissionTypeWinner winner = effective.missionTypeWinner(new ActivationId(activationId), missionTypeId);
        return new EffectiveMissionType(
                new PackIdentifier(winner.packId()),
                MissionTypeNarrowing.narrowed(
                        winner.missionType(),
                        "The read of the mission type " + missionTypeId + " under activation " + activationId),
                winner.stepContracts(),
                winner.sourceFingerprint());
    }

    @Override
    public EffectiveStepContract stepContract(String activationId, String stepContractId) {
        EffectiveStepContractWinner winner =
                effective.stepContractWinner(new ActivationId(activationId), stepContractId);
        return new EffectiveStepContract(
                new PackIdentifier(winner.packId()),
                winner.stepContract(),
                winner.missionType()
                        .map(type -> MissionTypeNarrowing.narrowed(
                                type,
                                "The read of the step contract " + stepContractId + " under activation "
                                        + activationId)),
                winner.held().stream()
                        .map(ref -> new HeldArtifact(ref.kind(), ref.id()))
                        .collect(Collectors.toUnmodifiableSet()),
                winner.sourceFingerprint());
    }

    @Override
    public EffectiveStepPlace stepPlace(String activationId, String missionTypeId, String stepId) {
        EffectiveReadService.StepPlace place =
                effective.stepPlace(new ActivationId(activationId), missionTypeId, stepId);
        return new EffectiveStepPlace(new PackIdentifier(place.packId()), place.sourceFingerprint());
    }
}
