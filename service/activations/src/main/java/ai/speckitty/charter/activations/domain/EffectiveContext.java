package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.ActionContext.AppliesThrough;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What {@code getEffectiveActionContext} answers (IC-11, FR-019, rule E16 as amended by FR-008), assembled from the
 * {@link EffectiveSet} and what the walk reached under the ownership rule: the artifacts that apply to the action, sorted
 * by URN, each as the effective list gives it with the part of the rule that lists it (FR-009); the enhancers in force of
 * each node; and the skipped report.
 *
 * @param artifacts the listed artifacts in URN order: those in force, then each suggestion among them by its URN
 * @param enhancements the enhancers in force of the step and of each listed artifact in force
 * @param skipped the skipped report, whose {@code missionTypeNotInForce} is the mission type itself when it is not in force
 */
public record EffectiveContext(List<Item> artifacts, List<Enhancement> enhancements, EffectiveSkippedReport skipped) {

    /**
     * One artifact of the context: the artifact as the effective list gives it, and the part of the ownership rule that
     * lists it for this action. A suggestion carries its candidate's.
     *
     * @param artifact the artifact, as the effective list gives it
     * @param appliesThrough the part of the rule that lists it
     */
    public record Item(EffectiveArtifact artifact, AppliesThrough appliesThrough) {

        /**
         * Checks the fields.
         *
         * @param artifact the artifact
         * @param appliesThrough the part of the rule that lists it
         */
        public Item {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(appliesThrough, "appliesThrough");
        }
    }

    /**
     * A node of the context and the enhancers in force that enhance it.
     *
     * @param target the step, or a listed artifact
     * @param enhancedBy the enhancers in force, sorted by kind and id whatever order they are given in
     */
    public record Enhancement(NodeRef target, List<ArtifactRef> enhancedBy) {

        /**
         * Checks and copies the fields.
         *
         * @param target the enhanced node
         * @param enhancedBy its enhancers
         */
        public Enhancement {
            Objects.requireNonNull(target, "target");
            enhancedBy = Objects.requireNonNull(enhancedBy, "enhancedBy").stream()
                    .sorted()
                    .toList();
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param artifacts the listed artifacts
     * @param enhancements the enhancements
     * @param skipped the skipped report
     */
    public EffectiveContext {
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        enhancements = List.copyOf(Objects.requireNonNull(enhancements, "enhancements"));
        Objects.requireNonNull(skipped, "skipped");
    }

    /**
     * The context of an action.
     *
     * @param set the effective set of the activation
     * @param walked what the walk reached from the action's step
     * @param missionType the mission type of the action
     * @param step the action's step
     * @return the context; for a mission type not in force it lists nothing and names the mission type itself in the
     *     skipped report's {@code missionTypeNotInForce}, as an action context's report does
     * @throws IllegalStateException when the walk lists an artifact the set does not hold
     */
    public static EffectiveContext of(EffectiveSet set, ActionContext walked, ArtifactRef missionType, NodeRef step) {
        if (!walked.missionTypeInForce()) {
            return new EffectiveContext(
                    List.of(), List.of(), set.skipped().withMissionTypeNotInForce(List.of(missionType)));
        }
        Map<ArtifactRef, EffectiveArtifact> byRef = new HashMap<>();
        set.artifacts().forEach(artifact -> byRef.put(artifact.ref(), artifact));
        List<Item> artifacts = Stream.concat(
                        walked.entries().stream()
                                .map(entry -> new Item(inForce(byRef, entry.ref()), entry.appliesThrough())),
                        walked.suggestionCandidates().stream()
                                .flatMap(candidate -> set
                                        .suggested(candidate.ref(), candidate.when(), candidate.reason())
                                        .map(suggestion -> new Item(suggestion, candidate.appliesThrough()))
                                        .stream()))
                .sorted(Comparator.comparing(item -> item.artifact().ref().urn(), ActivationDocument.CODE_POINT_ORDER))
                .toList();
        return new EffectiveContext(
                artifacts, enhancements(walked, step), set.skipped().withMissionTypeNotInForce(List.of()));
    }

    private static EffectiveArtifact inForce(Map<ArtifactRef, EffectiveArtifact> byRef, ArtifactRef ref) {
        EffectiveArtifact artifact = byRef.get(ref);
        if (artifact == null) {
            throw new IllegalStateException("The walk lists " + ref.urn() + ", which the effective set does not hold.");
        }
        return artifact;
    }

    /** The enhancers in force of the step and of each listed artifact that has one, by node. */
    private static List<Enhancement> enhancements(ActionContext walked, NodeRef step) {
        Stream<Enhancement> ofStep = Stream.of(new Enhancement(step, walked.stepEnhancedBy()));
        Stream<Enhancement> ofArtifacts =
                walked.entries().stream().map(entry -> new Enhancement(NodeRef.of(entry.ref()), entry.enhancedBy()));
        return Stream.concat(ofStep, ofArtifacts)
                .filter(enhancement -> !enhancement.enhancedBy().isEmpty())
                .sorted(Comparator.comparing(Enhancement::target))
                .toList();
    }
}
