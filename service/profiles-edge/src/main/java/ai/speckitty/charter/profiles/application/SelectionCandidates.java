package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.profiles.domain.selection.Candidates;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.Objects;
import java.util.Optional;

/**
 * The port of the profile selection (ruling D1 of slice G): where the candidates of one role under an activation come
 * from, with the pin and the fallback in force, the source fingerprint and the tag of the representation. The adapter
 * is {@code profiles.infra.activations}, the one place of the slice that asks the activations slice; the application
 * ring names no activations type.
 */
public interface SelectionCandidates {

    /**
     * The candidates of one role under an activation, read once from one verified session.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param role the asked role
     * @param context the asked context
     * @return the candidates, the pin and the fallback, conflict-free, with the fingerprint and the tag
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code ACTIVATION_NOT_FOUND},
     *     {@code PACK_UNREADABLE}, the code of a closure that cannot compose or merge, {@code FALLBACK_PROFILE_CONFLICT},
     *     {@code ROLE_PIN_CONFLICT} or {@code STALE_INPUT}
     */
    Read read(String activationId, String role, SelectionContext context);

    /**
     * The candidates of the role one step names under an activation (FR-012), read once from one verified session: the
     * step is judged as the action context judges it, and its role is read at the step's place.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param missionType the step's mission type
     * @param action the step
     * @param context the asked context
     * @return the step's role and the candidates of that role, or of none for a step that names none
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #read} does, with
     *     {@code MISSION_TYPE_NOT_FOUND} or {@code ACTION_NOT_FOUND} after {@code ACTIVATION_NOT_FOUND}
     */
    StepRead readForStep(String activationId, String missionType, String action, SelectionContext context);

    /**
     * What the port reads for a step-keyed selection.
     *
     * @param role the role the step names; empty when it names none, so any role may act
     * @param read the candidates, the fingerprint and the tag
     */
    record StepRead(Optional<String> role, Read read) {

        /**
         * Checks the fields.
         *
         * @param role the step's role
         * @param read the read
         */
        public StepRead {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(read, "read");
        }
    }

    /**
     * What the port reads for a selection.
     *
     * @param activationId the activation's id
     * @param candidates the profiles in force with their packs, closure positions and depths, the pin and the fallback
     * @param sourceFingerprint the source fingerprint of the read
     * @param etag the tag of the representation, unquoted
     */
    record Read(String activationId, Candidates candidates, String sourceFingerprint, String etag) {

        /**
         * Checks the fields.
         *
         * @param activationId the activation's id
         * @param candidates the candidates
         * @param sourceFingerprint the source fingerprint
         * @param etag the tag
         */
        public Read {
            Objects.requireNonNull(activationId, "activationId");
            Objects.requireNonNull(candidates, "candidates");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(etag, "etag");
        }
    }
}
