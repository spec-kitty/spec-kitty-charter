package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The port of the activation-scoped profile read (ADR-0037 as amended 2026-11-09): where the winner of a profile under an
 * activation, with its state and its governance, comes from. The adapter is {@code profiles.infra.activations}, the one
 * place of the slice that asks the activations slice; the application ring names no activations type.
 */
public interface EffectiveProfiles {

    /**
     * One agent profile under an activation, read once from one verified session.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param profileId the profile's id, exactly as declared
     * @param depth the number of {@code suggests} hops of the governance walk, 0 or 1
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the profile read, or that the client holds the current representation
     * @throws CharterRefusal with {@code ACTIVATION_NOT_FOUND}, {@code ARTIFACT_NOT_FOUND}, {@code PACK_UNREADABLE}, the
     *     code of a closure that cannot compose or merge, or {@code STALE_INPUT}
     */
    Outcome read(String activationId, String profileId, int depth, @Nullable String ifNoneMatch);

    /** What a read under an activation answers. */
    sealed interface Outcome {

        /**
         * The client holds the current representation ({@code 304}).
         *
         * @param etag the current entity tag, unquoted
         */
        record NotModified(String etag) implements Outcome {

            /**
             * Checks the tag.
             *
             * @param etag the tag
             */
            public NotModified {
                Objects.requireNonNull(etag, "etag");
            }
        }

        /**
         * The current representation.
         *
         * @param read the profile under the activation
         * @param etag its entity tag, unquoted
         */
        record Found(EffectiveProfileRead read, String etag) implements Outcome {

            /**
             * Checks the fields.
             *
             * @param read the read
             * @param etag the tag
             */
            public Found {
                Objects.requireNonNull(read, "read");
                Objects.requireNonNull(etag, "etag");
            }
        }
    }
}
