package ai.speckitty.charter.kernel.domain;

/**
 * An artifact that declares the steps of a mission (a mission type, ADR-0044). The kernel names the fact so a slice can
 * judge a step's place (research R8, A7) without depending on the slice that owns the artifact.
 */
public interface StepBearer {

    /**
     * Whether this version declares a step, among its steps or its on-demand steps.
     *
     * @param stepId the step's id, exactly as declared
     * @return {@code true} when this version declares the step
     */
    boolean declaresStep(String stepId);
}
