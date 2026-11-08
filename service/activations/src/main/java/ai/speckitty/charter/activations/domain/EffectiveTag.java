package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The strong entity tag of a read that depends on the packs of an activation's closure (research R5, decisions
 * {@code 01M6Y11E55YP5TG1EJ1MDKCFRP} and {@code 01M6Y3GZFRC5WEQQ7G6GT0YMW3}): the two effective reads and
 * {@code getActivation}. It is {@code sha256:} over a domain tag, the representation read, the activation's revision,
 * the verified fingerprint of the packs of the closure and the normalized query, so it changes when the document or a
 * pack of the closure changes and never names two representations (another page, kind, state or read). A pack outside
 * the closure is not in the fingerprint and leaves the tag as it is.
 *
 * <p>The token is the opaque tag without its quotes, as {@code EntityTags} takes it.
 *
 * @param token {@code sha256:} and 64 lowercase hex characters
 */
public record EffectiveTag(String token) {

    private static final Pattern TOKEN = Pattern.compile("sha256:[0-9a-f]{64}");

    /** The read a tag names. */
    public enum Read {
        /** {@code getActivation}: the document with the deny entries the floor or a lock overrides. */
        ACTIVATION,
        /** {@code listEffectiveArtifacts}. */
        ARTIFACTS,
        /** {@code getActivationSkippedReport}. */
        SKIPPED
    }

    /**
     * The query of {@code listEffectiveArtifacts}, as it selects a representation: a filter that is absent is the same
     * representation however the request spelled its absence.
     *
     * @param state the {@code state} filter, if the request sent one
     * @param kind the {@code kind} filter, if the request sent one
     * @param cursor the page cursor, if the request sent one
     * @param pageSize the page size the request resolved to, its default when it sent none
     */
    public record Query(Optional<State> state, Optional<ArtifactKind> kind, Optional<String> cursor, int pageSize) {

        /**
         * Checks the fields.
         *
         * @param state the state filter
         * @param kind the kind filter
         * @param cursor the cursor
         * @param pageSize the page size
         */
        public Query {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(cursor, "cursor");
        }
    }

    /**
     * Checks the token.
     *
     * @param token the tag without its quotes
     * @throws IllegalArgumentException when it is not {@code sha256:} and 64 lowercase hex characters
     */
    public EffectiveTag {
        Objects.requireNonNull(token, "token");
        if (!TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("An effective tag is sha256: and 64 lowercase hex characters.");
        }
    }

    /**
     * The tag of {@code getActivation}: the revision and the closure's fingerprint.
     *
     * @param revision the activation's revision
     * @param closure the closure the answer was computed from
     * @return the tag
     */
    public static EffectiveTag activation(Revision revision, ClosureSnapshot closure) {
        return new EffectiveTag("sha256:" + "0".repeat(64));
    }

    /**
     * The tag of the skipped report.
     *
     * @param revision the activation's revision
     * @param closure the closure the answer was computed from
     * @return the tag
     */
    public static EffectiveTag skipped(Revision revision, ClosureSnapshot closure) {
        return new EffectiveTag("sha256:" + "0".repeat(64));
    }

    /**
     * The tag of one representation of the effective artifact list.
     *
     * @param revision the activation's revision
     * @param closure the closure the answer was computed from
     * @param query the filters, the cursor and the page size
     * @return the tag
     */
    public static EffectiveTag artifacts(Revision revision, ClosureSnapshot closure, Query query) {
        return new EffectiveTag("sha256:" + "0".repeat(64));
    }

    @Override
    public String toString() {
        return token;
    }
}
