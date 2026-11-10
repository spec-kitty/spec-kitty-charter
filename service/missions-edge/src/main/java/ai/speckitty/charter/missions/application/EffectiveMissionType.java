package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.domain.MissionType;
import java.util.List;
import java.util.Objects;

/**
 * The winner of one mission type under an activation, as the {@link ActivationMissionTypes} port answers it (D12): the
 * type, the pack that holds it, the merged closure's step contracts bound to it and the source fingerprint of the read.
 * A step contract is a kernel {@link CharterArtifact}: narrowing it is the {@link StepContracts} port's.
 *
 * @param holder the configured identifier of the pack that holds the winner
 * @param type the winning mission type
 * @param stepContracts the winning step contracts of the merged closure bound to the type, by id in code-point order
 * @param fingerprint the source fingerprint of the read
 */
public record EffectiveMissionType(
        PackIdentifier holder, MissionType type, List<CharterArtifact> stepContracts, String fingerprint) {

    /**
     * Checks and copies the fields.
     *
     * @param holder the holding pack
     * @param type the winning type
     * @param stepContracts the step contracts bound to it
     * @param fingerprint the source fingerprint
     */
    public EffectiveMissionType {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(type, "type");
        stepContracts = List.copyOf(Objects.requireNonNull(stepContracts, "stepContracts"));
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
