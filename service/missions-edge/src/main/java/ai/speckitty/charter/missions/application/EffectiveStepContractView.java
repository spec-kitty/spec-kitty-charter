package ai.speckitty.charter.missions.application;

import java.util.Objects;
import java.util.Set;

/**
 * The step contract in force under an activation with what its links need (D11): whether the closure holds the mission
 * type its {@code mission} names, whether the winning type declares the step its {@code action} names, and which of its
 * delegation candidates of a listed kind the closure holds.
 *
 * @param contract the winning contract
 * @param missionTypeHeld whether the closure holds its mission type
 * @param stepHeld whether the winning mission type declares its step, among its steps or its on-demand steps
 * @param heldCandidates its delegation candidates of a listed kind that the closure holds
 */
public record EffectiveStepContractView(
        StepContractView contract, boolean missionTypeHeld, boolean stepHeld, Set<HeldArtifact> heldCandidates) {

    /**
     * Checks and copies the fields.
     *
     * @param contract the contract
     * @param missionTypeHeld whether its type is held
     * @param stepHeld whether its step is held
     * @param heldCandidates its held candidates
     */
    public EffectiveStepContractView {
        Objects.requireNonNull(contract, "contract");
        heldCandidates = Set.copyOf(Objects.requireNonNull(heldCandidates, "heldCandidates"));
    }
}
