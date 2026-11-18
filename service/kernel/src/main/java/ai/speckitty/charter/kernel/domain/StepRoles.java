package ai.speckitty.charter.kernel.domain;

import java.util.Optional;

/**
 * An artifact whose steps name the role that runs them (ADR-0032 decision 1): a mission type, whose steps name roles and
 * never profiles. The kernel names the fact so a slice can read a step's role, such as the step-keyed profile selection
 * under an activation, without depending on the slice that owns the artifact, as {@link RoleBearer} does for a
 * profile's roles. It names each step's model tier the same way (ADR-0080).
 */
public interface StepRoles {

    /**
     * The role one step names.
     *
     * @param stepId the step's id, from the sequence or the on-demand steps
     * @return the step's role; empty when the step declares none, or the artifact has no step of that id
     */
    Optional<String> stepRole(String stepId);

    /**
     * The model tier one step declares (ADR-0080 decision 13): the cost tier a model for the step is advised at most.
     *
     * @param stepId the step's id, from the sequence or the on-demand steps
     * @return the step's tier; empty when the step declares none, or the artifact has no step of that id
     */
    default Optional<CostTier> stepModelTier(String stepId) {
        return Optional.empty();
    }
}
