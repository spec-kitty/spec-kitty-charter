package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The stored bytes of the step documents of a mission type of a {@link Pack}: each step's prompt and guidelines files,
 * exactly as stored (#99). The packs rings cannot name the missions domain that types a mission type and its steps, so
 * they hold the type as a {@link CharterArtifact} and ask this port, which the store that binds the missions domain
 * implements. A read serves these bytes, never a re-rendering of the parsed documents, so what is served is what is
 * stored (PROJECT_002, ADR-0036); slot markers such as {@code <!-- spdd:reasons-block:start -->} survive.
 *
 * <p>Call it inside the verified read that loaded the mission type: the source fingerprint digests the files, so bytes
 * read between its two digests are the bytes the loaded type was parsed from.
 */
@FunctionalInterface
public interface PackCompanionFiles {

    /**
     * The stored files of one step.
     *
     * @param prompt the bytes of the step's prompt file; empty when the step has none
     * @param guidelines the bytes of the step's guidelines file; empty when the step has none
     */
    record StepFiles(Optional<Payload> prompt, Optional<Payload> guidelines) {

        /**
         * Checks the fields.
         *
         * @param prompt the prompt's bytes, possibly empty
         * @param guidelines the guidelines' bytes, possibly empty
         */
        public StepFiles {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(guidelines, "guidelines");
        }
    }

    /**
     * The stored prompt and guidelines files of every step of a mission type.
     *
     * @param pack the pack that holds the mission type
     * @param missionType an artifact of kind {@code mission_type}, as the pack aggregate holds it
     * @return each step's files by its id, its steps then its on-demand steps; empty for any other artifact
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when a file the type names
     *     cannot be read, or {@code PACK_NOT_FOUND} when no configured pack has this identifier
     */
    Map<String, StepFiles> stepFiles(PackIdentifier pack, CharterArtifact missionType);
}
