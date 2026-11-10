package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;

/**
 * The port that narrows a mission step contract, held as the kernel's {@link CharterArtifact}, to what the missions
 * slice reads of it (D11, D12). The packs slice binds the kind; its adapter, {@code missions.infra.packs}, is the one
 * place of the slice that may name that binding.
 */
@FunctionalInterface
public interface StepContracts {

    /**
     * The step contract's stored fields.
     *
     * @param contract an artifact of kind {@code mission_step_contract}
     * @param read the read that holds it, as the fault names it, for example
     *     {@code The read of the step contract specify-contract under activation a-1}
     * @return the contract as the missions slice reads it
     * @throws IllegalStateException when the artifact is no mission step contract, naming the read that lacks it: a
     *     bug, never a refusal
     */
    StepContractView view(CharterArtifact contract, String read);
}
