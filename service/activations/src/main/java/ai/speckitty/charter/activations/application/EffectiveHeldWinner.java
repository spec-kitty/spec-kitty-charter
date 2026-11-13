package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.Objects;

/**
 * The version of one artifact the layer merge keeps across the levels of an activation's closure, read from the merge
 * alone (research R2, operator ruling {@code DM-01M78S3RNPZ5ZFV5AR6H1X6HV1}): the pack and level that hold it and its
 * policy. It carries no state: it is what an activation answers of a kind the effective set does not resolve (an
 * asset is never selected, denied or cascaded to, so a state or a reason would be invented), and of the winners of a
 * kind it lists. Its entity, and an asset's bytes, are the packs slice's to read, against the source fingerprint of the
 * read that named the winner.
 *
 * @param ref the artifact
 * @param packId the configured identifier of the pack that holds the version in force
 * @param level the level that pack is configured at
 * @param policy the artifact's mutability level along the closure
 */
public record EffectiveHeldWinner(ArtifactRef ref, String packId, PackLevel level, Policy policy) {

    /**
     * Checks the fields.
     *
     * @param ref the artifact
     * @param packId the holding pack
     * @param level its level
     * @param policy the mutability level
     */
    public EffectiveHeldWinner {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(policy, "policy");
    }
}
