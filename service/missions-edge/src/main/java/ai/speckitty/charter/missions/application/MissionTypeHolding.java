package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.missions.domain.MissionType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What a scope holds of one mission type, read at one fingerprint: the type, its skeletons and the stored files of its
 * steps. The mission-type reads build their views and links
 * from it alone, so a link points only at what the same read found.
 *
 * @param type the mission type
 * @param skeletons the stored files under the type's {@code templates/} directory
 * @param stepFiles the stored prompt and guidelines files of each step, by step id; a step without an entry has none
 * @param fingerprint the source fingerprint the read verified
 * @param held the delegation candidates of the type's substeps the scope holds, of a listed kind (a kind the service
 *     serves), each by its kind and the candidate as written ({@code [<pack>::]<id>}): a substep's candidate links its
 *     read only when it is one
 */
public record MissionTypeHolding(
        MissionType type,
        List<StoredSkeleton> skeletons,
        Map<String, StepFiles> stepFiles,
        String fingerprint,
        Set<HeldArtifact> held) {

    /**
     * A holding, its lists and map copied.
     *
     * @param type the mission type
     * @param skeletons its skeletons
     * @param stepFiles its step files
     * @param fingerprint the verified fingerprint
     * @param held the held delegation candidates
     */
    public MissionTypeHolding {
        Objects.requireNonNull(type, "type");
        skeletons = List.copyOf(skeletons);
        stepFiles = Map.copyOf(stepFiles);
        Objects.requireNonNull(fingerprint, "fingerprint");
        held = Set.copyOf(held);
    }

    /**
     * The delegation candidates of the type's substeps that a scope holds, each by its kind and the candidate as written:
     * under an activation the merged closure holds an artifact by its kind and id alone, so a candidate written with a
     * pack's name is held when the closure holds the artifact of its id.
     *
     * @param type the mission type
     * @param held the artifacts of a listed kind the scope holds, by kind and id
     * @return the held candidates, by kind and the candidate as written
     */
    public static Set<HeldArtifact> heldCandidates(MissionType type, Set<HeldArtifact> held) {
        return Stream.concat(type.steps().stream(), type.onDemandSteps().stream())
                .flatMap(step -> step.substeps().stream())
                .flatMap(substep -> substep.delegatesTo().stream())
                .flatMap(delegation -> delegation.candidates().stream()
                        .filter(candidate -> held.contains(new HeldArtifact(delegation.kind(), candidate.id())))
                        .map(candidate -> new HeldArtifact(delegation.kind(), candidate.toString())))
                .collect(Collectors.toUnmodifiableSet());
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
         * @param read the read that holds it, as the fault names it, for example
         *     {@code The read of the mission type software-dev in pack main}
         * @return the skeleton
         * @throws IllegalStateException when the id does not start with the type's prefix, naming the read: a template
         *     of the type outside its directory is a bug of the store, never a refusal
         */
        public static StoredSkeleton ofTemplate(
                String templateId, String missionTypeId, StoredFile content, String read) {
            String prefix = missionTypeId + "/";
            if (!templateId.startsWith(prefix)) {
                throw new IllegalStateException(
                        read + " holds the template " + templateId + " outside the type's templates/ directory.");
            }
            return new StoredSkeleton(templateId.substring(prefix.length()), content);
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
