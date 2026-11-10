package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link EffectiveReadService#missionType} (#99, FR-007): what an activation's merged closure holds of one mission type,
 * from one session; the activation is judged first, then whether a pack of the closure holds the type, and the answer
 * carries the session's fingerprints.
 */
class EffectiveReadServiceMissionTypeTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private final List<String> asked = new ArrayList<>();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        ClosureSnapshot closure = ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.MISSION_TYPE, "software-dev")
                .fingerprint(SOURCE)
                .closureFingerprint(CLOSURE)
                .build();
        packs = new FakeAvailablePacks(closure);
        packs.missionTypes(id -> {
            asked.add(id);
            return id.equals("software-dev") ? Optional.of(held()) : Optional.empty();
        });
        service = new EffectiveReadService(repository, packs);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("main")))
                                .build()
                                .document())
                .orElseThrow();
    }

    @Test
    void theHeldTypeIsAnsweredWithTheActivationAndTheFingerprintsOfTheOneSession() {
        EffectiveMissionTypeAnswer answer = service.missionType(ID, "software-dev");

        assertThat(answer.stored()).isEqualTo(stored);
        assertThat(answer.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(answer.governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(answer.held()).isEqualTo(held());
        assertThat(packs.calls()).as("one session").isEqualTo(1);
        assertThat(asked).containsExactly("software-dev");
    }

    @Test
    void aTypeNoPackOfTheClosureHoldsIsMissionTypeNotFound() {
        assertThatThrownBy(() -> service.missionType(ID, "research"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("No pack of the activation's closure holds the mission type research.");
                });
    }

    @Test
    void anUnknownActivationIsJudgedBeforeThePacksAreRead() {
        assertThatThrownBy(() -> service.missionType(new ActivationId("nope"), "software-dev"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
        assertThat(asked).isEmpty();
    }

    @Test
    void aRefusalOfTheSessionIsTheAnswerUnchanged() {
        packs.missionTypes(id -> {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, "the packs changed");
        });

        assertThatThrownBy(() -> service.missionType(ID, "software-dev"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void theDefaultOfASessionThatReadsNoPackFilesRefusesToAnswer() {
        PacksSession session = new PacksSession() {
            @Override
            public ClosureSnapshot snapshot() {
                throw new AssertionError();
            }

            @Override
            public ai.speckitty.charter.activations.domain.Reach reach(
                    ai.speckitty.charter.activations.domain.ReachPlan plan) {
                throw new AssertionError();
            }

            @Override
            public ai.speckitty.charter.activations.domain.MergedSnapshot merged() {
                throw new AssertionError();
            }

            @Override
            public ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer inForce(
                    ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery query) {
                throw new AssertionError();
            }

            @Override
            public ai.speckitty.charter.activations.domain.GraphSnapshot graph(
                    ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery query) {
                throw new AssertionError();
            }

            @Override
            public ai.speckitty.charter.activations.domain.ActionContext actionContext(
                    String missionType,
                    String action,
                    int depth,
                    ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery query) {
                throw new AssertionError();
            }

            @Override
            public Optional<ai.speckitty.charter.activations.domain.EffectiveArtifactRead> effectiveArtifact(
                    ArtifactKind kind,
                    String id,
                    int depth,
                    ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery query) {
                throw new AssertionError();
            }

            @Override
            public void requireAction(String missionType, String action) {
                throw new AssertionError();
            }
        };

        assertThatThrownBy(() -> session.missionType("software-dev")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void heldBytesAreCopiedInAndOutAndCompareByContent() {
        byte[] bytes = {1, 2, 3};
        HeldBytes held = HeldBytes.of(bytes);
        bytes[0] = 9;

        assertThat(held.bytes()).containsExactly(1, 2, 3);
        held.bytes()[1] = 9;
        assertThat(held.bytes()).containsExactly(1, 2, 3);
        assertThat(held)
                .isEqualTo(HeldBytes.of(new byte[] {1, 2, 3}))
                .hasSameHashCodeAs(HeldBytes.of(new byte[] {1, 2, 3}));
        assertThat(held).isNotEqualTo(HeldBytes.of(new byte[] {1, 2}));
        assertThat(held).hasToString("HeldBytes[3 bytes]");
    }

    private static HeldMissionType held() {
        return new HeldMissionType(
                "main",
                PackLevel.ORG,
                new TypeStub(),
                List.of(new HeldMissionType.Skeleton("software-dev/spec-template.md", HeldBytes.of(new byte[] {35}))),
                Map.of(
                        "specify",
                        new HeldMissionType.StepFiles(Optional.of(HeldBytes.of(new byte[] {1})), Optional.empty())),
                List.of(new HeldMissionType.StepContract("specify", "specify", "main")));
    }

    /** A mission type as the activations slice holds it: an artifact of kind {@code mission_type}. */
    private record TypeStub() implements CharterArtifact {

        @Override
        public ArtifactKind kind() {
            return ArtifactKind.MISSION_TYPE;
        }

        @Override
        public PackIdentifier packId() {
            return new PackIdentifier("main");
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
