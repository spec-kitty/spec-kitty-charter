package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.missions.domain.MissionType;

/**
 * The narrowing of a mission type an adapter is handed as the kernel's {@link CharterArtifact}: the pack store binds
 * the kind to {@link MissionType}, so any other artifact is a bug of the store, never a refusal.
 */
public final class MissionTypeNarrowing {

    private MissionTypeNarrowing() {}

    /**
     * The mission type the pack store bound.
     *
     * @param artifact an artifact of kind {@code mission_type}
     * @param read the read that holds it, as the fault names it, for example
     *     {@code The read of the mission type software-dev under activation a-1}
     * @return the mission type
     * @throws IllegalStateException when the artifact is no {@link MissionType}, naming the read that lacks it
     */
    public static MissionType narrowed(CharterArtifact artifact, String read) {
        if (artifact instanceof MissionType type) {
            return type;
        }
        throw new IllegalStateException(read + " holds " + artifact.kind().token() + ":" + artifact.localId() + " as a "
                + artifact.getClass().getName() + ", not a mission type.");
    }
}
