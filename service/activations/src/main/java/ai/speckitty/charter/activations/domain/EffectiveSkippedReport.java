package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.SkippedReport.SkippedDeactivated;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What an activation leaves out of force at read, and the rules it did not apply (the contract's {@code SkippedReport}
 * of the two effective reads, data-model.md): each artifact the document names that is not in force is in the one bucket
 * of its first deciding rule, so a client sees why. {@code denyNotApplied} and {@code overrideNotApplied} list rules that
 * were not applied, not artifacts that are out of force. Every list is sorted by {@link ArtifactRef} (then by the
 * entry's other fields), so an answer is byte-stable across runs.
 *
 * <p>At read, the three buckets of a write ({@code skippedByScope}, {@code unreachedBehindDeactivated} and
 * {@code skippedShared}) are empty: {@link #base()} answers the report in the shape a write's report has, for the
 * serving layer to add the buckets of this record to.
 *
 * @param staleIds the ids the document selects or denies that the merged closure does not hold
 * @param denyNotApplied the deny entries not applied: on a locked artifact, or on a required one nothing else satisfies
 * @param enhancedTargetNotInForce the selected enhancers none of whose targets is in force, with their targets
 * @param overrideNotApplied the overrides not applied because the target is locked; both ends are in force
 * @param requiredNotHeld the must-have items of a governing preset that no pack of the closure holds
 * @param requiredNotInForce the required artifacts that cannot stand in force
 * @param pinNotHeld the version pins of the document that no pack of the closure satisfies
 * @param languageFiltered the artifacts the document names that the language filter excludes
 * @param missionTypeNotInForce the artifacts the document names that are bound to a mission type not in force
 * @param skippedDeactivated the artifacts the document denies; at read {@code viaRequires} is {@code false} and
 *     {@code requiredBy} is empty
 * @param overridden the artifacts the document selects that an artifact in force hides
 */
public record EffectiveSkippedReport(
        List<ArtifactRef> staleIds,
        List<DenyNotApplied> denyNotApplied,
        List<EnhancerWithoutTarget> enhancedTargetNotInForce,
        List<OverrideNotApplied> overrideNotApplied,
        List<ArtifactRef> requiredNotHeld,
        List<RequiredNotInForce> requiredNotInForce,
        List<PinNotHeld> pinNotHeld,
        List<ArtifactRef> languageFiltered,
        List<ArtifactRef> missionTypeNotInForce,
        List<SkippedDeactivated> skippedDeactivated,
        List<Overridden> overridden) {

    private static final EffectiveSkippedReport EMPTY = new EffectiveSkippedReport(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of());

    /** Why a deny entry was not applied. */
    public enum DenyReason {
        /** The artifact is required and nothing else satisfies the requirement. */
        REQUIRED,
        /** A pack locks the artifact. */
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

    /**
     * A deny entry that was not applied.
     *
     * @param ref the artifact the entry names
     * @param reason why it was not applied
     */
    public record DenyNotApplied(ArtifactRef ref, DenyReason reason) {

        /**
         * Checks the fields.
         *
         * @param ref the artifact
         * @param reason why
         */
        public DenyNotApplied {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * An enhancer none of whose targets is in force.
     *
     * @param ref the enhancer
     * @param targets the nodes it enhances, sorted; a step of a mission type is a node, which no artifact is
     */
    public record EnhancerWithoutTarget(ArtifactRef ref, List<NodeRef> targets) {

        /**
         * Checks the fields and sorts the targets.
         *
         * @param ref the enhancer
         * @param targets its targets
         */
        public EnhancerWithoutTarget {
            Objects.requireNonNull(ref, "ref");
            targets = sorted(targets, Comparator.naturalOrder());
        }
    }

    /**
     * An override that was not applied because its target is locked.
     *
     * @param ref the artifact that stays
     * @param overriddenBy the artifact in force whose override names it
     */
    public record OverrideNotApplied(ArtifactRef ref, ArtifactRef overriddenBy) {

        /**
         * Checks the fields.
         *
         * @param ref the artifact that stays
         * @param overriddenBy the overrider
         */
        public OverrideNotApplied {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(overriddenBy, "overriddenBy");
        }
    }

    /**
     * A required artifact that cannot stand in force.
     *
     * @param ref the artifact
     * @param reason {@link Reason#ENHANCED_TARGET_NOT_IN_FORCE} or {@link Reason#MISSION_TYPE_NOT_IN_FORCE}
     */
    public record RequiredNotInForce(ArtifactRef ref, Reason reason) {

        /**
         * Checks the fields.
         *
         * @param ref the artifact
         * @param reason why it cannot stand in force
         */
        public RequiredNotInForce {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * A version pin no pack of the closure satisfies (the contract's {@code PackPin}).
     *
     * @param packId the pinned pack's configured id
     * @param version the version the document pins
     */
    public record PinNotHeld(String packId, String version) {

        /**
         * Checks the fields.
         *
         * @param packId the pack
         * @param version the pinned version
         */
        public PinNotHeld {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(version, "version");
        }
    }

    /**
     * An artifact an artifact in force hides.
     *
     * @param ref the hidden artifact
     * @param overriddenBy the artifact in force that overrides it, directly or through a chain
     */
    public record Overridden(ArtifactRef ref, ArtifactRef overriddenBy) {

        /**
         * Checks the fields.
         *
         * @param ref the hidden artifact
         * @param overriddenBy its overrider
         */
        public Overridden {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(overriddenBy, "overriddenBy");
        }
    }

    /**
     * Checks the fields and sorts every list.
     *
     * @param staleIds the ids the closure does not hold
     * @param denyNotApplied the deny entries not applied
     * @param enhancedTargetNotInForce the enhancers without a target in force
     * @param overrideNotApplied the overrides not applied
     * @param requiredNotHeld the must-have items nobody holds
     * @param requiredNotInForce the required artifacts that cannot stand
     * @param pinNotHeld the pins no pack satisfies
     * @param languageFiltered the artifacts the language filter excludes
     * @param missionTypeNotInForce the artifacts bound to a mission type not in force
     * @param skippedDeactivated the denied artifacts
     * @param overridden the hidden artifacts
     */
    public EffectiveSkippedReport {
        staleIds = sorted(staleIds, Comparator.naturalOrder());
        denyNotApplied =
                sorted(denyNotApplied, Comparator.comparing(DenyNotApplied::ref).thenComparing(DenyNotApplied::reason));
        enhancedTargetNotInForce = sorted(enhancedTargetNotInForce, Comparator.comparing(EnhancerWithoutTarget::ref));
        overrideNotApplied = sorted(
                overrideNotApplied,
                Comparator.comparing(OverrideNotApplied::ref).thenComparing(OverrideNotApplied::overriddenBy));
        requiredNotHeld = sorted(requiredNotHeld, Comparator.naturalOrder());
        requiredNotInForce = sorted(requiredNotInForce, Comparator.comparing(RequiredNotInForce::ref));
        pinNotHeld = sorted(
                pinNotHeld,
                Comparator.comparing(PinNotHeld::packId, ActivationDocument.CODE_POINT_ORDER)
                        .thenComparing(PinNotHeld::version, ActivationDocument.CODE_POINT_ORDER));
        languageFiltered = sorted(languageFiltered, Comparator.naturalOrder());
        missionTypeNotInForce = sorted(missionTypeNotInForce, Comparator.naturalOrder());
        skippedDeactivated = sorted(skippedDeactivated, Comparator.comparing(SkippedDeactivated::ref));
        overridden = sorted(overridden, Comparator.comparing(Overridden::ref));
    }

    /**
     * The report with nothing skipped.
     *
     * @return the empty report
     */
    public static EffectiveSkippedReport empty() {
        return EMPTY;
    }

    /**
     * The report in the shape a write's {@link SkippedReport} has: the denied artifacts, and the three buckets a write
     * fills left empty, as they are at read.
     *
     * @return the report the serving layer adds this record's buckets to
     */
    public SkippedReport base() {
        return new SkippedReport(List.of(), skippedDeactivated, List.of(), List.of());
    }

    /**
     * The number of entries across every bucket: the summary's {@code skipped}.
     *
     * @return the count
     */
    public int count() {
        return staleIds.size()
                + denyNotApplied.size()
                + enhancedTargetNotInForce.size()
                + overrideNotApplied.size()
                + requiredNotHeld.size()
                + requiredNotInForce.size()
                + pinNotHeld.size()
                + languageFiltered.size()
                + missionTypeNotInForce.size()
                + skippedDeactivated.size()
                + overridden.size();
    }

    private static <T> List<T> sorted(List<T> list, Comparator<? super T> order) {
        return Objects.requireNonNull(list, "list").stream().sorted(order).toList();
    }
}
