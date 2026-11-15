package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.profiles.domain.selection.Candidates;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.Objects;

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
