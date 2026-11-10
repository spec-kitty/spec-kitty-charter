package ai.speckitty.charter.missions.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.StepContractView;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.GateBinding;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.StepDelegation;
import ai.speckitty.charter.packs.domain.StepInput;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The narrowing of a step contract the activations slice hands on as a kernel artifact (D11, D12): every stored field,
 * in stored order, and the binding of the step its {@code action} names; an artifact the store did not bind to the packs
 * slice's step contract is a bug.
 */
class PacksStepContractsTest {

    private final PacksStepContracts contracts = new PacksStepContracts();

    @Test
    void aStepContractIsReadWithEveryStoredFieldAndBindsTheStepItsActionNames() {
        StepContractView view =
                contracts.view(contract(), "The read of the step contract specify-contract in pack main");

        assertThat(view)
                .isEqualTo(new StepContractView(
                        "specify-contract",
                        "1.0",
                        "specify",
                        "software-dev",
                        List.of(
                                new StepContractView.Step(
                                        "bootstrap",
                                        "Load the context",
                                        Optional.of("spec-kitty charter context"),
                                        List.of(new StepContractView.Input("--json", "literal", Optional.of(true))),
                                        Optional.empty(),
                                        Optional.empty()),
                                new StepContractView.Step(
                                        "capture",
                                        "Capture the intent",
                                        Optional.empty(),
                                        List.of(),
                                        Optional.of(new StepContractView.Delegation(
                                                ArtifactKind.DIRECTIVE, List.of("GUARD", "NOPE"))),
                                        Optional.of("Ask first."))),
                        List.of(new StepContractView.Gate(
                                "for_review",
                                "check",
                                Optional.of("python"),
                                "1.0",
                                Optional.of(false),
                                Optional.of("core")))));
        assertThat(view.bound()).isEqualTo(new MissionTypeHolding.BoundContract("specify-contract", "specify"));
    }

    @Test
    void anArtifactTheStoreDidNotBindAsAStepContractIsABug() {
        CharterArtifact stub = new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.MISSION_STEP_CONTRACT;
            }

            @Override
            public PackIdentifier packId() {
                return new PackIdentifier("main");
            }

            @Override
            public String localId() {
                return "specify-contract";
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return "specify-contract";
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };

        assertThatThrownBy(() ->
                        contracts.view(stub, "The read of the step contract specify-contract under activation a-1"))
                .isInstanceOf(IllegalStateException.class)
                .as("the fault names the read that lacks its step contract")
                .hasMessageStartingWith("The read of the step contract specify-contract under activation a-1 holds ")
                .hasMessageEndingWith(", not a mission step contract.");
    }

    static MissionStepContract contract() {
        return MissionStepContract.builder()
                .id(new MissionStepContractIdentifier("specify-contract"))
                .packId(new PackIdentifier("main"))
                .schemaVersion("1.0")
                .action("specify")
                .mission("software-dev")
                .step(new ContractStep(
                        "bootstrap",
                        "Load the context",
                        Optional.of("spec-kitty charter context"),
                        List.of(new StepInput("--json", "literal", Optional.of(true))),
                        Optional.empty(),
                        Optional.empty()))
                .step(new ContractStep(
                        "capture",
                        "Capture the intent",
                        Optional.empty(),
                        List.of(),
                        Optional.of(new StepDelegation(ArtifactKind.DIRECTIVE, List.of("GUARD", "NOPE"))),
                        Optional.of("Ask first.")))
                .gate(new GateBinding(
                        "for_review", "check", Optional.of("python"), "1.0", Optional.of(false), Optional.of("core")))
                .build();
    }
}
