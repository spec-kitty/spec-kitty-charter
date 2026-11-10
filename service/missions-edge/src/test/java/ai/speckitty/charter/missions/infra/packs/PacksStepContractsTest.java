package ai.speckitty.charter.missions.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The narrowing of a step contract the activations slice hands on as a kernel artifact (D12): its id and the step its
 * {@code action} names; an artifact the store did not bind to the packs slice's step contract is a bug.
 */
class PacksStepContractsTest {

    private final PacksStepContracts contracts = new PacksStepContracts();

    @Test
    void aStepContractBindsTheStepItsActionNames() {
        assertThat(contracts.bound(contract()))
                .isEqualTo(new MissionTypeHolding.BoundContract("specify-contract", "specify"));
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

        assertThatThrownBy(() -> contracts.bound(stub))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the packs read a mission step contract as");
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
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty()))
                .build();
    }
}
