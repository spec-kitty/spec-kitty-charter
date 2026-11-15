package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.util.Objects;
import java.util.Set;

/**
 * What {@link EffectiveReadService#missionTypeWinner} answers (#99, FR-007): the winner of one mission type under an
 * activation, the pack that holds it and the source fingerprint of the read. It is what the activations slice owns of a mission type; what the holding pack stores beside the type (its
 * skeletons and step files) is the packs slice's to read, and the missions edge checks that read against the
 * fingerprint here.
 *
 * @param packId the configured identifier of the pack that holds the winner
 * @param missionType the winning mission type, as its pack stores it; a caller narrows it to its kind
 * @param sourceFingerprint the source fingerprint of the read
 * @param held every artifact of a listed kind the closure holds, as the layer merge keeps it: what a substep's
 *     delegation candidate is linked to under the activation
 */
public record EffectiveMissionTypeWinner(
        String packId, CharterArtifact missionType, String sourceFingerprint, Set<ArtifactRef> held) {

    /**
     * Checks and copies the fields.
     *
     * @param packId the holding pack
     * @param missionType the winning type
     * @param sourceFingerprint the source fingerprint
     * @param held the held artifacts
     */
    public EffectiveMissionTypeWinner {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(missionType, "missionType");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        held = Set.copyOf(Objects.requireNonNull(held, "held"));
    }
}
