package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Objects;

/**
 * The place of one step under an activation, as the {@link ActivationMissionTypes} port answers it (research R8, A7,
 * DIV-243): the pack that holds the merged winner of {@code action:<missionType>/<step>}, which keeps a step that only a
 * version the winning mission type replaced declares, and the source fingerprint of the read. What that pack stores of
 * the mission type (its version of the type, its skeletons and step files) is the {@link PackMissionTypes} port's to
 * read, at the same fingerprint.
 *
 * @param pack the configured identifier of the place's pack
 * @param fingerprint the source fingerprint of the read
 */
public record EffectiveStepPlace(PackIdentifier pack, String fingerprint) {

    /**
     * Checks the fields.
     *
     * @param pack the place's pack
     * @param fingerprint the source fingerprint
     */
    public EffectiveStepPlace {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
