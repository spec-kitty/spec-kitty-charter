package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The strong entity tag of a read that depends on the packs of an activation's closure (research R5, decisions
 * {@code 01M6Y11E55YP5TG1EJ1MDKCFRP} and {@code 01M6Y3GZFRC5WEQQ7G6GT0YMW3}): the four effective reads and
 * {@code getActivation}; the graph and the context each name their own read, and the context its mission type, action and depth. It is {@code sha256:} over a domain tag, the representation read, the activation's revision,
 * the verified digest of the packs IN the closure ({@link ClosureSnapshot#closureFingerprint()}, never the source
 * fingerprint of every configured pack) and the normalized query, so it changes when the document or a pack of the
 * closure changes and never names two representations (another page, kind, state or read). A configured pack outside
 * the closure is not in that digest and leaves the tag as it is.
 *
 * <p>The token is the opaque tag without its quotes, as {@code EntityTags} takes it.
 *
 * @param token {@code sha256:} and 64 lowercase hex characters
 */
public record EffectiveTag(String token) {

    private static final Pattern TOKEN = Pattern.compile("sha256:[0-9a-f]{64}");

    /**
     * The domain tag, with the tag's version: it is bumped in any change that alters what the reads answer for the same
     * inputs (a merge rule change, a new reason), so a strong tag never names two bodies across releases.
     */
    private static final String DOMAIN = "charter-effective-read\0v1\n";

    private static final String NO_QUERY = "";

    /** The read a tag names. */
    public enum Read {
        /** {@code getActivation}: the document with the deny entries the floor or a lock overrides. */
        ACTIVATION,
        /** {@code listEffectiveArtifacts}. */
        ARTIFACTS,
        /** {@code getActivationSkippedReport}. */
        SKIPPED,
        /** {@code getEffectiveGraph}. */
        GRAPH,
        /** {@code getEffectiveActionContext}. */
        CONTEXT
    }

    /**
     * The query of {@code getEffectiveActionContext}, as it selects a representation.
     *
     * @param missionType the mission type of the path
     * @param action the action of the path
     * @param depth the number of {@code suggests} hops the request resolved to, its default when it sent none
     */
    public record ContextQuery(String missionType, String action, int depth) {

        /**
         * Checks the fields.
         *
         * @param missionType the mission type
         * @param action the action
         * @param depth the depth
         */
        public ContextQuery {
            Objects.requireNonNull(missionType, "missionType");
            Objects.requireNonNull(action, "action");
        }

        private String normalized() {
            return "missionType=" + missionType + "\naction=" + action + "\ndepth=" + depth;
        }
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

        /** The query as one unambiguous line: an absent part is {@code -}, a cursor is marked {@code +}. */
        private String normalized() {
            return "state=" + state.map(State::token).orElse("-")
                    + "\nkind=" + kind.map(ArtifactKind::token).orElse("-")
                    + "\ncursor=" + cursor.map(value -> "+" + value).orElse("-")
                    + "\npageSize=" + pageSize;
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
        return of(Read.ACTIVATION, revision, closure, NO_QUERY);
    }

    /**
     * The tag of the skipped report.
     *
     * @param revision the activation's revision
     * @param closure the closure the answer was computed from
     * @return the tag
     */
    public static EffectiveTag skipped(Revision revision, ClosureSnapshot closure) {
        return of(Read.SKIPPED, revision, closure, NO_QUERY);
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
        return of(Read.ARTIFACTS, revision, closure, query.normalized());
    }

    /**
     * The tag of the effective graph.
     *
     * @param revision the activation's revision
     * @param closure the closure the answer was computed from
     * @return the tag
     */
    public static EffectiveTag graph(Revision revision, ClosureSnapshot closure) {
        return of(Read.GRAPH, revision, closure, NO_QUERY);
    }

    /**
     * The tag of one effective action context.
     *
     * @param revision the activation's revision
     * @param closure the closure the answer was computed from
     * @param query the mission type, the action and the depth
     * @return the tag
     */
    public static EffectiveTag context(Revision revision, ClosureSnapshot closure, ContextQuery query) {
        return of(Read.CONTEXT, revision, closure, query.normalized());
    }

    private static EffectiveTag of(Read read, Revision revision, ClosureSnapshot closure, String query) {
        String preimage = DOMAIN + read.name().toLowerCase(Locale.ROOT)
                + "\n" + revision.token()
                + "\n" + closure.closureFingerprint()
                + "\n" + query;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(preimage.getBytes(StandardCharsets.UTF_8));
            return new EffectiveTag("sha256:" + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256.", e);
        }
    }

    @Override
    public String toString() {
        return token;
    }
}
