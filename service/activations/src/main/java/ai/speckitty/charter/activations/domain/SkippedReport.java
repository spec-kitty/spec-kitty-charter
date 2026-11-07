package ai.speckitty.charter.activations.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a cascade left out, and why (ADR-0013 D4, the contract's {@code SkippedReport}). Every list is sorted by
 * {@link ArtifactRef}, so an answer is byte-stable across runs.
 *
 * @param skippedByScope the reached artifacts whose kind lies outside the cascade scope
 * @param skippedDeactivated the reached artifacts the walk stopped at because they are denied
 * @param unreachedBehindDeactivated the artifacts reachable only through a denied one
 * @param skippedShared for a cascading deactivate, the artifacts kept because an active artifact references them
 * @param skippedByDepth present only when a cascade ran with a {@code depth} (IC-21): the {@code suggests} targets one
 *     hop past the bound, so a client knows whether a deeper depth would reach more; empty when no depth was sent
 */
public record SkippedReport(
        List<ArtifactRef> skippedByScope,
        List<SkippedDeactivated> skippedDeactivated,
        List<ArtifactRef> unreachedBehindDeactivated,
        List<SharedSkip> skippedShared,
        Optional<List<ArtifactRef>> skippedByDepth) {

    private static final SkippedReport EMPTY = new SkippedReport(List.of(), List.of(), List.of(), List.of());

    /**
     * Checks and sorts the lists.
     *
     * @param skippedByScope the out-of-scope artifacts
     * @param skippedDeactivated the denied artifacts
     * @param unreachedBehindDeactivated the artifacts behind a denied one
     * @param skippedShared the shared artifacts kept
     * @param skippedByDepth the frontier past the bound, when a depth was sent
     */
    public SkippedReport {
        skippedByScope = sorted(skippedByScope, Comparator.naturalOrder());
        skippedDeactivated = sorted(skippedDeactivated, Comparator.comparing(SkippedDeactivated::ref));
        unreachedBehindDeactivated = sorted(unreachedBehindDeactivated, Comparator.naturalOrder());
        skippedShared = sorted(skippedShared, Comparator.comparing(SharedSkip::ref));
        skippedByDepth = Objects.requireNonNull(skippedByDepth, "skippedByDepth")
                .map(refs -> sorted(refs, Comparator.naturalOrder()));
    }

    /**
     * A report of a walk without a depth: it has no {@code skippedByDepth}.
     *
     * @param skippedByScope the out-of-scope artifacts
     * @param skippedDeactivated the denied artifacts
     * @param unreachedBehindDeactivated the artifacts behind a denied one
     * @param skippedShared the shared artifacts kept
     */
    public SkippedReport(
            List<ArtifactRef> skippedByScope,
            List<SkippedDeactivated> skippedDeactivated,
            List<ArtifactRef> unreachedBehindDeactivated,
            List<SharedSkip> skippedShared) {
        this(skippedByScope, skippedDeactivated, unreachedBehindDeactivated, skippedShared, Optional.empty());
    }

    /**
     * The same report, saying what lay one {@code suggests} hop past the bound of a walk that had a depth.
     *
     * @param frontier the {@code suggests} targets past the bound
     * @return this report with {@code skippedByDepth} present
     */
    public SkippedReport withSkippedByDepth(List<ArtifactRef> frontier) {
        return new SkippedReport(
                skippedByScope, skippedDeactivated, unreachedBehindDeactivated, skippedShared, Optional.of(frontier));
    }

    /**
     * The report with nothing skipped.
     *
     * @return the empty report
     */
    public static SkippedReport empty() {
        return EMPTY;
    }

    /**
     * An artifact the walk stopped at because it is denied (the contract's {@code SkippedDeactivated}).
     *
     * @param ref the artifact
     * @param viaRequires whether an edge that reached it is {@code requires}
     * @param requiredBy the sources of the edges that reached it, sorted
     */
    public record SkippedDeactivated(ArtifactRef ref, boolean viaRequires, List<ArtifactRef> requiredBy) {

        /**
         * Checks the fields and sorts {@code requiredBy}.
         *
         * @param ref the artifact
         * @param viaRequires whether a {@code requires} edge reached it
         * @param requiredBy the sources of the edges that reached it
         */
        public SkippedDeactivated {
            Objects.requireNonNull(ref, "ref");
            requiredBy = sorted(requiredBy, Comparator.naturalOrder());
        }
    }

    /**
     * An artifact a cascading deactivate kept, because an active artifact still references it (the contract's
     * {@code SharedSkip}).
     *
     * @param ref the artifact kept
     * @param referencedBy the active artifact that references it
     */
    public record SharedSkip(ArtifactRef ref, ArtifactRef referencedBy) {

        /**
         * Checks the fields.
         *
         * @param ref the artifact kept
         * @param referencedBy the active artifact
         */
        public SharedSkip {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(referencedBy, "referencedBy");
        }
    }

    private static <T> List<T> sorted(List<T> list, Comparator<? super T> order) {
        return Objects.requireNonNull(list, "list").stream().sorted(order).toList();
    }
}
