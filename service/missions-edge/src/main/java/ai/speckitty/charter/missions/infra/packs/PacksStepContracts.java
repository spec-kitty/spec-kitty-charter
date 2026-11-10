package ai.speckitty.charter.missions.infra.packs;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.missions.application.StepContractView;
import ai.speckitty.charter.missions.application.StepContracts;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.GateBinding;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import org.springframework.stereotype.Component;

/**
 * The one bean of {@link StepContracts}: the packs slice binds the kind {@code mission_step_contract} to its
 * {@link MissionStepContract}, which only this adapter of the missions slice may name, and this adapter copies its
 * stored fields into the missions slice's own {@link StepContractView}. Any other artifact here is a bug, never a
 * refusal.
 */
@Component
public final class PacksStepContracts implements StepContracts {

    /** The adapter; it holds nothing. */
    public PacksStepContracts() {}

    @Override
    public StepContractView view(CharterArtifact contract) {
        MissionStepContract narrowed = narrowed(contract);
        return new StepContractView(
                narrowed.localId(),
                narrowed.schemaVersion().orElseThrow(),
                narrowed.action(),
                narrowed.mission(),
                narrowed.steps().stream().map(PacksStepContracts::step).toList(),
                narrowed.gates().stream().map(PacksStepContracts::gate).toList());
    }

    /** The step contract the pack store bound; anything else is a bug of the store. */
    static MissionStepContract narrowed(CharterArtifact artifact) {
        if (artifact instanceof MissionStepContract contract) {
            return contract;
        }
        throw new IllegalStateException("the packs read a mission step contract as "
                + artifact.getClass().getName());
    }

    private static StepContractView.Step step(ContractStep step) {
        return new StepContractView.Step(
                step.id(),
                step.description(),
                step.command(),
                step.inputs().stream()
                        .map(input -> new StepContractView.Input(input.flag(), input.source(), input.optional()))
                        .toList(),
                step.delegatesTo()
                        .map(delegation -> new StepContractView.Delegation(delegation.kind(), delegation.candidates())),
                step.guidance());
    }

    private static StepContractView.Gate gate(GateBinding gate) {
        return new StepContractView.Gate(
                gate.onTransition(),
                gate.handler(),
                gate.handlerKind(),
                gate.schemaVersion(),
                gate.failOpen(),
                gate.provenance());
    }
}
