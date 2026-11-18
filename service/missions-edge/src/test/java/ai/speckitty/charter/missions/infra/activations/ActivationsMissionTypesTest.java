package ai.speckitty.charter.missions.infra.activations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.activations.application.EffectiveMissionTypeWinner;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.application.EffectiveMissionType;
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
 * missions domain's {@link MissionType}, the holder is the pack that holds it and the fingerprint is the read's source
 * fingerprint. A refusal passes unchanged;
 * any other artifact held as the type is a bug.
 */
class ActivationsMissionTypesTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final String SOURCE = "sha256:" + "e".repeat(64);

    private final EffectiveReadService effective = mock(EffectiveReadService.class);
    private final ActivationsMissionTypes adapter = new ActivationsMissionTypes(effective);

    @Test
    void theWinnerIsTheMissionsSlicesOwnWithItsPackAndTheSourceFingerprint() {
        MissionType type = type();
        when(effective.missionTypeWinner(ID, "software-dev"))
                .thenReturn(new EffectiveMissionTypeWinner(
                        "proj", type, SOURCE, Set.of(new ArtifactRef(ArtifactKind.TACTIC, "tdd"))));

        EffectiveMissionType winner = adapter.missionType("a-1", "software-dev");

        assertThat(winner.held()).containsExactly(new HeldArtifact(ArtifactKind.TACTIC, "tdd"));

        assertThat(winner.holder()).isEqualTo(PROJ);
        assertThat(winner.type()).isSameAs(type);
        assertThat(winner.fingerprint()).isEqualTo(SOURCE);
    }

    @Test
    void anArtifactThatIsNoMissionTypeIsABugAndARefusalPassesUnchanged() {
        when(effective.missionTypeWinner(ID, "software-dev"))
                .thenReturn(new EffectiveMissionTypeWinner(
                        "proj", new Stub(ArtifactKind.MISSION_TYPE, "software-dev"), SOURCE, Set.of()));
        assertThatThrownBy(() -> adapter.missionType("a-1", "software-dev"))
                .isInstanceOf(IllegalStateException.class)
                .as("the fault names the read that lacks its mission type")
                .hasMessageStartingWith("The read of the mission type software-dev under activation a-1 holds ")
                .hasMessageEndingWith(", not a mission type.");
        CharterRefusal refused = new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "no a-1");
        when(effective.missionTypeWinner(ID, "software-dev")).thenThrow(refused);

        assertThatThrownBy(() -> adapter.missionType("a-1", "software-dev")).isSameAs(refused);
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
                        Optional.empty(),
                        List.of(),
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
