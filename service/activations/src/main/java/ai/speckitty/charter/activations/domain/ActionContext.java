package ai.speckitty.charter.activations.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the effective form of ADR-0018's walk reached for one action over the artifacts in force (IC-11, FR-019, rule E16
 * as amended by FR-008: the ownership rule), as the activations slice reads it: the packs slice walked the merged
 * closure, and this is the adapter's copy, as values only. Each entry and each candidate carries the part of the rule
 * that listed it ({@link AppliesThrough}, FR-009).
 * It names artifacts, never states: which of them is in force, why, and whether a candidate is a suggestion is judged
 * against the {@link EffectiveSet}, the one place that rule lives.
 *
 * @param missionTypeInForce whether the mission type of the step is in force; when it is not, nothing else is held
 * @param entries the artifacts in force the walk lists, in URN order
 * @param suggestionCandidates the artifacts out of force that an artifact the walk lists suggests and nothing hard
 *     reaches, in URN order; whether one is a suggestion is the effective set's to judge
 * @param stepEnhancedBy the enhancers in force of the step itself, in URN order
 */
public record ActionContext(
        boolean missionTypeInForce,
        List<Reached> entries,
        List<SuggestionCandidate> suggestionCandidates,
        List<ArtifactRef> stepEnhancedBy) {

    private static final ActionContext NOT_IN_FORCE = new ActionContext(false, List.of(), List.of(), List.of());

    /**
     * The part of the ownership rule that lists an artifact for the action (research R1 and R3, operator ruling of
     * 2026-11-12): the first that reaches it, in declaration order. It mirrors the walk's passes, which the packs slice
     * names; the adapter maps one to the other.
     */
    public enum AppliesThrough {
        /** The action's own walk: ADR-0018's five steps from the action's step. */
        ACTION("action"),
        /** The walk from the directives, tactics and paradigms in force that the action's mission type scopes. */
        MISSION_TYPE("mission_type"),
        /** The walk from the directives, tactics and paradigms in force that no action and no mission type scopes. */
        PROJECT("project");

        private final String token;

        AppliesThrough(String token) {
            this.token = token;
        }

        /**
         * The value the contract spells ({@code EffectiveContextArtifact.appliesThrough}).
         *
         * @return the token
         */
        public String token() {
            return token;
        }
    }

    /**
     * An artifact in force the walk lists.
     *
     * @param ref the artifact
     * @param enhancedBy the enhancers in force that enhance it, in URN order
     * @param appliesThrough the part of the rule that lists it
     */
    public record Reached(ArtifactRef ref, List<ArtifactRef> enhancedBy, AppliesThrough appliesThrough) {

        /**
         * Checks and copies the fields.
         *
         * @param ref the artifact
         * @param enhancedBy its enhancers in force
         * @param appliesThrough the part of the rule that lists it
         */
        public Reached {
            Objects.requireNonNull(ref, "ref");
            enhancedBy = List.copyOf(Objects.requireNonNull(enhancedBy, "enhancedBy"));
            Objects.requireNonNull(appliesThrough, "appliesThrough");
        }
    }

    /**
     * An artifact out of force that a listed artifact suggests: a candidate for a conditional entry.
     *
     * @param ref the suggested artifact
     * @param when the {@code when} of the {@code suggests} edge that reaches it, if it states one
     * @param reason the {@code reason} of that edge, if it states one
     * @param appliesThrough the part of the rule whose {@code suggests} edge reached it, which a suggestion carries
     */
    public record SuggestionCandidate(
            ArtifactRef ref, Optional<String> when, Optional<String> reason, AppliesThrough appliesThrough) {

        /**
         * Checks the fields.
         *
         * @param ref the suggested artifact
         * @param when the edge's condition
         * @param reason the edge's reason
         * @param appliesThrough the part of the rule that reached it
         */
        public SuggestionCandidate {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(appliesThrough, "appliesThrough");
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param missionTypeInForce whether the mission type is in force
     * @param entries the artifacts the walk lists
     * @param suggestionCandidates the candidates for a suggestion
     * @param stepEnhancedBy the enhancers in force of the step
     */
    public ActionContext {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        suggestionCandidates = List.copyOf(Objects.requireNonNull(suggestionCandidates, "suggestionCandidates"));
        stepEnhancedBy = List.copyOf(Objects.requireNonNull(stepEnhancedBy, "stepEnhancedBy"));
    }

    /**
     * The context of a mission type that is not in force: no entry, no candidate, no enhancer.
     *
     * @return the empty context
     */
    public static ActionContext notInForce() {
        return NOT_IN_FORCE;
    }
}
