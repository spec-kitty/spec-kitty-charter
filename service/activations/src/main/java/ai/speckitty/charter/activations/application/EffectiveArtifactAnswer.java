package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What {@link EffectiveReadService#artifact} answers (FR-007): the winner of one artifact under an activation, with the
 * state the effective set gives it, the entity as the winner's pack stores it and, for an agent profile, the governance
 * with the state of each artifact it lists. The application layer is the surface the slices share (decision
 * {@code 01M710DQEMV7VPKDNQPWKPGD4F}): the generic activation route maps {@link #winner} and embeds no body, and the
 * profiles slice's route maps the same answer with {@link #artifact} and {@link #governance}.
 *
 * @param stored the stored activation
 * @param sourceFingerprint the source fingerprint of the read
 * @param governanceFingerprint the digest of the closure's own packs
 * @param winner the artifact as the effective list gives it: the pack and level that hold the version in force, its
 *     state, reason, policy and, if it is one, its effective-set suggestion
 * @param artifact the winning entity, as the winner's pack stores it; a caller narrows it to its kind
 * @param governance a profile's governance; empty for every other kind
 */
public record EffectiveArtifactAnswer(
        StoredActivation stored,
        String sourceFingerprint,
        String governanceFingerprint,
        EffectiveArtifact winner,
        CharterArtifact artifact,
        Optional<Governance> governance) {

    /**
     * A profile's governance under an activation.
     *
     * @param entries the artifacts in force the walk lists, in URN order, each with the state it has
     * @param suggested the artifacts out of force that the effective set judges to be suggestions, in URN order, each with
     *     the words of the edge that reaches it
     */
    public record Governance(List<Entry> entries, List<Suggested> suggested) {

        /**
         * Checks and copies the fields.
         *
         * @param entries the entries
         * @param suggested the suggestions
         */
        public Governance {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            suggested = List.copyOf(Objects.requireNonNull(suggested, "suggested"));
        }
    }

    /**
     * The words of an edge: the {@code when} and the {@code reason} it states, either of which may be absent (FR-004).
     * Every artifact the walk lists carries the words of the edge it was reached through, whatever the relation of that
     * edge, whether the artifact is in force or not. An edge that states neither is {@link #NONE}. Where more than one
     * edge could reach an artifact, the words are those of the edge of its {@code via}, the parent the walk keeps (the
     * first reach: the fewest hops, then the smallest parent, then the smallest edge); the walk lists an artifact once, at
     * the earliest step that reaches it.
     *
     * @param when the condition the edge states
     * @param reason the reason the edge states
     */
    public record EdgeWords(Optional<String> when, Optional<String> reason) {

        /** The edge that states neither word. */
        public static final EdgeWords NONE = new EdgeWords(Optional.empty(), Optional.empty());

        /**
         * Checks the fields.
         *
         * @param when the condition
         * @param reason the reason
         */
        public EdgeWords {
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * One artifact the walk lists.
     *
     * @param artifact the artifact as the effective list gives it
     * @param step the step that listed it
     * @param relation the relation it is listed by
     * @param via its parent: the profile, or another entry
     * @param hops the length of the path from the profile
     * @param edge the words of the edge that listed it, whatever its relation and whether the artifact is in force or
     *     not; {@link EdgeWords#NONE} when that edge states neither
     */
    public record Entry(
            EffectiveArtifact artifact,
            GovernanceFacts.Step step,
            RelationType relation,
            ArtifactRef via,
            int hops,
            EdgeWords edge) {

        /**
         * Checks the fields.
         *
         * @param artifact the artifact
         * @param step the step
         * @param relation the relation
         * @param via the parent
         * @param hops the path length
         * @param edge the listing edge's words
         */
        public Entry {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(edge, "edge");
        }
    }

    /**
     * An artifact out of force that a listed artifact suggests.
     *
     * @param artifact the artifact as the effective list gives it, its suggestion set
     * @param via the listed artifact whose {@code suggests} edge reaches it
     * @param hops the length of the path from the profile
     * @param edge the words of that {@code suggests} edge, which are the words an entry in force carries for its own
     *     edge
     */
    public record Suggested(EffectiveArtifact artifact, ArtifactRef via, int hops, EdgeWords edge) {

        /**
         * Checks the fields.
         *
         * @param artifact the artifact
         * @param via the suggesting artifact
         * @param hops the path length
         * @param edge the suggesting edge's words
         */
        public Suggested {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(edge, "edge");
        }
    }

    /**
     * Checks the fields.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint
     * @param governanceFingerprint the closure's digest
     * @param winner the winner as the list gives it
     * @param artifact the winning entity
     * @param governance the governance
     */
    public EffectiveArtifactAnswer {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(governance, "governance");
    }
}
