package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.util.List;
import java.util.Objects;

/**
 * What {@link EffectiveReadService#missionTypeWinner} answers (#99, FR-007): the winner of one mission type under an
 * activation, the pack that holds it, the merged closure's step contracts bound to it and the source fingerprint of the
 * read. It is what the activations slice owns of a mission type; what the holding pack stores beside the type (its
 * skeletons and step files) is the packs slice's to read, and the missions edge checks that read against the
 * fingerprint here.
 *
 * @param packId the configured identifier of the pack that holds the winner
 * @param missionType the winning mission type, as its pack stores it; a caller narrows it to its kind
 * @param stepContracts the winning step contracts of the merged closure bound to the type, each as the pack that holds
 *     it stores it, by id in code-point order
 * @param sourceFingerprint the source fingerprint of the read
 */
public record EffectiveMissionTypeWinner(
        String packId, CharterArtifact missionType, List<CharterArtifact> stepContracts, String sourceFingerprint) {

    /**
     * Checks and copies the fields.
     *
     * @param packId the holding pack
     * @param missionType the winning type
     * @param stepContracts the step contracts bound to it
     * @param sourceFingerprint the source fingerprint
     */
    public EffectiveMissionTypeWinner {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(missionType, "missionType");
        stepContracts = List.copyOf(Objects.requireNonNull(stepContracts, "stepContracts"));
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
    }
}
