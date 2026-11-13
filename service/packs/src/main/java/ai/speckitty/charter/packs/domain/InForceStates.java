package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the in-force resolution answers (rules E9 to E14): one state and one reason for every winning artifact of the
 * merged closure, and the rules that were not applied. It holds plain values only, so the {@code activations} slice
 * maps it without naming a {@code packs} type beyond the port.
 *
 * @param states one entry per winning URN, in the order the merged closure holds them
 * @param staleIds the ids the request selects or denies that the merged closure does not hold, in URN order (E1)
 * @param denyNotApplied the deny entries that were not applied: on a locked artifact, or on a required one that nothing
 *     else satisfies (ADR-0013 D2), in URN order
 * @param overrideNotApplied the {@code overrides} edges of an artifact in force that were not applied because the
 *     target is not {@code open} (C27): each target once, in URN order, with every overrider in force whose edge names it
 * @param requiredNotHeld the must-have items that no pack of the closure holds (A10, C31), in URN order
 * @param requiredNotInForce the required or locked artifacts that cannot stand in force (the enhancer rule and the
 *     binding rule win over the floor), in URN order
 * @param enhancedTargets for each enhancer that is not in force for want of a target, the targets it enhances, in URN
 *     order
 */
public record InForceStates(
        Map<Urn, ArtifactState> states,
        List<Urn> staleIds,
        List<DenyNotApplied> denyNotApplied,
        List<OverrideNotApplied> overrideNotApplied,
        List<Urn> requiredNotHeld,
        List<RequiredNotInForce> requiredNotInForce,
        Map<Urn, List<Urn>> enhancedTargets) {

    /** Whether an artifact is in force, out of force, or denied (rule E9). */
    public enum State {
        /** In force. */
        ACTIVE,
        /** Out of force without a deny entry. */
        INACTIVE,
        /** Out of force because the document denies it. */
        EXCLUDED
    }

    /** The first rule of the precedence table that decides an artifact's state (rule E9). */
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
        ENHANCED_TARGET_NOT_IN_FORCE
    }

    /** Why the pruning removed a required or locked artifact from force (rule E6): the two reasons it gives, no other. */
    public enum PruningReason {
        /** Out of force because the mission type it is bound to is not in force. */
        MISSION_TYPE_NOT_IN_FORCE,
        /** Out of force because it is an enhancer none of whose targets is in force. */
        ENHANCED_TARGET_NOT_IN_FORCE
    }

    /** The bucket of the skipped report an artifact the request names falls into (E14). */
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

    /** Why a deny entry was not applied. */
    public enum DenyReason {
        /** The artifact is required and nothing else satisfies the requirement. */
        REQUIRED,
        /** A pack locks the artifact. */
        LOCKED
    }

    /**
     * One artifact's state.
     *
     * @param urn the artifact
     * @param state in force, out of force, or denied
     * @param reason the first rule that decided the state
     * @param required whether a governing preset requires it or a pack locks it; it no longer implies {@code ACTIVE}
     * @param satisfiedBy every artifact in force that overrides it, directly or through a chain, for a required artifact
     *     that is out of force, by the level of its winning pack (built-in, org, project) and then by URN in code point
     *     order (operator ruling of 2026-11-12 on #439, option (b)); empty otherwise. Never an enhancer
     * @param overriddenBy every artifact in force that hides it, directly or through a chain, in the same order; non-empty
     *     exactly when {@code reason} is {@link Reason#OVERRIDDEN}
     * @param bucket the skipped-report bucket, present only for an artifact the request selects or denies that is not in
     *     force
     */
    public record ArtifactState(
            Urn urn,
            State state,
            Reason reason,
            boolean required,
            List<Urn> satisfiedBy,
            List<Urn> overriddenBy,
            Optional<Bucket> bucket) {

        /**
         * Checks the fields.
         *
         * @param urn the artifact
         * @param state the state
         * @param reason the reason
         * @param required whether it is on the floor or locked
         * @param satisfiedBy the artifacts in force that satisfy its requirement
         * @param overriddenBy the artifacts in force that hide it
         * @param bucket its skipped-report bucket
         */
        public ArtifactState {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reason, "reason");
            satisfiedBy = List.copyOf(Objects.requireNonNull(satisfiedBy, "satisfiedBy"));
            overriddenBy = List.copyOf(Objects.requireNonNull(overriddenBy, "overriddenBy"));
            Objects.requireNonNull(bucket, "bucket");
        }

        /**
         * Whether the artifact is in force.
         *
         * @return {@code true} when its state is {@link State#ACTIVE}
         */
        public boolean active() {
            return state == State.ACTIVE;
        }
    }

    /**
     * A deny entry that was not applied.
     *
     * @param urn the artifact the entry names
     * @param reason why it was not applied
     */
    public record DenyNotApplied(Urn urn, DenyReason reason) {

        /**
         * Checks the fields.
         *
         * @param urn the artifact
         * @param reason why
         */
        public DenyNotApplied {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The overrides of one target that were not applied.
     *
     * @param target the artifact that stays, because it is not {@code open}
     * @param overriddenBy every artifact in force whose {@code overrides} edge names it, by the level of its winning pack
     *     and then by URN (#439); never empty
     */
    public record OverrideNotApplied(Urn target, List<Urn> overriddenBy) {

        /**
         * Checks and copies the fields.
         *
         * @param target the artifact that stays
         * @param overriddenBy the overriders
         * @throws IllegalArgumentException when no overrider is named
         */
        public OverrideNotApplied {
            Objects.requireNonNull(target, "target");
            overriddenBy = List.copyOf(Objects.requireNonNull(overriddenBy, "overriddenBy"));
            if (overriddenBy.isEmpty()) {
                throw new IllegalArgumentException("an override not applied names its overrider");
            }
        }
    }

    /**
     * A required or locked artifact that cannot stand in force.
     *
     * @param urn the artifact
     * @param reason the pruning reason: the enhancer rule or the binding rule
     */
    public record RequiredNotInForce(Urn urn, PruningReason reason) {

        /**
         * Checks the fields.
         *
         * @param urn the artifact
         * @param reason why it cannot stand in force
         */
        public RequiredNotInForce {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param states one entry per winning URN
     * @param staleIds the ids the merged closure does not hold
     * @param denyNotApplied the deny entries not applied
     * @param overrideNotApplied the overrides not applied
     * @param requiredNotHeld the must-have items nobody holds
     * @param requiredNotInForce the required artifacts that cannot stand
     * @param enhancedTargets the targets of each enhancer out of force for want of one
     */
    public InForceStates {
        states = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(states, "states")));
        staleIds = List.copyOf(Objects.requireNonNull(staleIds, "staleIds"));
        denyNotApplied = List.copyOf(Objects.requireNonNull(denyNotApplied, "denyNotApplied"));
        overrideNotApplied = List.copyOf(Objects.requireNonNull(overrideNotApplied, "overrideNotApplied"));
        requiredNotHeld = List.copyOf(Objects.requireNonNull(requiredNotHeld, "requiredNotHeld"));
        requiredNotInForce = List.copyOf(Objects.requireNonNull(requiredNotInForce, "requiredNotInForce"));
        Map<Urn, List<Urn>> targets = new LinkedHashMap<>();
        Objects.requireNonNull(enhancedTargets, "enhancedTargets")
                .forEach((urn, list) -> targets.put(urn, List.copyOf(list)));
        enhancedTargets = Collections.unmodifiableMap(targets);
    }

    /**
     * The state of one artifact.
     *
     * @param urn the artifact
     * @return its state; empty for a URN the merged closure does not hold
     */
    public Optional<ArtifactState> stateOf(Urn urn) {
        return Optional.ofNullable(states.get(Objects.requireNonNull(urn, "urn")));
    }
}
