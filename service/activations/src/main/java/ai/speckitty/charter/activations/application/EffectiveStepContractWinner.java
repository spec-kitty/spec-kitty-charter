package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What {@link EffectiveReadService#stepContractWinner} answers (#99, D11): the winner of one step contract under an
 * activation, the pack that holds it, the winner of the mission type it is bound to when the closure holds that type,
 * the artifacts of a listed kind the merged closure holds (a delegation candidate's read under the activation answers
 * for those) and the source fingerprint of the read.
 *
 * @param packId the configured identifier of the pack that holds the winner
 * @param stepContract the winning step contract, as its pack stores it; a caller narrows it to its kind
 * @param missionType the winning mission type the contract is bound to, as its pack stores it; empty when no pack of
 *     the closure holds that type
 * @param held the artifacts of a kind the packs slice lists that the merged closure holds
 * @param sourceFingerprint the source fingerprint of the read
 */
public record EffectiveStepContractWinner(
        String packId,
        CharterArtifact stepContract,
        Optional<CharterArtifact> missionType,
        Set<ArtifactRef> held,
        String sourceFingerprint) {

    /**
     * Checks and copies the fields.
     *
     * @param packId the holding pack
     * @param stepContract the winning step contract
     * @param missionType the winning type it is bound to
     * @param held the held artifacts of a listed kind
     * @param sourceFingerprint the source fingerprint
     */
    public EffectiveStepContractWinner {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(stepContract, "stepContract");
        Objects.requireNonNull(missionType, "missionType");
        held = Set.copyOf(Objects.requireNonNull(held, "held"));
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
    }
}
