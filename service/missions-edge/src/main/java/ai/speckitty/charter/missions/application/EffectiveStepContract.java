package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.domain.MissionType;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The winner of one step contract under an activation, as the {@link ActivationMissionTypes} port answers it (D11): the
 * contract, the pack that holds it, the winning mission type it is bound to when the closure holds it, the artifacts of
 * a listed kind the closure holds, and the source fingerprint of the read.
 *
 * @param holder the configured identifier of the pack that holds the winner
 * @param contract the winning step contract, as a kernel artifact that the {@link StepContracts} port narrows
 * @param missionType the winning mission type the contract is bound to; empty when no pack of the closure holds it
 * @param held the artifacts of a listed kind the closure holds
 * @param fingerprint the source fingerprint of the read
 */
public record EffectiveStepContract(
        PackIdentifier holder,
        CharterArtifact contract,
        Optional<MissionType> missionType,
        Set<HeldArtifact> held,
        String fingerprint) {

    /**
     * Checks and copies the fields.
     *
     * @param holder the holding pack
     * @param contract the winning contract
     * @param missionType the winning type it is bound to
     * @param held the held artifacts of a listed kind
     * @param fingerprint the source fingerprint
     */
    public EffectiveStepContract {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(missionType, "missionType");
        held = Set.copyOf(Objects.requireNonNull(held, "held"));
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
