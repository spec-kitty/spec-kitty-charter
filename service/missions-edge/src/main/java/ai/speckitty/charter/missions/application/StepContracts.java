package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;

/**
 * The port that narrows a mission step contract, held as the kernel's {@link CharterArtifact}, to what the missions
 * slice reads of it (D12). The packs slice binds the kind; its adapter, {@code missions.infra.packs}, is the one place
 * of the slice that may name that binding.
 */
@FunctionalInterface
public interface StepContracts {

    /**
     * The step contract's id and the step its {@code action} names.
     *
     * @param contract an artifact of kind {@code mission_step_contract}
     * @return the contract as a binding of its step
     * @throws IllegalStateException when the artifact is no mission step contract: a bug, never a refusal
     */
    MissionTypeHolding.BoundContract bound(CharterArtifact contract);
}
