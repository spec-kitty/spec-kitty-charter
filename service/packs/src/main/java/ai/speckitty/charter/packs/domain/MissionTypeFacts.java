package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;

/**
 * One mission type of a pack as plain facts (operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN): the file that declares it,
 * its id, and the ids of its steps and its on-demand steps. The packs domain cannot name the missions domain that types
 * a mission type, so {@link MissionStepLinkRule} judges these.
 *
 * @param file the pack-relative path of the mission type file
 * @param id the mission type's id
 * @param stepIds the ids of its steps, then of its on-demand steps, as written
 */
public record MissionTypeFacts(String file, String id, List<String> stepIds) {
    /**
     * Checks the fields and copies the ids.
     *
     * @param file the file
     * @param id the id
     * @param stepIds the step ids
     */
    public MissionTypeFacts {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(id, "id");
        stepIds = List.copyOf(stepIds);
    }
}
