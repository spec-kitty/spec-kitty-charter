package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.domain.MissionType;
import java.util.Objects;
import java.util.Set;

/**
 * The winner of one mission type under an activation, as the {@link ActivationMissionTypes} port answers it (D12): the
 * type, the pack that holds it and the source fingerprint of the read.
 *
 * @param holder the configured identifier of the pack that holds the winner
 * @param type the winning mission type
 * @param fingerprint the source fingerprint of the read
 * @param held the artifacts of a listed kind the merged closure holds
 */
public record EffectiveMissionType(
        PackIdentifier holder, MissionType type, String fingerprint, Set<HeldArtifact> held) {

    /**
     * Checks and copies the fields.
     *
     * @param holder the holding pack
     * @param type the winning type
     * @param fingerprint the source fingerprint
     * @param held the held artifacts
     */
    public EffectiveMissionType {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fingerprint, "fingerprint");
        held = Set.copyOf(held);
    }
}
