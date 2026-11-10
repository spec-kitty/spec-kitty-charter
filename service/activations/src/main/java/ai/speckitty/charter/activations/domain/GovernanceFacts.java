package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What a profile's governance walk reached under an activation, as the activations slice reads it: the packs slice walked
 * the merged closure with the effective set as its predicate, and this is the adapter's copy, as values only. Each
 * entry names an artifact in force, how the walk reached it and the edge's own words, whatever its relation. The
 * artifacts out of force that a listed artifact suggests are carried apart, as candidates. Which state an artifact has is
 * the {@link EffectiveSet}'s to say, so no state is carried here.
 *
 * <p>The walk's step, via and hops are kept, which {@code ClosureViewSession.contextOf} drops for an action context: this
 * type has its own {@link Step}, and the packs slice's enum reaches no wire through it (fold W6).
 *
 * @param profile the profile the walk started from; it is exempt from the in-force predicate, so it is no entry
 * @param entries the artifacts in force the walk lists, in URN order
 * @param suggestionCandidates the artifacts out of force that a suggests step reached and nothing hard reaches, in URN
 *     order; whether one is a suggestion is the effective set's to judge
 */
public record GovernanceFacts(ArtifactRef profile, List<Entry> entries, List<Candidate> suggestionCandidates) {

    /** The step of the walk that lists an entry; the profile walk's own five steps. */
    public enum Step {
        /** The profile's ancestors, through {@code specializes_from}. */
        LINEAGE,
        /** What the profile and its ancestors require. */
        REQUIRES,
        /** What they suggest, as many hops as the depth says. */
        SUGGESTS,
        /** What the suggested artifacts require. */
        REQUIRES_OF_SUGGESTED,
        /** One hop of vocabulary from the profile and from everything the earlier steps list. */
        VOCABULARY;

        /**
         * The contract's spelling.
         *
         * @return the lower-case token
         */
        public String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One artifact in force the walk lists.
     *
     * @param ref the artifact
     * @param step the step that listed it
     * @param relation the relation the entry is listed by
     * @param via its parent: the profile, or another entry
     * @param hops the length of the path from the profile
     * @param when the {@code when} of the edge that reached it, whatever its relation; empty when the edge states none
     * @param reason the {@code reason} of that edge; empty likewise
     */
    public record Entry(
            ArtifactRef ref,
            Step step,
            RelationType relation,
            ArtifactRef via,
            int hops,
            Optional<String> when,
            Optional<String> reason) {

        /**
         * Checks the fields.
         *
         * @param ref the artifact
         * @param step the step
         * @param relation the relation
         * @param via the parent
         * @param hops the path length
         * @param when the edge's condition
         * @param reason the edge's reason
         */
        public Entry {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * An artifact out of force that a listed artifact suggests: a candidate for a conditional entry.
     *
     * @param ref the suggested artifact
     * @param via the listed artifact whose {@code suggests} edge reaches it
     * @param hops the length of the path from the profile
     * @param when the {@code when} of that edge, if it states one
     * @param reason the {@code reason} of that edge, if it states one
     */
    public record Candidate(
            ArtifactRef ref, ArtifactRef via, int hops, Optional<String> when, Optional<String> reason) {

        /**
         * Checks the fields.
         *
         * @param ref the suggested artifact
         * @param via the suggesting artifact
         * @param hops the path length
         * @param when the edge's condition
         * @param reason the edge's reason
         */
        public Candidate {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param profile the profile
     * @param entries the entries
     * @param suggestionCandidates the candidates
     */
    public GovernanceFacts {
        Objects.requireNonNull(profile, "profile");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        suggestionCandidates = List.copyOf(Objects.requireNonNull(suggestionCandidates, "suggestionCandidates"));
    }
}
