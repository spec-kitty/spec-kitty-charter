package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What an activation puts in force, and why the rest is not (FR-013, FR-017): one {@link EffectiveArtifact} per winning
 * artifact of the merged closure, the {@link EffectiveSkippedReport} of what the document names and the closure does not
 * put in force, and the {@link Summary} that counts the whole set.
 *
 * <p>It is a mapping over what the packs slice answered through the port: the precedence of the states (rule E9), the
 * override chains, the floor and the enhancers are decided by {@code packs.domain.InForceResolution}, and this record
 * keeps only the answer, one state and one reason per artifact and one bucket per skipped one. It never walks (C-002).
 *
 * @param artifacts every artifact of the merged closure with its state, sorted by kind and then by id in code-point
 *     order
 * @param skipped what the document names that is not in force, and the rules that were not applied
 * @param summary the counts of the whole set
 */
public record EffectiveSet(List<EffectiveArtifact> artifacts, EffectiveSkippedReport skipped, Summary summary) {

    /** Whether an artifact is in force, out of force, or denied (the contract's {@code EffectiveArtifactState}). */
    public enum State {
        /** In force. */
        ACTIVE,
        /** Out of force without a deny entry. */
        INACTIVE,
        /** Out of force because the document denies it. */
        EXCLUDED;

        /**
         * The contract's spelling.
         *
         * @return the lower-case token
         */
        public String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The rule that decided an artifact's state (the contract's {@code EffectiveArtifactReason}). */
    public enum Reason {
        /** In force because the document selects it. */
        SELECTED,
        /** In force because a governing preset requires it. */
        REQUIRED,
        /** In force because a pack locks it. */
        LOCKED,
        /** Out of force because the document denies it. */
        DEACTIVATED,
        /** Out of force because the document does not select it. */
        NOT_SELECTED,
        /** Out of force because the language filter excludes it. */
        LANGUAGE_FILTERED,
        /** Out of force because an artifact in force overrides it. */
        OVERRIDDEN,
        /** Out of force because the mission type it is bound to is not in force. */
        MISSION_TYPE_NOT_IN_FORCE,
        /** Out of force because it is an enhancer none of whose targets is in force. */
        ENHANCED_TARGET_NOT_IN_FORCE;

        /**
         * The contract's spelling.
         *
         * @return the lower-case token
         */
        public String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The mutability level of the version in force (the contract's {@code ArtifactPolicy}). */
    public enum Policy {
        /** What a later pack may replace, enhance and deactivate. */
        OPEN,
        /** What a later pack may enhance, and no more. */
        EXTENDABLE,
        /** What no later pack may change. */
        LOCKED;

        /**
         * The contract's spelling.
         *
         * @return the lower-case token
         */
        public String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The bucket of the skipped report an artifact the document names falls into. */
    public enum Bucket {
        /** Bound to a mission type that is not in force. */
        MISSION_TYPE_NOT_IN_FORCE,
        /** An enhancer without a target in force. */
        ENHANCED_TARGET_NOT_IN_FORCE,
        /** Excluded by the language filter. */
        LANGUAGE_FILTERED,
        /** Denied by the document. */
        DEACTIVATED,
        /** Overridden by an artifact in force. */
        OVERRIDDEN
    }

    /**
     * Why an artifact is listed as a suggestion (the contract's {@code EffectiveArtifactSuggestion}): the {@code when}
     * and the {@code rationale}, as {@code reason}, of the {@code suggests} edge that reaches it.
     *
     * @param when the condition the edge states, if any
     * @param reason the reason the edge gives, if any
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
     * One artifact of the merged closure under an activation.
     *
     * @param ref the artifact
     * @param packId the configured id of the pack that holds the version in force
     * @param level the level of that pack
     * @param state in force, out of force, or denied
     * @param required whether a governing preset requires it or a pack locks it; it does not imply {@code active}
     * @param satisfiedBy the artifact in force that overrides a required artifact that is out of force; never an
     *     enhancer
     * @param policy the mutability level of the version in force
     * @param reason the rule that decided the state
     * @param suggestion present only for an artifact reached only through a {@code suggests} edge of an artifact in
     *     force
     */
    public record EffectiveArtifact(
            ArtifactRef ref,
            String packId,
            PackLevel level,
            State state,
            boolean required,
            Optional<ArtifactRef> satisfiedBy,
            Policy policy,
            Reason reason,
            Optional<Suggestion> suggestion) {

        /**
         * Checks the fields.
         *
         * @param ref the artifact
         * @param packId the holding pack
         * @param level its level
         * @param state the state
         * @param required whether it is required or locked
         * @param satisfiedBy what satisfies its requirement
         * @param policy the mutability level
         * @param reason the rule that decided
         * @param suggestion the edge that suggests it
         */
        public EffectiveArtifact {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(satisfiedBy, "satisfiedBy");
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(suggestion, "suggestion");
        }

        /**
         * The artifact's kind.
         *
         * @return the kind
         */
        public ArtifactKind kind() {
            return ref.kind();
        }

        /**
         * The artifact's declared id.
         *
         * @return the id
         */
        public String artifactId() {
            return ref.id();
        }
    }

    /**
     * The counts of the whole set, not of a page (the contract's {@code ActivationSummary}).
     *
     * @param active the artifacts in force
     * @param inactive the artifacts out of force without a deny entry
     * @param excluded the artifacts the document denies
     * @param required the required artifacts, in force or satisfied by an override of them
     * @param locked the artifacts a pack locks
     * @param skipped the entries across the buckets of the skipped report
     */
    public record Summary(int active, int inactive, int excluded, int required, int locked, int skipped) {}

    /**
     * Checks the fields.
     *
     * @param artifacts the artifacts
     * @param skipped the skipped report
     * @param summary the counts
     */
    public EffectiveSet {
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        Objects.requireNonNull(skipped, "skipped");
        Objects.requireNonNull(summary, "summary");
    }

    /**
     * The effective set of a document.
     *
     * @param document the activation document, as stored
     * @param closure the closure the document names, as the port answered it
     * @param merged the closure under the layer merge
     * @param answered what the packs slice put in force for the document's request
     * @return the set
     */
    public static EffectiveSet of(
            ActivationDocument document, ClosureSnapshot closure, MergedSnapshot merged, InForceAnswer answered) {
        return new EffectiveSet(List.of(), EffectiveSkippedReport.empty(), new Summary(0, 0, 0, 0, 0, 0));
    }
}
