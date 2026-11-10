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
     * @param reader the slice that read it, as the fault names it, for example {@code packs}
     * @return the mission type
     * @throws IllegalStateException when the artifact is no {@link MissionType}
     */
    public static MissionType narrowed(CharterArtifact artifact, String reader) {
        if (artifact instanceof MissionType type) {
            return type;
        }
        throw new IllegalStateException("the " + reader + " read a mission type as "
                + artifact.getClass().getName());
    }
}
