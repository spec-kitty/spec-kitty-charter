package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.util.Set;

/**
 * The step ids a mission type of a {@link Pack} declares in its file: its steps and its on-demand steps. The packs rings
 * cannot name the missions domain that types a mission type, so they hold it as a {@link CharterArtifact} and ask this
 * port, which the store that binds the missions domain implements. A step contract's {@code step} link is built only
 * when its mission type declares the step its {@code action} names.
 */
@FunctionalInterface
public interface MissionTypeSteps {

    /**
     * The ids of the steps a mission type declares.
     *
     * @param missionType an artifact of kind {@code mission_type}, as a pack aggregate holds it
     * @return the ids of its steps and its on-demand steps; empty for any other artifact
     */
    Set<String> stepIds(CharterArtifact missionType);
}
