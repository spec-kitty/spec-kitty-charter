package ai.speckitty.charter.missions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The mission-type reads under an activation (#99, FR-007, D12) over fakes of the three ports: each read asks the
 * activations port once for the winner, then the packs port once for what the pack that holds the winner stores beside
 * it, at the same fingerprint or {@code stale_input}; the view is built as the pack read builds it, with the holding
 * pack's skeletons and step files and the merged closure's step contracts (never the holding pack's own), names the
 * pack that holds the winner for the mission type, and names the activation in the refusals of a step or a skeleton.
 */
class EffectiveMissionTypeReadServiceTest {

    private static final String FINGERPRINT = "sha256:" + "d".repeat(64);
    private static final PackIdentifier PROJ = new PackIdentifier("proj");

    private final List<String> asked = new ArrayList<>();
    private String packFingerprint = FINGERPRINT;

    private final ActivationMissionTypes activations = (activation, type) -> {
        asked.add(activation + " " + type);
        if (!type.equals("software-dev")) {
            throw new CharterRefusal(RefusalCode.MISSION_TYPE_NOT_FOUND, "no " + type);
        }
        return new EffectiveMissionType(PROJ, type(), List.of(new Contract("specify-contract")), FINGERPRINT);
    };
    private final PackMissionTypes packs = (pack, type) -> {
        asked.add(pack.value() + ":" + type);
        return holding(packFingerprint);
    };
    private final StepContracts contracts =
            contract -> new MissionTypeHolding.BoundContract(contract.localId(), "specify");
    private final EffectiveMissionTypeReadService reads =
            new EffectiveMissionTypeReadService(activations, packs, contracts);

    @Test
    void theTypeIsTheWinnersViewWithThePackThatHoldsItAndTheFingerprintOfTheRead() {
        EffectiveMissionTypeReadService.Held<MissionTypeView> held = reads.missionType("a-1", "software-dev");

        assertThat(held.holder()).isEqualTo(PROJ);
        assertThat(held.read().fingerprint()).isEqualTo(FINGERPRINT);
        assertThat(held.read().value().steps())
                .containsExactly(new MissionTypeView.StepEntry("specify", "Specification", false));
        assertThat(held.read().value().skeletons())
                .containsExactly(new MissionTypeView.SkeletonEntry("spec.md", "templates/spec.md"));
        assertThat(asked)
                .as("the winner, then the files of the pack that holds it")
                .containsExactly("a-1 software-dev", "proj:software-dev");
    }

    @Test
    void aStepIsTheWinnersWithTheHoldingPacksFilesAndTheMergedStepContract() {
        Fingerprinted<StepView> step = reads.step("a-1", "software-dev", "specify");

        assertThat(step.fingerprint()).isEqualTo(FINGERPRINT);
        assertThat(step.value().prompt()).contains("# Specify\n");
        assertThat(step.value().stepContractId())
                .as("the merged closure's contract, not the holding pack's own")
                .contains("specify-contract");
        assertThat(asked).containsExactly("a-1 software-dev", "proj:software-dev");
    }

    @Test
    void aHoldingReadAtAnotherFingerprintIsStaleInputForEachRead() {
        packFingerprint = "sha256:" + "f".repeat(64);

        for (Runnable read : List.<Runnable>of(
                () -> reads.missionType("a-1", "software-dev"),
                () -> reads.step("a-1", "software-dev", "specify"),
                () -> reads.skeleton("a-1", "software-dev", "spec.md"))) {
            assertThatThrownBy(read::run)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
        }
    }

    @Test
    void aSkeletonIsTheHoldingPacks() {
        Fingerprinted<SkeletonView> skeleton = reads.skeleton("a-1", "software-dev", "spec.md");

        assertThat(skeleton.value().content()).isEqualTo("# Spec\n");
        assertThat(skeleton.value().path()).isEqualTo("templates/spec.md");
        assertThat(skeleton.fingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void anUnknownStepOrSkeletonNamesTheActivationInItsRefusal() {
        assertThatThrownBy(() -> reads.step("a-1", "software-dev", "deploy"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("Mission type software-dev of activation a-1 has no step deploy.");
                });
        assertThatThrownBy(() -> reads.skeleton("a-1", "software-dev", "nope.md"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.SKELETON_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("Mission type software-dev of activation a-1 has no skeleton nope.md.");
                });
    }

    @Test
    void theRefusalOfThePortComesFirstUnchanged() {
        assertThatThrownBy(() -> reads.step("a-1", "research", "deploy"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThatThrownBy(() -> reads.skeleton("a-1", "research", "nope.md"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
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

    /** What proj stores beside its copy: a skeleton, the step's prompt, and a contract of its own that lost the merge. */
    private static MissionTypeHolding holding(String fingerprint) {
        return new MissionTypeHolding(
                type(),
                List.of(new MissionTypeHolding.StoredSkeleton("spec.md", utf8("# Spec\n"))),
                List.of(new MissionTypeHolding.BoundContract("a-proj-contract", "specify")),
                Map.of("specify", new MissionTypeHolding.StepFiles(Optional.of(utf8("# Specify\n")), Optional.empty())),
                fingerprint);
    }

    /** A step contract as the activations slice hands it on: a kernel artifact, which the contracts port narrows. */
    private record Contract(String localId) implements CharterArtifact {

        @Override
        public ArtifactKind kind() {
            return ArtifactKind.MISSION_STEP_CONTRACT;
        }

        @Override
        public PackIdentifier packId() {
            return new PackIdentifier("main");
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

    private static StoredFile utf8(String text) {
        return StoredFile.of(text.getBytes(StandardCharsets.UTF_8));
    }
}
