package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.EnhancerWithoutTarget;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.Overridden;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.PinNotHeld;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.Edge;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.SkippedReport.SkippedDeactivated;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * @param satisfiedBy every artifact in force that overrides a required artifact that is out of force, by the level of
     *     its winning pack and then by URN (operator ruling of 2026-11-12 on #439); empty otherwise, and never an
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
            List<ArtifactRef> satisfiedBy,
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
            satisfiedBy = List.copyOf(Objects.requireNonNull(satisfiedBy, "satisfiedBy"));
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
    public record Summary(int active, int inactive, int excluded, int required, int locked, int skipped) {

        /**
         * The counts of a set.
         *
         * @param artifacts every artifact of the set
         * @param skipped the skipped report of the set
         * @return the counts; a required artifact counts when it is in force or an override of it satisfies it
         */
        static Summary of(List<EffectiveArtifact> artifacts, EffectiveSkippedReport skipped) {
            return new Summary(
                    count(artifacts, artifact -> artifact.state() == State.ACTIVE),
                    count(artifacts, artifact -> artifact.state() == State.INACTIVE),
                    count(artifacts, artifact -> artifact.state() == State.EXCLUDED),
                    count(
                            artifacts,
                            artifact -> artifact.required()
                                    && (artifact.state() == State.ACTIVE
                                            || !artifact.satisfiedBy().isEmpty())),
                    count(artifacts, artifact -> artifact.policy() == Policy.LOCKED),
                    skipped.count());
        }

        private static int count(List<EffectiveArtifact> artifacts, Predicate<EffectiveArtifact> in) {
            return (int) artifacts.stream().filter(in).count();
        }
    }

    /** The relations that reach an artifact for certain: the cascade's, but {@code suggests} and {@code enhances}, which pulls nothing in. */
    private static final Set<RelationType> HARD_RELATIONS = hardRelations();

    private static Set<RelationType> hardRelations() {
        Set<RelationType> hard = EnumSet.copyOf(CascadeRules.CASCADE_RELATIONS);
        hard.remove(RelationType.SUGGESTS);
        hard.remove(RelationType.ENHANCES);
        return Set.copyOf(hard);
    }

    /**
     * An artifact the action context lists as a suggestion (decision {@code 01M70CTZP7P2EHSCK2G0RET74B}): one the listing
     * gives a suggestion, judged over every artifact in force by {@link #suggestionsOf} (not selected, and no artifact in
     * force reaches it through a hard relation, inside the context or outside it), and that an artifact the context lists
     * reaches through {@code suggests}. The caller, which holds the walk, answers the second half; this answers the first,
     * from the one rule of the list, and carries the edge the walk reached the artifact by.
     *
     * @param ref the artifact a listed artifact suggests
     * @param when the {@code when} of the {@code suggests} edge that reaches it, if it states one
     * @param reason the {@code reason} of that edge, if it states one
     * @return the artifact as the list gives it, with the edge's {@code when} and {@code reason} as its suggestion; empty
     *     when the list gives it no suggestion
     */
    public Optional<EffectiveArtifact> suggested(ArtifactRef ref, Optional<String> when, Optional<String> reason) {
        return artifacts.stream()
                .filter(artifact ->
                        artifact.ref().equals(ref) && artifact.suggestion().isPresent())
                .findFirst()
                .map(artifact -> new EffectiveArtifact(
                        artifact.ref(),
                        artifact.packId(),
                        artifact.level(),
                        artifact.state(),
                        artifact.required(),
                        artifact.satisfiedBy(),
                        artifact.policy(),
                        artifact.reason(),
                        Optional.of(new Suggestion(when, reason))));
    }

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
     * What the document asks of the in-force resolution: its selection and deny entries of every kind, its language
     * filter (absent when it sets none) and its version pins.
     *
     * @param document the activation document, as stored
     * @return the query to put to {@link ai.speckitty.charter.activations.application.PacksSession#inForce}
     */
    public static InForceQuery queryOf(ActivationDocument document) {
        Map<String, String> pins = new LinkedHashMap<>();
        document.packs()
                .orElseGet(List::of)
                .forEach(pack -> pack.version().ifPresent(version -> pins.put(pack.packId(), version)));
        return new InForceQuery(
                ActivationChange.refs(document.selected()),
                ActivationChange.refs(document.deactivated()),
                document.languages().map(Set::copyOf),
                pins);
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
        Map<ArtifactRef, Suggestion> suggestions = suggestionsOf(merged.edges(), answered.states());
        List<EffectiveArtifact> artifacts = answered.states().values().stream()
                .flatMap(state -> artifactOf(state, merged, suggestions).stream())
                .sorted(Comparator.comparing(EffectiveArtifact::ref))
                .toList();
        EffectiveSkippedReport skipped = skippedOf(document, closure, answered);
        return new EffectiveSet(artifacts, skipped, Summary.of(artifacts, skipped));
    }

    private static Optional<EffectiveArtifact> artifactOf(
            ArtifactState state, MergedSnapshot merged, Map<ArtifactRef, Suggestion> suggestions) {
        return Optional.ofNullable(merged.winners().get(state.ref()))
                .map(winner -> new EffectiveArtifact(
                        state.ref(),
                        winner.packId(),
                        winner.level(),
                        state.state(),
                        state.required(),
                        state.satisfiedBy(),
                        winner.policy(),
                        state.reason(),
                        Optional.ofNullable(suggestions.get(state.ref()))));
    }

    /**
     * The artifacts reached only through {@code suggests}: an artifact the document does not select ({@code not_selected})
     * that an artifact in force suggests, and that no artifact in force reaches through a hard relation. It reads the edges among the winners one hop at a
     * time and walks nothing. Of several suggesters, the edge of the smallest wins.
     */
    private static Map<ArtifactRef, Suggestion> suggestionsOf(
            List<Edge> edges, Map<ArtifactRef, ArtifactState> states) {
        List<Edge> fromForce =
                edges.stream().filter(edge -> inForce(states, edge.source())).toList();
        Set<ArtifactRef> reachedHard = fromForce.stream()
                .filter(edge -> HARD_RELATIONS.contains(edge.relation()))
                .map(Edge::target)
                .collect(Collectors.toSet());
        Map<ArtifactRef, Suggestion> suggestions = new LinkedHashMap<>();
        fromForce.stream()
                .filter(edge -> edge.relation() == RelationType.SUGGESTS
                        && !reachedHard.contains(edge.target())
                        && notSelected(states, edge.target()))
                .sorted(Comparator.comparing(Edge::source))
                .forEach(edge -> suggestions.putIfAbsent(edge.target(), new Suggestion(edge.when(), edge.rationale())));
        return suggestions;
    }

    /** Whether the artifact is out of force for want of a selection: no other rule keeps it out (decision 01M6Y6VE6VZQ4MXD98N93JB3GJ). */
    private static boolean notSelected(Map<ArtifactRef, ArtifactState> states, ArtifactRef ref) {
        return Optional.ofNullable(states.get(ref))
                .filter(state -> notSelected(state.reason()))
                .isPresent();
    }

    /** The not-selected half of the one rule of a suggestion: no other rule keeps the artifact out. */
    private static boolean notSelected(Reason reason) {
        return reason == Reason.NOT_SELECTED;
    }

    private static boolean inForce(Map<ArtifactRef, ArtifactState> states, ArtifactRef ref) {
        return Optional.ofNullable(states.get(ref))
                .filter(state -> state.state() == State.ACTIVE)
                .isPresent();
    }

    private static EffectiveSkippedReport skippedOf(
            ActivationDocument document, ClosureSnapshot closure, InForceAnswer answered) {
        Collection<ArtifactState> states = answered.states().values();
        return new EffectiveSkippedReport(
                answered.staleIds(),
                answered.denyNotApplied(),
                inBucket(states, Bucket.ENHANCED_TARGET_NOT_IN_FORCE)
                        .map(state -> new EnhancerWithoutTarget(
                                state.ref(), answered.enhancedTargets().getOrDefault(state.ref(), List.of())))
                        .toList(),
                answered.overrideNotApplied(),
                answered.requiredNotHeld(),
                answered.requiredNotInForce(),
                StaleIds.drop(document, closure).droppedPins().stream()
                        .flatMap(pin -> pin.version().map(version -> new PinNotHeld(pin.packId(), version)).stream())
                        .toList(),
                refsIn(states, Bucket.LANGUAGE_FILTERED),
                refsIn(states, Bucket.MISSION_TYPE_NOT_IN_FORCE),
                inBucket(states, Bucket.DEACTIVATED)
                        .map(state -> new SkippedDeactivated(state.ref(), false, List.of()))
                        .toList(),
                inBucket(states, Bucket.OVERRIDDEN)
                        .map(state -> new Overridden(state.ref(), state.overriddenBy()))
                        .toList());
    }

    private static Stream<ArtifactState> inBucket(Collection<ArtifactState> states, Bucket bucket) {
        return states.stream()
                .filter(state -> state.bucket().filter(bucket::equals).isPresent());
    }

    private static List<ArtifactRef> refsIn(Collection<ArtifactState> states, Bucket bucket) {
        return inBucket(states, bucket).map(ArtifactState::ref).toList();
    }
}
