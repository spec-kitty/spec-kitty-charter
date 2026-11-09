package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import java.util.List;
import java.util.Objects;

/**
 * What {@code getEffectiveActionContext} answers (IC-11, FR-019, rule E16), assembled from the {@link EffectiveSet} and
 * what the walk reached: the artifacts the action's step names, sorted by URN, each as the effective list gives it; the
 * enhancers in force of each node; and the skipped report.
 *
 * @param artifacts the listed artifacts in URN order: those in force, then each suggestion among them by its URN
 * @param enhancements the enhancers in force of the step and of each listed artifact in force
 * @param skipped the skipped report, whose {@code missionTypeNotInForce} is the mission type itself when it is not in force
 */
public record EffectiveContext(
        List<EffectiveArtifact> artifacts, List<Enhancement> enhancements, EffectiveSkippedReport skipped) {

    /**
     * A node of the context and the enhancers in force that enhance it.
     *
     * @param target the step, or a listed artifact
     * @param enhancedBy the enhancers in force, sorted
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
            enhancedBy = List.copyOf(Objects.requireNonNull(enhancedBy, "enhancedBy"));
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
     * @return the context
     */
    public static EffectiveContext of(EffectiveSet set, ActionContext walked, ArtifactRef missionType, NodeRef step) {
        return new EffectiveContext(List.of(), List.of(), set.skipped());
    }
}
