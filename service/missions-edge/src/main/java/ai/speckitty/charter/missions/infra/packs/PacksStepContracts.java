package ai.speckitty.charter.missions.infra.packs;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.StepContracts;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import org.springframework.stereotype.Component;

/**
 * The one bean of {@link StepContracts}: the packs slice binds the kind {@code mission_step_contract} to its
 * {@link MissionStepContract}, which only this adapter of the missions slice may name, so any other artifact here is a
 * bug, never a refusal.
 */
@Component
public final class PacksStepContracts implements StepContracts {

    /** The adapter; it holds nothing. */
    public PacksStepContracts() {}

    @Override
    public MissionTypeHolding.BoundContract bound(CharterArtifact contract) {
        MissionStepContract narrowed = narrowed(contract);
        return new MissionTypeHolding.BoundContract(narrowed.localId(), narrowed.action());
    }

    /** The step contract the pack store bound; anything else is a bug of the store. */
    static MissionStepContract narrowed(CharterArtifact artifact) {
        if (artifact instanceof MissionStepContract contract) {
            return contract;
        }
        throw new IllegalStateException("the packs read a mission step contract as "
                + artifact.getClass().getName());
    }
}
