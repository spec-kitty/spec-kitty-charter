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
import java.util.Set;
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
    private String laterFingerprint = FINGERPRINT;
    private Optional<RefusalCode> packRefusal = Optional.empty();

    private EffectiveStepContract stepContract = new EffectiveStepContract(
            new PackIdentifier("main"), new Contract("specify-contract"), Optional.empty(), Set.of(), FINGERPRINT);

    private final ActivationMissionTypes activations = new ActivationMissionTypes() {
        @Override
        public EffectiveMissionType missionType(String activation, String type) {
            String fingerprint = asked.contains(activation + " " + type) ? laterFingerprint : FINGERPRINT;
            asked.add(activation + " " + type);
            if (!type.equals("software-dev")) {
                throw new CharterRefusal(RefusalCode.MISSION_TYPE_NOT_FOUND, "no " + type);
            }
            return new EffectiveMissionType(PROJ, type(), List.of(new Contract("specify-contract")), fingerprint);
        }

        @Override
        public EffectiveStepContract stepContract(String activation, String id) {
            asked.add(activation + " contract " + id);
            return stepContract;
        }
    };
    private final PackMissionTypes packs = new PackMissionTypes() {
        @Override
        public MissionTypeHolding holding(PackIdentifier pack, String type) {
            asked.add(pack.value() + ":" + type);
            packRefusal.ifPresent(code -> {
                throw new CharterRefusal(code, "pack " + pack.value() + ": " + code.token());
            });
            return EffectiveMissionTypeReadServiceTest.holding(packFingerprint);
        }

        // An activation read never computes a pack revision (C-002): the call is recorded so a test can see it.
        @Override
        public PackRead<MissionTypeHolding> revised(PackIdentifier pack, String type) {
            asked.add("revised " + pack.value() + ":" + type);
            MissionTypeHolding held = EffectiveMissionTypeReadServiceTest.holding(packFingerprint);
            return new PackRead<>(held, held.fingerprint(), "sha256:" + "e".repeat(64));
        }
    };
    private final StepContracts contracts = (contract, read) -> view(contract.localId(), "specify");
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
    void noActivationReadAsksForThePackRevision() {
        reads.missionType("a-1", "software-dev");
        reads.step("a-1", "software-dev", "specify");
        reads.skeleton("a-1", "software-dev", "spec.md");

        assertThat(asked).noneMatch(call -> call.startsWith("revised "));
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
    void aHolderThatLeftTheConfigurationOrNoLongerHoldsTheTypeAtTheSecondReadIsStaleInputForEachRead() {
        laterFingerprint = "sha256:" + "f".repeat(64);
        for (RefusalCode changed :
                List.of(RefusalCode.PACK_NOT_FOUND, RefusalCode.MISSION_TYPE_NOT_FOUND, RefusalCode.PACK_UNREADABLE)) {
            packRefusal = Optional.of(changed);
            for (Runnable read : List.<Runnable>of(
                    () -> reads.missionType("a-1", "software-dev"),
                    () -> reads.step("a-1", "software-dev", "specify"),
                    () -> reads.skeleton("a-1", "software-dev", "spec.md"))) {
                asked.clear();
                assertThatThrownBy(read::run)
                        .as(
                                "the activation's read found the type in proj a moment ago, so %s means the packs changed"
                                        + " (pack_unreadable: and the fingerprint moved)",
                                changed.token())
                        .isInstanceOfSatisfying(
                                CharterRefusal.class,
                                refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
            }
        }
    }

    @Test
    void anUnreadableHolderAtTheSecondReadIsRefusedAsTheHoldingReadRefusesIt() {
        packRefusal = Optional.of(RefusalCode.PACK_UNREADABLE);

        assertThatThrownBy(() -> reads.missionType("a-1", "software-dev"))
                .as("a file the holding read alone reads may stay unreadable, so reading again would not help")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void aSkeletonIsTheHoldingPacks() {
        Fingerprinted<SkeletonView> skeleton = reads.skeleton("a-1", "software-dev", "spec.md");

        assertThat(skeleton.value().content()).isEqualTo("# Spec\n");
        assertThat(skeleton.value().path()).isEqualTo("templates/spec.md");
        assertThat(skeleton.fingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void aStepContractLinksItsTypeAndItsStepWhenTheWinningTypeDeclaresItAndItsHeldCandidates() {
        stepContract = new EffectiveStepContract(
                new PackIdentifier("main"),
                new Contract("specify-contract"),
                Optional.of(type()),
                Set.of(new HeldArtifact(ArtifactKind.DIRECTIVE, "GUARD"), new HeldArtifact(ArtifactKind.TACTIC, "t")),
                FINGERPRINT);

        EffectiveMissionTypeReadService.Held<EffectiveStepContractView> held =
                reads.stepContract("a-1", "specify-contract");

        assertThat(held.holder()).isEqualTo(new PackIdentifier("main"));
        assertThat(held.read().fingerprint()).isEqualTo(FINGERPRINT);
        EffectiveStepContractView view = held.read().value();
        assertThat(view.contract().id()).isEqualTo("specify-contract");
        assertThat(view.missionTypeHeld()).isTrue();
        assertThat(view.stepHeld()).as("the winning type declares specify").isTrue();
        assertThat(view.heldCandidates())
                .as("the candidates the closure holds, of the delegation's own kind")
                .containsExactly(new HeldArtifact(ArtifactKind.DIRECTIVE, "GUARD"));
        assertThat(asked).as("one read of the activation").containsExactly("a-1 contract specify-contract");
    }

    @Test
    void aStepContractWhoseActionTheWinningTypeDoesNotDeclareLinksNoStep() {
        stepContract = new EffectiveStepContract(
                new PackIdentifier("main"), new Contract("review"), Optional.of(type()), Set.of(), FINGERPRINT);

        EffectiveStepContractView view =
                reads.stepContract("a-1", "review").read().value();

        assertThat(view.missionTypeHeld()).isTrue();
        assertThat(view.stepHeld()).isFalse();
    }

    @Test
    void anOnDemandStepIsAStepTheWinningTypeDeclares() {
        stepContract = new EffectiveStepContract(
                new PackIdentifier("main"),
                new Contract("retro"),
                Optional.of(typeWithARetrospective()),
                Set.of(),
                FINGERPRINT);

        assertThat(reads.stepContract("a-1", "retro").read().value().stepHeld()).isTrue();
    }

    @Test
    void aStepContractWhoseTypeTheClosureDoesNotHoldLinksNeither() {
        EffectiveStepContractView view =
                reads.stepContract("a-1", "specify-contract").read().value();

        assertThat(view.missionTypeHeld()).isFalse();
        assertThat(view.stepHeld()).isFalse();
        assertThat(view.heldCandidates()).isEmpty();
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

    /** The type with an on-demand step, retrospect. */
    private static MissionType typeWithARetrospective() {
        return MissionType.builder()
                .id(new MissionTypeIdentifier("software-dev"))
                .packId(PROJ)
                .schemaVersion("1.0")
                .title("Software Development")
                .description("Build software")
                .step(type().steps().getFirst())
                .onDemandStep(new MissionStep(
                        "retrospect",
                        "Retrospective",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of()))
                .build();
    }

    /**
     * A contract's view: specify-contract binds specify and delegates to two directives, one held; review binds review,
     * which the type does not declare; retro binds the on-demand step retrospect.
     */
    private static StepContractView view(String id, String unused) {
        String action = switch (id) {
            case "review" -> "review";
            case "retro" -> "retrospect";
            default -> "specify";
        };
        return new StepContractView(
                id,
                "1.0",
                action,
                "software-dev",
                List.of(new StepContractView.Step(
                        "capture",
                        "Capture",
                        Optional.empty(),
                        List.of(),
                        Optional.of(new StepContractView.Delegation(ArtifactKind.DIRECTIVE, List.of("GUARD", "NOPE"))),
                        Optional.empty())),
                List.of());
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
