package ai.speckitty.charter.missions.infra.activations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.activations.application.EffectiveMissionTypeWinner;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.EffectiveStepContractWinner;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.application.EffectiveMissionType;
import ai.speckitty.charter.missions.application.EffectiveStepContract;
import ai.speckitty.charter.missions.application.HeldArtifact;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The adapter over the activations slice's winner of a mission type (#99, FR-007, D12): the winner is narrowed to the
 * missions domain's {@link MissionType}, the holder is the pack that holds it, the step contracts pass as the kernel
 * artifacts the activations slice read and the fingerprint is the read's source fingerprint. A refusal passes unchanged;
 * any other artifact held as the type is a bug.
 */
class ActivationsMissionTypesTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final String SOURCE = "sha256:" + "e".repeat(64);

    private final EffectiveReadService effective = mock(EffectiveReadService.class);
    private final ActivationsMissionTypes adapter = new ActivationsMissionTypes(effective);

    @Test
    void theWinnerIsTheMissionsSlicesOwnWithItsPackItsBoundContractsAndTheSourceFingerprint() {
        MissionType type = type();
        CharterArtifact contract = new Stub(ArtifactKind.MISSION_STEP_CONTRACT, "specify-contract");
        when(effective.missionTypeWinner(ID, "software-dev"))
                .thenReturn(new EffectiveMissionTypeWinner("proj", type, List.of(contract), SOURCE));

        EffectiveMissionType winner = adapter.missionType("a-1", "software-dev");

        assertThat(winner.holder()).isEqualTo(PROJ);
        assertThat(winner.type()).isSameAs(type);
        assertThat(winner.stepContracts()).containsExactly(contract);
        assertThat(winner.fingerprint()).isEqualTo(SOURCE);
    }

    @Test
    void anArtifactThatIsNoMissionTypeIsABugAndARefusalPassesUnchanged() {
        when(effective.missionTypeWinner(ID, "software-dev"))
                .thenReturn(new EffectiveMissionTypeWinner(
                        "proj", new Stub(ArtifactKind.MISSION_TYPE, "software-dev"), List.of(), SOURCE));
        assertThatThrownBy(() -> adapter.missionType("a-1", "software-dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the activations read a mission type as");
        CharterRefusal refused = new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "no a-1");
        when(effective.missionTypeWinner(ID, "software-dev")).thenThrow(refused);

        assertThatThrownBy(() -> adapter.missionType("a-1", "software-dev")).isSameAs(refused);
    }

    @Test
    void aStepContractIsTheMissionsSlicesOwnWithItsPackItsBoundTypeAndTheHeldArtifacts() {
        MissionType type = type();
        CharterArtifact contract = new Stub(ArtifactKind.MISSION_STEP_CONTRACT, "specify");
        when(effective.stepContractWinner(ID, "specify"))
                .thenReturn(new EffectiveStepContractWinner(
                        "main",
                        contract,
                        Optional.of(type),
                        Set.of(new ArtifactRef(ArtifactKind.DIRECTIVE, "GUARD")),
                        SOURCE));

        EffectiveStepContract winner = adapter.stepContract("a-1", "specify");

        assertThat(winner.holder()).isEqualTo(new PackIdentifier("main"));
        assertThat(winner.contract()).isSameAs(contract);
        assertThat(winner.missionType()).containsSame(type);
        assertThat(winner.held()).containsExactly(new HeldArtifact(ArtifactKind.DIRECTIVE, "GUARD"));
        assertThat(winner.fingerprint()).isEqualTo(SOURCE);
    }

    @Test
    void aStepContractWhoseTypeIsHeldNowhereHasNoneAndARefusalPassesUnchanged() {
        when(effective.stepContractWinner(ID, "stray"))
                .thenReturn(new EffectiveStepContractWinner(
                        "main",
                        new Stub(ArtifactKind.MISSION_STEP_CONTRACT, "stray"),
                        Optional.empty(),
                        Set.of(),
                        SOURCE));
        assertThat(adapter.stepContract("a-1", "stray").missionType()).isEmpty();
        CharterRefusal refused = new CharterRefusal(RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND, "no gather");
        when(effective.stepContractWinner(ID, "gather")).thenThrow(refused);

        assertThatThrownBy(() -> adapter.stepContract("a-1", "gather")).isSameAs(refused);
    }

    @Test
    void theAdapterNeedsTheEffectiveReads() {
        assertThatThrownBy(() -> new ActivationsMissionTypes(null)).isInstanceOf(NullPointerException.class);
    }

    private static MissionType type() {
        return MissionType.builder()
                .id(new MissionTypeIdentifier("software-dev"))
                .packId(PROJ)
                .schemaVersion("1.0")
                .title("Software Development")
                .description("Build software")
                .step(new MissionStep(
                        "specify",
                        "Specification",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of()))
                .build();
    }

    /** An artifact of a kind as the kernel holds it, which the store would never bind. */
    private record Stub(ArtifactKind kind, String localId) implements CharterArtifact {

        @Override
        public PackIdentifier packId() {
            return PROJ;
        }

        @Override
        public Optional<String> schemaVersion() {
            return Optional.empty();
        }

        @Override
        public String label() {
            return localId;
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }
    }
}
