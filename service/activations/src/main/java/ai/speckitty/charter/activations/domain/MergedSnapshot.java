package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.EffectiveSet.Bucket;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.OverrideNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.RequiredNotInForce;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The closure under the layer merge, as the activations slice reads it: the adapter's copy of what the packs slice
 * merged, as values only (IC-09, C-002). No type of {@code packs} crosses here, and nothing here walks: the merge, the
 * override chains and the in-force resolution are the packs slice's, and the domain only reads their answers.
 *
 * <p>Only artifacts of an activatable kind are held (a step of a mission type, a template or an asset is no artifact of
 * an activation), each once, as the version in force.
 *
 * @param winners each artifact with the version in force, in the order the closure first holds them
 * @param languages each winner's declared {@code applies_to_languages}, in declared order; a winner that declares none
 *     is absent
 * @param edges the typed edges among the winners, after the merge re-homed them through the winners
 * @param layerOverrides every version a later level replaced, in merge order
 * @param bindings the mission type each winner that is bound to one is bound to (a step contract to the type its
 *     {@code mission} names), as the pack that holds the version in force declares it; a winner bound to none is absent
 */
public record MergedSnapshot(
        Map<ArtifactRef, Winner> winners,
        Map<ArtifactRef, Set<String>> languages,
        List<Edge> edges,
        List<LayerOverride> layerOverrides,
        Map<ArtifactRef, ArtifactRef> bindings) {

    private static final MergedSnapshot EMPTY = new MergedSnapshot(Map.of(), Map.of(), List.of(), List.of());

    /**
     * The version of an artifact in force.
     *
     * @param packId the configured id of the pack that holds it
     * @param level the level that pack is configured at
     * @param policy the artifact's mutability level along the closure
     */
    public record Winner(String packId, PackLevel level, Policy policy) {

        /**
         * Checks the fields.
         *
         * @param packId the holding pack
         * @param level its level
         * @param policy the mutability level
         */
        public Winner {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(policy, "policy");
        }
    }

    /**
     * A typed edge between two winners.
     *
     * @param source the artifact the edge starts at
     * @param target the artifact it ends at
     * @param relation what the edge means
     * @param when the condition the edge states, if any
     * @param rationale the reason the edge gives, if any
     */
    public record Edge(
            ArtifactRef source,
            ArtifactRef target,
            RelationType relation,
            Optional<String> when,
            Optional<String> rationale) {

        /**
         * Checks the fields.
         *
         * @param source the start
         * @param target the end
         * @param relation the relation
         * @param when the condition
         * @param rationale the reason
         */
        public Edge {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    /**
     * One version of a node that a later level replaced.
     *
     * @param ref the node: an artifact, or a step of a mission type held by the closure
     * @param winner the version in force
     * @param loser the version it replaced
     */
    public record LayerOverride(NodeRef ref, Side winner, Side loser) {

        /**
         * One version of the artifact.
         *
         * @param packId the configured id of the holding pack
         * @param level the level the pack is configured at
         */
        public record Side(String packId, PackLevel level) {

            /**
             * Checks the fields.
             *
             * @param packId the holding pack
             * @param level its level
             */
            public Side {
                Objects.requireNonNull(packId, "packId");
                Objects.requireNonNull(level, "level");
            }
        }

        /**
         * Checks the fields.
         *
         * @param ref the node
         * @param winner the version in force
         * @param loser the version it replaced
         */
        public LayerOverride {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(loser, "loser");
        }
    }

    /**
     * What an activation document asks of the in-force resolution: its selection, its deny entries, its language
     * filter and its version pins, as plain values. The pins are carried and never consulted by the resolution (they
     * decide which packs compose); {@link EffectiveSet} judges them against the closure's pack versions.
     *
     * @param selected the ids the document selects, of every kind
     * @param deactivated the ids the document denies, of every kind
     * @param languages the language filter; empty when the document sets none, which is not a filter naming no language
     * @param pins the version pins of the document, by configured pack id
     */
    public record InForceQuery(
            Set<ArtifactRef> selected,
            Set<ArtifactRef> deactivated,
            Optional<Set<String>> languages,
            Map<String, String> pins) {

        /**
         * Checks and copies the fields.
         *
         * @param selected the selected ids
         * @param deactivated the denied ids
         * @param languages the language filter, if any
         * @param pins the pins by pack id
         */
        public InForceQuery {
            selected = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(selected, "selected")));
            deactivated = Collections.unmodifiableSet(
                    new LinkedHashSet<>(Objects.requireNonNull(deactivated, "deactivated")));
            languages = Objects.requireNonNull(languages, "languages")
                    .map(set -> Collections.unmodifiableSet(new LinkedHashSet<>(set)));
            pins = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(pins, "pins")));
        }
    }

    /**
     * One artifact's state, as the in-force resolution decided it (the first row of the precedence table that holds).
     *
     * @param ref the artifact
     * @param state in force, out of force, or denied
     * @param reason the first rule that decided the state
     * @param required whether a governing preset requires it or a pack locks it
     * @param satisfiedBy the artifact in force that satisfies the requirement of a required artifact that is out of force
     * @param overriddenBy the artifact in force that hides it; present exactly for the reason {@link Reason#OVERRIDDEN}
     * @param bucket the skipped-report bucket, present only for an artifact the document names that is not in force
     */
    public record ArtifactState(
            ArtifactRef ref,
            State state,
            Reason reason,
            boolean required,
            Optional<ArtifactRef> satisfiedBy,
            Optional<ArtifactRef> overriddenBy,
            Optional<Bucket> bucket) {

        /**
         * Checks the fields.
         *
         * @param ref the artifact
         * @param state the state
         * @param reason the reason
         * @param required whether it is required or locked
         * @param satisfiedBy what satisfies its requirement
         * @param overriddenBy what hides it
         * @param bucket its bucket
         */
        public ArtifactState {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(satisfiedBy, "satisfiedBy");
            Objects.requireNonNull(overriddenBy, "overriddenBy");
            Objects.requireNonNull(bucket, "bucket");
        }
    }

    /**
     * What the in-force resolution answers: one state per winning artifact, and the rules that were not applied. The
     * entries are the skipped report's own, since the resolution already decided them.
     *
     * @param states one entry per winning artifact
     * @param staleIds the ids the request names that the merged closure does not hold
     * @param denyNotApplied the deny entries not applied
     * @param overrideNotApplied the overrides not applied because the target is locked
     * @param requiredNotHeld the must-have items no pack holds
     * @param requiredNotInForce the required artifacts that cannot stand in force
     * @param enhancedTargets for each enhancer out of force for want of a target, the targets it enhances
     */
    public record InForceAnswer(
            Map<ArtifactRef, ArtifactState> states,
            List<ArtifactRef> staleIds,
            List<DenyNotApplied> denyNotApplied,
            List<OverrideNotApplied> overrideNotApplied,
            List<ArtifactRef> requiredNotHeld,
            List<RequiredNotInForce> requiredNotInForce,
            Map<ArtifactRef, List<NodeRef>> enhancedTargets) {

        private static final InForceAnswer EMPTY =
                new InForceAnswer(Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());

        /**
         * Checks and copies the fields.
         *
         * @param states the states
         * @param staleIds the stale ids
         * @param denyNotApplied the deny entries not applied
         * @param overrideNotApplied the overrides not applied
         * @param requiredNotHeld the must-have items nobody holds
         * @param requiredNotInForce the required artifacts that cannot stand
         * @param enhancedTargets the targets of each enhancer out of force
         */
        public InForceAnswer {
            states = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(states, "states")));
            staleIds = List.copyOf(Objects.requireNonNull(staleIds, "staleIds"));
            denyNotApplied = List.copyOf(Objects.requireNonNull(denyNotApplied, "denyNotApplied"));
            overrideNotApplied = List.copyOf(Objects.requireNonNull(overrideNotApplied, "overrideNotApplied"));
            requiredNotHeld = List.copyOf(Objects.requireNonNull(requiredNotHeld, "requiredNotHeld"));
            requiredNotInForce = List.copyOf(Objects.requireNonNull(requiredNotInForce, "requiredNotInForce"));
            Map<ArtifactRef, List<NodeRef>> targets = new LinkedHashMap<>();
            Objects.requireNonNull(enhancedTargets, "enhancedTargets")
                    .forEach((ref, list) -> targets.put(ref, List.copyOf(list)));
            enhancedTargets = Collections.unmodifiableMap(targets);
        }

        /**
         * The answer for a closure that holds nothing.
         *
         * @return the empty answer
         */
        public static InForceAnswer empty() {
            return EMPTY;
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param winners each artifact with its winner
     * @param languages the languages of each winner
     * @param edges the typed edges among the winners
     * @param layerOverrides every replaced version
     * @param bindings the mission type each bound winner is bound to
     */
    public MergedSnapshot {
        winners = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(winners, "winners")));
        Map<ArtifactRef, Set<String>> declared = new LinkedHashMap<>();
        Objects.requireNonNull(languages, "languages")
                .forEach((ref, set) -> declared.put(ref, Collections.unmodifiableSet(new LinkedHashSet<>(set))));
        languages = Collections.unmodifiableMap(declared);
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        layerOverrides = List.copyOf(Objects.requireNonNull(layerOverrides, "layerOverrides"));
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(bindings, "bindings")));
    }

    /**
     * A snapshot whose winners are bound to no mission type.
     *
     * @param winners each artifact with its winner
     * @param languages the languages of each winner
     * @param edges the typed edges among the winners
     * @param layerOverrides every replaced version
     */
    public MergedSnapshot(
            Map<ArtifactRef, Winner> winners,
            Map<ArtifactRef, Set<String>> languages,
            List<Edge> edges,
            List<LayerOverride> layerOverrides) {
        this(winners, languages, edges, layerOverrides, Map.of());
    }

    /**
     * The snapshot of a closure that holds nothing.
     *
     * @return the empty snapshot
     */
    public static MergedSnapshot empty() {
        return EMPTY;
    }
}
