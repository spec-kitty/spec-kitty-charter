package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link EffectiveReadService#missionTypeWinner} (#99, FR-007, D12): what the activations slice owns of one mission type
 * under an activation, from one session: the winner of the layer merge and the pack that holds it, the merged closure's
 * step contracts bound to the type (each the winner of its own id, from whichever pack holds it) and the source
 * fingerprint. The activation is judged first, then whether a pack of the closure holds the type.
 *
 * <p>The closure: main (org) and proj (project) both hold the type sd, proj's wins; the contract a is held by main and
 * bound to sd, b by proj and bound to sd, c by proj and bound to another type.
 */
class EffectiveReadServiceMissionWinnerTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String SOURCE = "sha256:source";
    private static final ArtifactRef SD = new ArtifactRef(ArtifactKind.MISSION_TYPE, "sd");
    private static final ArtifactRef OTHER = new ArtifactRef(ArtifactKind.MISSION_TYPE, "other");
    private static final ArtifactRef A = new ArtifactRef(ArtifactKind.MISSION_STEP_CONTRACT, "a");
    private static final ArtifactRef B = new ArtifactRef(ArtifactKind.MISSION_STEP_CONTRACT, "b");
    private static final ArtifactRef C = new ArtifactRef(ArtifactKind.MISSION_STEP_CONTRACT, "c");

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .pack("proj", "1.0.0")
                .held(ArtifactKind.MISSION_TYPE, "sd", "other")
                .held(ArtifactKind.MISSION_STEP_CONTRACT, "a", "b", "c")
                .fingerprint(SOURCE)
                .closureFingerprint("sha256:closure")
                .build());
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        winners.put(SD, winner("proj", PackLevel.PROJECT));
        winners.put(OTHER, winner("main", PackLevel.ORG));
        winners.put(B, winner("proj", PackLevel.PROJECT));
        winners.put(A, winner("main", PackLevel.ORG));
        winners.put(C, winner("proj", PackLevel.PROJECT));
        Map<ArtifactRef, ArtifactRef> bindings = new LinkedHashMap<>();
        bindings.put(B, SD);
        bindings.put(A, SD);
        bindings.put(C, OTHER);
        packs.merged(new MergedSnapshot(winners, Map.of(), List.of(), List.of(), bindings));
        packs.winners(ask -> Optional.ofNullable(winners.get(new ArtifactRef(ask.kind(), ask.id())))
                .map(won -> new EffectiveArtifactRead(
                        entity(won.packId(), ask.kind(), ask.id()), won.packId(), won.level(), Optional.empty())));
        service = new EffectiveReadService(repository, packs);
        assertThat(repository.create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("main"), PackRef.of("proj")))
                                .build()
                                .document()))
                .isPresent();
    }

    @Test
    void theWinnerIsTheProjectsCopyWithTheMergedStepContractsBoundToItAndTheSourceFingerprint() {
        EffectiveMissionTypeWinner winner = service.missionTypeWinner(ID, "sd");

        assertThat(winner.packId()).isEqualTo("proj");
        assertThat(winner.missionType().packId()).isEqualTo(new PackIdentifier("proj"));
        assertThat(winner.missionType().localId()).isEqualTo("sd");
        assertThat(winner.stepContracts())
                .as("each winner bound to sd, from whichever pack holds it, by id; c is bound to another type")
                .extracting(contract -> contract.packId().value() + ":" + contract.localId())
                .containsExactly("main:a", "proj:b");
        assertThat(winner.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(packs.calls()).as("one session").isEqualTo(1);
        assertThat(packs.effectiveAsks())
                .extracting(ask -> ask.kind().token() + ":" + ask.id())
                .as("the type and the contracts bound to it, nothing else")
                .containsExactly("mission_type:sd", "mission_step_contract:a", "mission_step_contract:b");
    }

    @Test
    void aTypeNoPackOfTheClosureHoldsIsMissionTypeNotFoundAndNoEntityIsRead() {
        assertThatThrownBy(() -> service.missionTypeWinner(ID, "research"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("No pack of the activation's closure holds the mission type research.");
                });
        assertThat(packs.effectiveAsks()).isEmpty();
    }

    @Test
    void anUnknownActivationIsJudgedBeforeThePacksAreRead() {
        assertThatThrownBy(() -> service.missionTypeWinner(new ActivationId("nope"), "sd"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aRefusalOfTheSessionIsTheAnswerUnchanged() {
        packs.winners(ask -> {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, "the packs changed");
        });

        assertThatThrownBy(() -> service.missionTypeWinner(ID, "sd"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aStepContractIsAnsweredWithItsBoundTypesWinnerAndTheHeldArtifactsOfTheListedKinds() {
        EffectiveStepContractWinner winner = service.stepContractWinner(ID, "a");

        assertThat(winner.packId()).isEqualTo("main");
        assertThat(winner.stepContract().localId()).isEqualTo("a");
        assertThat(winner.missionType())
                .as("the winner of the type the merge binds a to")
                .hasValueSatisfying(type -> assertThat(type.packId()).isEqualTo(new PackIdentifier("proj")));
        assertThat(winner.held())
                .as("the listed kinds only: the fake lists no mission kind")
                .isEmpty();
        assertThat(winner.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(packs.calls()).as("one session").isEqualTo(1);

        packs.listedKinds(List.of(ArtifactKind.MISSION_TYPE, ArtifactKind.DIRECTIVE));
        assertThat(service.stepContractWinner(ID, "a").held()).containsExactlyInAnyOrder(SD, OTHER);
    }

    @Test
    void aStepContractBoundToATypeTheClosureDoesNotHoldHasNoType() {
        packs.merged(new MergedSnapshot(
                Map.of(A, winner("main", PackLevel.ORG)),
                Map.of(),
                List.of(),
                List.of(),
                Map.of(A, new ArtifactRef(ArtifactKind.MISSION_TYPE, "research"))));

        assertThat(service.stepContractWinner(ID, "a").missionType()).isEmpty();
        assertThat(packs.effectiveAsks())
                .extracting(ask -> ask.kind().token() + ":" + ask.id())
                .containsExactly("mission_step_contract:a");
    }

    @Test
    void aStepContractNoPackOfTheClosureHoldsIsNotFoundAfterTheActivation() {
        assertThatThrownBy(() -> service.stepContractWinner(ID, "gather"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("No pack of the activation's closure holds the mission step contract gather.");
                });
        assertThat(packs.effectiveAsks()).isEmpty();
        assertThatThrownBy(() -> service.stepContractWinner(new ActivationId("nope"), "gather"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
    }

    @Test
    void aStepsPlaceIsThePackOfTheMergedWinnerOfItsActionWithTheSourceFingerprint() {
        packs.places(
                (type, step) -> type.equals("sd") && step.equals("review") ? Optional.of("main") : Optional.empty());

        EffectiveReadService.StepPlace place = service.stepPlace(ID, "sd", "review");

        assertThat(place.packId())
                .as("main keeps review, which proj's winning sd drops (A7)")
                .isEqualTo("main");
        assertThat(place.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(packs.nameChecks()).as("judged as the context judges it").containsExactly("sd/review");
        assertThat(packs.calls()).as("one session").isEqualTo(1);
        assertThat(packs.effectiveAsks()).as("no entity is read").isEmpty();
    }

    @Test
    void aHeldStepNoVersionAtItsPlaceDeclaresIsActionNotFound() {
        packs.places((type, step) -> Optional.empty());

        assertThatThrownBy(() -> service.stepPlace(ID, "sd", "review"))
                .as("the twin of the step main's version declares: the names pass, and no version declares it")
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("No version of the mission type sd in the activation's closure declares the step"
                                    + " review.");
                });
        assertThat(packs.nameChecks()).containsExactly("sd/review");
    }

    @Test
    void aStepTheClosureDoesNotHoldIsTheSessionsRefusalBeforeAnyPlaceIsAsked() {
        packs.places((type, step) -> {
            throw new AssertionError("no place is asked of a step the names check refused");
        });
        packs.contexts(ask -> {
            if (ask.action().equals("deploy")) {
                throw new CharterRefusal(RefusalCode.ACTION_NOT_FOUND, "no such action");
            }
            throw new CharterRefusal(RefusalCode.MISSION_TYPE_NOT_FOUND, "no such mission type");
        });

        assertThatThrownBy(() -> service.stepPlace(ID, "sd", "deploy"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
        assertThatThrownBy(() -> service.stepPlace(ID, "research", "gather"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThatThrownBy(() -> service.stepPlace(new ActivationId("nope"), "sd", "review"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
    }

    private static MergedSnapshot.Winner winner(String packId, PackLevel level) {
        return new MergedSnapshot.Winner(packId, level, Policy.OPEN);
    }

    static CharterArtifact entity(String pack, ArtifactKind kind, String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return kind;
            }

            @Override
            public PackIdentifier packId() {
                return new PackIdentifier(pack);
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }
}
