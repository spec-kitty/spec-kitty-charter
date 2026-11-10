package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What an activation's merged closure holds of one mission type (#99, FR-007), read from one {@link PacksSession}: the
 * winner of the layer merge as the pack that holds it stores it, that pack's skeletons of the type and its step files,
 * and the step contracts of the merged closure whose {@code mission} is the type, each the winner of its own id. The
 * skeletons and the step files are not merged on their own: they are the holding pack's, as the type is.
 *
 * @param packId the configured identifier of the pack that holds the winner
 * @param level the level that pack is configured at
 * @param missionType the winning mission type, as its pack stores it; a caller narrows it to its kind
 * @param skeletons the holding pack's templates of the type, by id in code-point order
 * @param stepFiles the holding pack's stored prompt and guidelines files of each step, by step id
 * @param stepContracts the winning step contracts whose {@code mission} is the type, by id in code-point order
 */
public record HeldMissionType(
        String packId,
        PackLevel level,
        CharterArtifact missionType,
        List<Skeleton> skeletons,
        Map<String, StepFiles> stepFiles,
        List<StepContract> stepContracts) {

    /**
     * Checks and copies the fields.
     *
     * @param packId the holding pack
     * @param level its level
     * @param missionType the winning type
     * @param skeletons its skeletons
     * @param stepFiles its step files
     * @param stepContracts the step contracts bound to it
     */
    public HeldMissionType {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(missionType, "missionType");
        skeletons = List.copyOf(Objects.requireNonNull(skeletons, "skeletons"));
        stepFiles = Map.copyOf(Objects.requireNonNull(stepFiles, "stepFiles"));
        stepContracts = List.copyOf(Objects.requireNonNull(stepContracts, "stepContracts"));
    }

    /**
     * A skeleton of the type in the holding pack.
     *
     * @param id the template's id, {@code <type>/<path under the type's templates/>}
     * @param content its stored bytes
     */
    public record Skeleton(String id, HeldBytes content) {

        /**
         * Checks the fields.
         *
         * @param id the id
         * @param content the bytes
         */
        public Skeleton {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * The stored files of one step in the holding pack.
     *
     * @param prompt its prompt file; empty when it has none
     * @param guidelines its guidelines file; empty when it has none
     */
    public record StepFiles(Optional<HeldBytes> prompt, Optional<HeldBytes> guidelines) {

        /**
         * Checks the fields.
         *
         * @param prompt the prompt
         * @param guidelines the guidelines
         */
        public StepFiles {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(guidelines, "guidelines");
        }
    }

    /**
     * A winning step contract of the merged closure whose {@code mission} is the type.
     *
     * @param id its id
     * @param action the step its {@code action} names
     * @param packId the configured identifier of the pack that holds the winner
     */
    public record StepContract(String id, String action, String packId) {

        /**
         * Checks the fields.
         *
         * @param id the id
         * @param action the action
         * @param packId the holding pack
         */
        public StepContract {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(packId, "packId");
        }
    }
}
