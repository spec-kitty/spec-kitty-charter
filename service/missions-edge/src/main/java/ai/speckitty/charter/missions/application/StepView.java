package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.MissionStep;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One step as its read serves it: its stored fields, its stored prompt and guidelines as text, the step contract bound
 * to it and the skeleton each expected output names.
 *
 * @param missionTypeId the id of the step's mission type
 * @param step the step, for its stored fields
 * @param onDemand whether it is one of the type's on-demand steps
 * @param prompt the stored text of its prompt file; empty when it has none
 * @param guidelines the stored text of its guidelines file; empty when it has none
 * @param stepContractId the step contract whose {@code mission} and {@code action} are the type and the step, the lowest
 *     id when several are; empty when none is
 * @param outputs its expected output artifacts, in stored order, each with the skeleton it names
 */
public record StepView(
        String missionTypeId,
        MissionStep step,
        boolean onDemand,
        Optional<String> prompt,
        Optional<String> guidelines,
        Optional<String> stepContractId,
        List<OutputView> outputs) {

    /**
     * A view, its outputs copied.
     *
     * @param missionTypeId the type's id
     * @param step the step
     * @param onDemand whether it is on demand
     * @param prompt the prompt text
     * @param guidelines the guidelines text
     * @param stepContractId the bound step contract's id
     * @param outputs the expected outputs
     */
    public StepView {
        Objects.requireNonNull(missionTypeId, "missionTypeId");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(guidelines, "guidelines");
        Objects.requireNonNull(stepContractId, "stepContractId");
        outputs = List.copyOf(outputs);
    }

    /**
     * One expected output artifact of the step.
     *
     * @param artifact the output as stored
     * @param skeletonName the name (D9) of the stored skeleton its {@code templatePath} names; empty when it names none,
     *     or one the type does not store
     */
    public record OutputView(ExpectedArtifact artifact, Optional<String> skeletonName) {}
}
