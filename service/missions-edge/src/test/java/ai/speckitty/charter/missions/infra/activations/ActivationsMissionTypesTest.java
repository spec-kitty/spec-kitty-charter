package ai.speckitty.charter.missions.infra.activations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.activations.application.EffectiveMissionTypeAnswer;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.HeldBytes;
import ai.speckitty.charter.activations.application.HeldMissionType;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.application.EffectiveMissionTypeHolding;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The adapter over the activations slice's read of a mission type (#99, FR-007): the winner is narrowed to the missions
 * domain's {@link MissionType}, a skeleton is named by its path under the type's {@code templates/}, a step contract by
 * its id and action, a step's files keep their bytes, the holder is the pack that holds the winner and the fingerprint
 * is the read's source fingerprint. A refusal passes unchanged; any other artifact held as the type is a bug.
 */
class ActivationsMissionTypesTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final String SOURCE = "sha256:" + "e".repeat(64);

    private final EffectiveReadService effective = mock(EffectiveReadService.class);
    private final ActivationsMissionTypes adapter = new ActivationsMissionTypes(effective);

    @Test
    void theHoldingIsTheMissionsSlicesOwnWithTheWinnersPackAtTheReadsSourceFingerprint() {
        MissionType type = type();
        when(effective.missionType(ID, "software-dev"))
                .thenReturn(answer(
                        type,
                        List.of(new HeldMissionType.Skeleton(
                                "software-dev/divio/x.md", HeldBytes.of(bytes("# X\n"))))));

        EffectiveMissionTypeHolding held = adapter.holding("a-1", "software-dev");

        assertThat(held.holder()).isEqualTo(PROJ);
        MissionTypeHolding holding = held.holding();
        assertThat(holding.type()).isSameAs(type);
        assertThat(holding.fingerprint()).isEqualTo(SOURCE);
        assertThat(holding.skeletons()).singleElement().satisfies(skeleton -> {
            assertThat(skeleton.path()).isEqualTo("divio/x.md");
            assertThat(skeleton.content().bytes()).isEqualTo(bytes("# X\n"));
        });
        assertThat(holding.stepContracts())
                .containsExactly(new MissionTypeHolding.BoundContract("specify-contract", "specify"));
        MissionTypeHolding.StepFiles files = holding.stepFiles().get("specify");
        assertThat(files.prompt().map(StoredFile::bytes))
                .hasValueSatisfying(prompt -> assertThat(prompt).isEqualTo(bytes("# Specify\n")));
        assertThat(files.guidelines()).isEmpty();
    }

    @Test
    void aSkeletonOutsideTheTypesDirectoryIsABug() {
        when(effective.missionType(ID, "software-dev"))
                .thenReturn(
                        answer(type(), List.of(new HeldMissionType.Skeleton("other/x.md", HeldBytes.of(bytes("x"))))));

        assertThatThrownBy(() -> adapter.holding("a-1", "software-dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("other/x.md");
    }

    @Test
    void anArtifactThatIsNoMissionTypeIsABugAndARefusalPassesUnchanged() {
        assertThatThrownBy(() -> ActivationsMissionTypes.narrowed(new NotAType()))
                .isInstanceOf(IllegalStateException.class);
        CharterRefusal refused = new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "no a-1");
        when(effective.missionType(ID, "software-dev")).thenThrow(refused);

        assertThatThrownBy(() -> adapter.holding("a-1", "software-dev")).isSameAs(refused);
    }

    @Test
    void theAdapterNeedsTheEffectiveReads() {
        assertThatThrownBy(() -> new ActivationsMissionTypes(null)).isInstanceOf(NullPointerException.class);
    }

    private static EffectiveMissionTypeAnswer answer(MissionType type, List<HeldMissionType.Skeleton> skeletons) {
        return new EffectiveMissionTypeAnswer(
                StoredActivation.of(ID, ActivationDocument.builder().build().document()),
                SOURCE,
                "sha256:closure",
                new HeldMissionType(
                        "proj",
                        PackLevel.PROJECT,
                        type,
                        skeletons,
                        Map.of(
                                "specify",
                                new HeldMissionType.StepFiles(
                                        Optional.of(HeldBytes.of(bytes("# Specify\n"))), Optional.empty())),
                        List.of(new HeldMissionType.StepContract("specify-contract", "specify", "main"))));
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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** An artifact the store would never hold as a mission type. */
    private record NotAType() implements CharterArtifact {

        @Override
        public ArtifactKind kind() {
            return ArtifactKind.MISSION_TYPE;
        }

        @Override
        public PackIdentifier packId() {
            return PROJ;
        }

        @Override
        public String localId() {
            return "software-dev";
        }

        @Override
        public Optional<String> schemaVersion() {
            return Optional.empty();
        }

        @Override
        public String label() {
            return "software-dev";
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }
    }
}
