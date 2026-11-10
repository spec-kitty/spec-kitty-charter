package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.Objects;
import java.util.Optional;

/**
 * The winning copy of one artifact under an activation, as one verified read of the closure answers it: the entity the
 * layer merge keeps, the pack that holds it and, for an agent profile, its governance. The entity is the kernel's
 * {@link CharterArtifact}, as the winner's pack stores it; the state it has under the activation is the
 * {@link EffectiveSet}'s to say and is not carried here.
 *
 * @param artifact the winning entity; a caller narrows it to its kind
 * @param packId the configured identifier of the pack that holds the version in force
 * @param level the level of that pack
 * @param governance the profile's governance under the activation; empty for every kind but {@code agent_profile}
 */
public record EffectiveArtifactRead(
        CharterArtifact artifact, String packId, PackLevel level, Optional<GovernanceFacts> governance) {

    /**
     * Checks the fields.
     *
     * @param artifact the winning entity
     * @param packId the holding pack
     * @param level its level
     * @param governance the governance
     */
    public EffectiveArtifactRead {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(governance, "governance");
    }
}
