package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
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
 * The activation reads of the asset and skill twins (slice A2, IC-05; research R1, R2, R3 and R15):
 * {@link EffectiveReadService#heldWinners}, {@link EffectiveReadService#heldWinner} and
 * {@link EffectiveReadService#heldPayload} answer the layer-merge winners of a kind the activation does not resolve, with
 * no state and without the in-force resolution; the skill route of {@link EffectiveReadService#artifact} answers under a
 * tag of its own; and the templated read refuses a listed kind the activation does not resolve (T008).
 *
 * <p>The closure: core (built-in) holds the asset run and the asset logo, proj (project) its own run, which wins; core
 * holds the skill land-pr, which the activation does not select.
 */
class EffectiveReadServiceHeldTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";
    private static final ArtifactRef LOGO = new ArtifactRef(ArtifactKind.ASSET, "logo");
    private static final ArtifactRef RUN = new ArtifactRef(ArtifactKind.ASSET, "run");
    private static final ArtifactRef SKILL = new ArtifactRef(ArtifactKind.SKILL, "land-pr");
    private static final EffectiveHeldWinner LOGO_WINNER =
            new EffectiveHeldWinner(LOGO, "core", PackLevel.BUILT_IN, Policy.OPEN);
    private static final EffectiveHeldWinner RUN_WINNER =
            new EffectiveHeldWinner(RUN, "proj", PackLevel.PROJECT, Policy.EXTENDABLE);
    private static final EffectiveHeldWinner SKILL_WINNER =
            new EffectiveHeldWinner(SKILL, "core", PackLevel.BUILT_IN, Policy.OPEN);

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure());
        packs.listedKinds(
                List.of(ArtifactKind.DIRECTIVE, ArtifactKind.ASSET, ArtifactKind.SKILL, ArtifactKind.AGENT_PROFILE));
        packs.heldWinners(kind -> switch (kind) {
            case ASSET -> List.of(LOGO_WINNER, RUN_WINNER);
            case SKILL -> List.of(SKILL_WINNER);
            default -> List.of();
        });
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        winners.put(SKILL, new MergedSnapshot.Winner("core", PackLevel.BUILT_IN, Policy.OPEN));
        packs.merged(new MergedSnapshot(winners, Map.of(), List.of(), List.of()));
        packs.resolves(query -> new InForceAnswer(
                Map.of(
                        SKILL,
                        new ArtifactState(
                                SKILL,
                                State.INACTIVE,
                                Reason.NOT_SELECTED,
                                false,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()));
        packs.winners(ask -> Optional.of(
                new EffectiveArtifactRead(entity(ask.kind(), ask.id()), "core", PackLevel.BUILT_IN, Optional.empty())));
        service = new EffectiveReadService(repository, packs);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("core"), PackRef.of("proj")))
                                .build()
                                .document())
                .orElseThrow();
    }

    private static ClosureSnapshot closure() {
        return ClosureSnapshots.snapshot()
                .pack("core", "1.0.0")
                .pack("proj", "1.0.0")
                .held(ArtifactKind.SKILL, "land-pr")
                .fingerprint(SOURCE)
                .closureFingerprint(CLOSURE)
                .build();
    }

    private static <T> T found(Read<T> read) {
        assertThat(read).isInstanceOf(Read.Found.class);
        return ((Read.Found<T>) read).value();
    }

    private static <T> String etag(Read<T> read) {
        return switch (read) {
            case Read.Found<T> found -> found.etag();
            case Read.NotModified<T> notModified -> notModified.etag();
        };
    }

    // ---- the list of the winners of one kind

    @Test
    void theAssetListIsTheSessionsHeldWinnersWithTheFingerprintsAndNoStateIsResolved() {
        EffectiveReadService.HeldWinners held = found(service.heldWinners(ID, ArtifactKind.ASSET, null));

        assertThat(held.winners()).containsExactly(LOGO_WINNER, RUN_WINNER);
        assertThat(held.stored().id()).isEqualTo(ID);
        assertThat(held.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(held.governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(packs.calls()).as("one session").isEqualTo(1);
        assertThat(packs.heldAsks()).containsExactly(ArtifactKind.ASSET);
        assertThat(packs.queries()).as("no in-force resolution").isEmpty();
        assertThat(packs.effectiveAsks()).as("no entity is read").isEmpty();
    }

    @Test
    void theSkillListIsTheSkillWinners() {
        assertThat(found(service.heldWinners(ID, ArtifactKind.SKILL, null)).winners())
                .containsExactly(SKILL_WINNER);
    }

    @Test
    void eachListHasATagOfItsOwnReadAndAMatchingIfNoneMatchIsNotModifiedWithoutAskingTheWinners() {
        String assets = etag(service.heldWinners(ID, ArtifactKind.ASSET, null));
        String skills = etag(service.heldWinners(ID, ArtifactKind.SKILL, null));

        assertThat(assets)
                .isEqualTo(
                        EffectiveTag.winners(EffectiveTag.Read.ASSETS, ArtifactKind.ASSET, stored.revision(), closure())
                                .token());
        assertThat(skills)
                .isEqualTo(
                        EffectiveTag.winners(EffectiveTag.Read.SKILLS, ArtifactKind.SKILL, stored.revision(), closure())
                                .token())
                .isNotEqualTo(assets);
        int asked = packs.heldAsks().size();

        Read<EffectiveReadService.HeldWinners> again =
                service.heldWinners(ID, ArtifactKind.ASSET, "\"" + assets + "\"");

        assertThat(again).isEqualTo(new Read.NotModified<EffectiveReadService.HeldWinners>(assets));
        assertThat(packs.heldAsks()).hasSize(asked);
        assertThat(service.heldWinners(ID, ArtifactKind.ASSET, "\"" + skills + "\""))
                .as("the tag of the skill list never matches the asset list")
                .isInstanceOf(Read.Found.class);
    }

    @Test
    void noOtherKindIsListedByTheHeldReadsAndNothingIsRead() {
        assertThatThrownBy(() -> service.heldWinners(ID, ArtifactKind.DIRECTIVE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listEffectiveArtifacts");
        assertThat(packs.calls()).isZero();
    }

    @Test
    void anUnknownActivationIsActivationNotFoundBeforeThePacksAreRead() {
        ActivationId nope = new ActivationId("nope");
        assertThatThrownBy(() -> service.heldWinners(nope, ArtifactKind.ASSET, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThatThrownBy(() -> service.heldWinner(nope, ArtifactKind.ASSET, "run", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThatThrownBy(() -> service.heldPayload(nope, "run", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    // ---- one asset and its payload

    @Test
    void anAssetIsItsWinnerWithTheFingerprintsAndNoStateIsResolved() {
        EffectiveReadService.HeldAnswer answer = found(service.heldWinner(ID, ArtifactKind.ASSET, "run", null));

        assertThat(answer.winner()).isEqualTo(RUN_WINNER);
        assertThat(answer.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(answer.governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(packs.queries()).isEmpty();
        assertThat(packs.merges())
                .as("the activation-domain merge holds no asset and is not asked")
                .isZero();
    }

    @Test
    void theAssetAndItsPayloadHaveTagsOfTheirOwnReadsAndEachAnswers304ToItsOwnTag() {
        EffectiveTag.ArtifactQuery run = new EffectiveTag.ArtifactQuery(ArtifactKind.ASSET, "run", 0);
        String item = etag(service.heldWinner(ID, ArtifactKind.ASSET, "run", null));
        String payload = etag(service.heldPayload(ID, "run", null));

        assertThat(item)
                .isEqualTo(EffectiveTag.winner(EffectiveTag.Read.ASSET, stored.revision(), closure(), run)
                        .token());
        assertThat(payload)
                .isEqualTo(EffectiveTag.winner(EffectiveTag.Read.ASSET_PAYLOAD, stored.revision(), closure(), run)
                        .token())
                .isNotEqualTo(item);
        assertThat(service.heldWinner(ID, ArtifactKind.ASSET, "run", "\"" + item + "\""))
                .isEqualTo(new Read.NotModified<EffectiveReadService.HeldAnswer>(item));
        assertThat(service.heldPayload(ID, "run", "\"" + payload + "\""))
                .isEqualTo(new Read.NotModified<EffectiveReadService.HeldAnswer>(payload));
        assertThat(service.heldPayload(ID, "run", "\"" + item + "\""))
                .as("the item's tag never matches the payload")
                .isInstanceOf(Read.Found.class);
        assertThat(etag(service.heldWinner(ID, ArtifactKind.ASSET, "logo", null)))
                .as("another asset, another tag")
                .isNotEqualTo(item);
    }

    @Test
    void anAssetNoPackOfTheClosureHoldsIsArtifactNotFoundWhateverIfNoneMatchSays() {
        for (String ifNoneMatch : new String[] {null, "*"}) {
            assertThatThrownBy(() -> service.heldWinner(ID, ArtifactKind.ASSET, "ghost", ifNoneMatch))
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                        assertThat(refusal.getMessage())
                                .isEqualTo("No pack of the activation's closure holds the asset ghost.");
                    });
            assertThatThrownBy(() -> service.heldPayload(ID, "ghost", ifNoneMatch))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        }
    }

    @Test
    void aRefusalOfTheMergeIsTheAnswerUnchanged() {
        packs.heldWinners(kind -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a layer override the lock forbids");
        });

        assertThatThrownBy(() -> service.heldWinner(ID, ArtifactKind.ASSET, "run", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void aKindTheActivationResolvesIsNotReadAsAHeldWinner() {
        assertThatThrownBy(() -> service.heldWinner(ID, ArtifactKind.SKILL, "land-pr", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(packs.calls()).isZero();
    }

    // ---- the skill route

    @Test
    void theSkillRouteAnswersTheSkillWhateverItsStateUnderATagOfItsOwnRead() {
        Read<EffectiveArtifactAnswer> read = service.artifact(ID, ArtifactKind.SKILL, "land-pr", 0, null);
        EffectiveArtifactAnswer answer = found(read);

        assertThat(answer.winner().state()).isEqualTo(State.INACTIVE);
        assertThat(answer.winner().reason()).isEqualTo(Reason.NOT_SELECTED);
        assertThat(answer.artifact().localId()).isEqualTo("land-pr");
        assertThat(answer.governance()).isEmpty();
        String skill = etag(read);
        assertThat(skill)
                .isEqualTo(EffectiveTag.winner(
                                EffectiveTag.Read.SKILL,
                                stored.revision(),
                                closure(),
                                new EffectiveTag.ArtifactQuery(ArtifactKind.SKILL, "land-pr", 0))
                        .token());
        assertThat(etag(service.artifact(ID, "skill", "land-pr", 0, null)))
                .as("the generic route answers another body for the same skill, so it has another tag")
                .isNotEqualTo(skill);
    }

    // ---- T008: the templated read

    @Test
    void theTemplatedReadRefusesAnAssetWithKindNotFoundNamingTheLiteralPathBeforeThePacksAreRead() {
        assertThatThrownBy(() -> service.artifact(ID, "asset", "run", 0, null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("The templated effective read renders no asset; read it at "
                                    + "/activations/a-1/artifacts/asset/run.");
                });
        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.ASSET, "run", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void theTemplatedReadJudgesTheActivationBeforeTheKind() {
        assertThatThrownBy(() -> service.artifact(new ActivationId("nope"), "asset", "run", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
    }

    @Test
    void theTemplatedReadStillServesAnActivatableListedKind() {
        assertThat(service.artifact(ID, "skill", "land-pr", 0, null)).isInstanceOf(Read.Found.class);
    }

    private static CharterArtifact entity(ArtifactKind kind, String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return kind;
            }

            @Override
            public PackIdentifier packId() {
                return new PackIdentifier("core");
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
