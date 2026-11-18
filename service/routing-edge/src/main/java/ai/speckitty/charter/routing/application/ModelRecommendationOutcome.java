package ai.speckitty.charter.routing.application;

import java.util.Objects;

/** What {@code getModelRecommendation} answers: the recommendation, or that the client holds it already. */
public sealed interface ModelRecommendationOutcome {

    /**
     * The recommendation, with the fingerprint of the one verified read and its tag.
     *
     * @param read the recommendation
     * @param sourceFingerprint the source fingerprint of the read
     * @param etag the tag of the representation, unquoted
     */
    record Found(ModelRecommendationRead read, String sourceFingerprint, String etag)
            implements ModelRecommendationOutcome {

        /**
         * Checks the fields.
         *
         * @param read the recommendation
         * @param sourceFingerprint the fingerprint
         * @param etag the tag
         */
        public Found {
            Objects.requireNonNull(read, "read");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(etag, "etag");
        }
    }

    /**
     * The client's {@code If-None-Match} matches the tag.
     *
     * @param etag the tag, unquoted
     */
    record NotModified(String etag) implements ModelRecommendationOutcome {

        /**
         * Checks the tag.
         *
         * @param etag the tag
         */
        public NotModified {
            Objects.requireNonNull(etag, "etag");
        }
    }
}
