package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.StoredActivation;
import java.util.Objects;

/**
 * What {@link EffectiveReadService#missionType} answers (#99, FR-007): the mission type an activation's merged closure
 * holds, with the holding pack's skeletons and step files and the merged closure's step contracts bound to it, all from
 * one verified read. The missions slice builds its mission type, step and skeleton reads under the activation from it.
 *
 * @param stored the stored activation
 * @param sourceFingerprint the source fingerprint of the read
 * @param governanceFingerprint the digest of the closure's own packs
 * @param held what the merged closure holds of the type
 */
public record EffectiveMissionTypeAnswer(
        StoredActivation stored, String sourceFingerprint, String governanceFingerprint, HeldMissionType held) {

    /**
     * Checks the fields.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint
     * @param governanceFingerprint the closure's digest
     * @param held the held type
     */
    public EffectiveMissionTypeAnswer {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
        Objects.requireNonNull(held, "held");
    }
}
