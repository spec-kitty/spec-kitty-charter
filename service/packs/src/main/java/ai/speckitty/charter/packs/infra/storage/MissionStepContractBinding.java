package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.GateBinding;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.StepDelegation;
import ai.speckitty.charter.packs.domain.StepInput;

/**
 * The {@link KindBinding} of the mission step contract: {@link MissionStepContractDso} to {@link MissionStepContract}
 * and back. Its files are {@code *.step-contract.yaml}, validated by the {@code mission-step-contract} schema
 * ({@code contracts/packs/file-kinds.yaml}), neither of which is the kind's token.
 */
enum MissionStepContractBinding implements KindBinding<MissionStepContractDso, MissionStepContract> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.MISSION_STEP_CONTRACT;
    }

    @Override
    public String fileToken() {
        return "step-contract";
    }

    @Override
    public String schemaName() {
        return "mission-step-contract";
    }

    @Override
    public Class<MissionStepContract> entityType() {
        return MissionStepContract.class;
    }

    @Override
    public Class<MissionStepContractDso> dsoType() {
        return MissionStepContractDso.class;
    }

    @Override
    public String idOf(MissionStepContractDso dso) {
        return dso.id();
    }

    @Override
    public MissionStepContract toEntity(PackIdentifier pack, MissionStepContractDso dso) {
        MissionStepContract.Builder builder = MissionStepContract.builder()
                .id(new MissionStepContractIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .action(dso.action())
                .mission(dso.mission());
        for (MissionStepContractDso.Step step : dso.steps()) {
            builder.step(new ContractStep(
                    step.id(),
                    step.description(),
                    step.command(),
                    step.inputs().stream()
                            .map(input -> new StepInput(input.flag(), input.source(), input.optional()))
                            .toList(),
                    step.delegatesTo().map(MissionStepContractBinding::delegation),
                    step.guidance()));
        }
        for (MissionStepContractDso.Gate gate : dso.gates()) {
            builder.gate(new GateBinding(
                    gate.onTransition(),
                    gate.handler(),
                    gate.handlerKind(),
                    gate.schemaVersion(),
                    gate.failOpen(),
                    gate.provenance()));
        }
        return builder.build();
    }

    @Override
    public MissionStepContractDso toDso(MissionStepContract contract) {
        return new MissionStepContractDso(
                contract.id().value(),
                contract.schemaVersion().orElseThrow(),
                contract.action(),
                contract.mission(),
                contract.steps().stream()
                        .map(step -> new MissionStepContractDso.Step(
                                step.id(),
                                step.description(),
                                step.command(),
                                step.inputs().stream()
                                        .map(input -> new MissionStepContractDso.Input(
                                                input.flag(), input.source(), input.optional()))
                                        .toList(),
                                step.delegatesTo()
                                        .map(delegation -> new MissionStepContractDso.Delegation(
                                                delegation.kind().token(), delegation.candidates())),
                                step.guidance()))
                        .toList(),
                contract.gates().stream()
                        .map(gate -> new MissionStepContractDso.Gate(
                                gate.onTransition(),
                                gate.handler(),
                                gate.handlerKind(),
                                gate.schemaVersion(),
                                gate.failOpen(),
                                gate.provenance()))
                        .toList());
    }

    private static StepDelegation delegation(MissionStepContractDso.Delegation delegation) {
        ArtifactKind kind = ArtifactKind.fromToken(delegation.kind())
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown artifact kind '" + delegation.kind() + "' in delegates_to"));
        return new StepDelegation(kind, delegation.candidates());
    }
}
