package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Objects;
import java.util.Optional;

/**
 * One artifact in a profile's governance, as the profiles slice owns it: what the packs slice's walk lists, in the
 * slice's own vocabulary, so the application ring and the REST adapter never name the packs slice (ADR-0037 as
 * amended 2026-11-09). The walk lists an artifact once, at the earliest step that reaches it; the entries of an answer
 * are in URN order.
 *
 * <p>{@code held} describes the edge that listed the entry. A URN that a dangling edge lists in an early step keeps
 * {@code held} false even when a later step reaches it through a held edge, and the later steps expand from it, so an
 * entry below it names an entry that is not held as its {@code via}. A reader must not take {@code held} false to mean
 * that nothing is below the entry.
 *
 * @param urn the artifact the entry lists
 * @param step the step that listed it, its earliest
 * @param via its parent in the walk: the profile, or another entry of the answer
 * @param hops the length of the path from the profile, one more than the hops of {@code via}; at least 1
 * @param suggestion why the profile suggests it; present exactly for a {@link Step#SUGGESTS} entry
 * @param holder the configured identifier of the first pack of the closure that holds it; present exactly when
 *     {@code held} is true
 * @param held whether the place the listing edge leads to holds the artifact
 */
public record GovernanceEntry(
        Urn urn,
        Step step,
        Urn via,
        int hops,
        Optional<Suggestion> suggestion,
        Optional<PackIdentifier> holder,
        boolean held) {

    /** The step of the walk that lists an entry, with the relation its entries are listed by. */
    public enum Step {
        /** The ancestors: {@code specializes_from}, transitively. */
        LINEAGE("lineage", RelationType.SPECIALIZES_FROM),
        /** What the profile and its ancestors require, transitively. */
        REQUIRES("requires", RelationType.REQUIRES),
        /** What the profile and its ancestors suggest, as many hops as the depth says. */
        SUGGESTS("suggests", RelationType.SUGGESTS),
        /** What the suggested artifacts require, transitively. */
        REQUIRES_OF_SUGGESTED("requires_of_suggested", RelationType.REQUIRES),
        /** One hop of vocabulary from the profile and from everything the earlier steps list. */
        VOCABULARY("vocabulary", RelationType.VOCABULARY);

        private final String token;
        private final RelationType relation;

        Step(String token, RelationType relation) {
            this.token = token;
            this.relation = relation;
        }

        /**
         * The step's spelling on the wire.
         *
         * @return the lower-case token, for example {@code requires_of_suggested}
         */
        public String token() {
            return token;
        }

        /**
         * The relation of the edges the step follows.
         *
         * @return the relation; {@code requires} for both requiring steps
         */
        public RelationType relation() {
            return relation;
        }
    }

    /**
     * Why a profile suggests an artifact: the condition and the reason the {@code suggests} edge states. Either may be
     * absent, since an edge may state neither.
     *
     * @param when the condition under which the suggestion applies
     * @param reason why the profile suggests it
     */
    public record Suggestion(Optional<String> when, Optional<String> reason) {

        /**
         * Checks the fields.
         *
         * @param when the condition
         * @param reason the reason
         */
        public Suggestion {
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Checks the fields and the rules that tie them: an entry is a hop or more away, it carries a suggestion exactly
     * when a {@code suggests} edge listed it, and it names a holder exactly when it is held.
     *
     * @param urn the artifact
     * @param step the step that listed it
     * @param via its parent
     * @param hops the path length
     * @param suggestion the edge's suggestion
     * @param holder the holding pack
     * @param held whether the listing edge leads to a place that holds the artifact
     * @throws IllegalArgumentException when {@code hops} is below 1, a suggestion is on a step that is not
     *     {@code suggests} (or missing from one), or the holder disagrees with {@code held}
     */
    public GovernanceEntry {
        Objects.requireNonNull(urn, "urn");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(via, "via");
        Objects.requireNonNull(suggestion, "suggestion");
        Objects.requireNonNull(holder, "holder");
        if (hops < 1) {
            throw new IllegalArgumentException("an entry is at least one hop from the profile: " + hops);
        }
        if (suggestion.isPresent() != (step == Step.SUGGESTS)) {
            throw new IllegalArgumentException("a suggestion belongs to a suggests entry and to no other: " + urn);
        }
        if (holder.isPresent() != held) {
            throw new IllegalArgumentException(
                    "a held entry names its holder and an entry not held names none: " + urn);
        }
    }

    /**
     * The relation of the edge that listed the entry.
     *
     * @return the step's relation
     */
    public RelationType relation() {
        return step.relation();
    }
}
