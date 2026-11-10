package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.missions.domain.MissionType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scope holds of one mission type, read at one fingerprint: the type, its skeletons, the step contracts whose
 * {@code mission} is the type, and the stored files of its steps. The mission-type reads build their views and links
 * from it alone, so a link points only at what the same read found.
 *
 * @param type the mission type
 * @param skeletons the stored files under the type's {@code templates/} directory
 * @param stepContracts the step contracts whose {@code mission} is the type
 * @param stepFiles the stored prompt and guidelines files of each step, by step id; a step without an entry has none
 * @param fingerprint the source fingerprint the read verified
 */
public record MissionTypeHolding(
        MissionType type,
        List<StoredSkeleton> skeletons,
        List<BoundContract> stepContracts,
        Map<String, StepFiles> stepFiles,
        String fingerprint) {

    /**
     * A holding, its lists and map copied.
     *
     * @param type the mission type
     * @param skeletons its skeletons
     * @param stepContracts the step contracts bound to it
     * @param stepFiles its step files
     * @param fingerprint the verified fingerprint
     */
    public MissionTypeHolding {
        Objects.requireNonNull(type, "type");
        skeletons = List.copyOf(skeletons);
        stepContracts = List.copyOf(stepContracts);
        stepFiles = Map.copyOf(stepFiles);
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    /**
     * One skeleton of the type.
     *
     * @param path its path under the type's {@code templates/} directory, for example {@code divio/howto-template.md}
     * @param content its stored bytes
     */
    public record StoredSkeleton(String path, StoredFile content) {

        /**
         * Checks the fields.
         *
         * @param path the path under {@code templates/}
         * @param content the stored bytes
         */
        public StoredSkeleton {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(content, "content");
        }

        /**
         * The skeleton a template of the type is: its id, {@code <type>/<path under templates/>}, without the
         * {@code <type>/} prefix.
         *
         * @param templateId the template's id
         * @param missionTypeId the mission type's id
         * @param content its stored bytes
         * @return the skeleton
         * @throws IllegalStateException when the id does not start with the type's prefix: a template of the type
         *     outside its directory is a bug of the store, never a refusal
         */
        public static StoredSkeleton ofTemplate(String templateId, String missionTypeId, StoredFile content) {
            String prefix = missionTypeId + "/";
            if (!templateId.startsWith(prefix)) {
                throw new IllegalStateException("a template of the type outside its directory: " + templateId);
            }
            return new StoredSkeleton(templateId.substring(prefix.length()), content);
        }
    }

    /**
     * One step contract whose {@code mission} is the type.
     *
     * @param id the step contract's id
     * @param action the step its {@code action} names
     */
    public record BoundContract(String id, String action) {

        /**
         * Checks the fields.
         *
         * @param id the id
         * @param action the action
         */
        public BoundContract {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(action, "action");
        }
    }

    /**
     * The stored files of one step.
     *
     * @param prompt its prompt file; empty when it has none
     * @param guidelines its guidelines file; empty when it has none
     */
    public record StepFiles(Optional<StoredFile> prompt, Optional<StoredFile> guidelines) {

        /** A step without files. */
        public static final StepFiles NONE = new StepFiles(Optional.empty(), Optional.empty());

        /**
         * Checks the fields.
         *
         * @param prompt the prompt, possibly empty
         * @param guidelines the guidelines, possibly empty
         */
        public StepFiles {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(guidelines, "guidelines");
        }
    }
}
