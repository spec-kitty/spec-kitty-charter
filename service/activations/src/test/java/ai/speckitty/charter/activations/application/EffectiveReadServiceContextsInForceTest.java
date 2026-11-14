package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
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
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The glossary contexts in force under an activation (#106, WP06; ADR-0076 decisions 1, 2 and 8):
 * {@link EffectiveReadService#contextsInForce} lists the winning copy of each glossary context whose state is in force,
 * from the one effective set of one session, under the tag of the vocabulary read it serves, with every refusal judged
 * before {@code If-None-Match}.
 *
 * <p>The closure: core (built-in) and proj (project) both hold spec-kitty-core, and proj's copy wins. core holds
 * testing, which the activation selects, qa, which it does not select, and legacy, which it denies. core also holds
 * the skill review, in force, which is no glossary context.
 */
class EffectiveReadServiceContextsInForceTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";
    private static final ArtifactRef KITTY = new ArtifactRef(ArtifactKind.GLOSSARY, "spec-kitty-core");
    private static final ArtifactRef TESTING = new ArtifactRef(ArtifactKind.GLOSSARY, "testing");
    private static final ArtifactRef QA = new ArtifactRef(ArtifactKind.GLOSSARY, "qa");
    private static final ArtifactRef LEGACY = new ArtifactRef(ArtifactKind.GLOSSARY, "legacy");
    private static final ArtifactRef REVIEW = new ArtifactRef(ArtifactKind.SKILL, "review");

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure());
        packs.listedKinds(List.of(ArtifactKind.SKILL, ArtifactKind.GLOSSARY));
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        winners.put(TESTING, new MergedSnapshot.Winner("core", PackLevel.BUILT_IN, Policy.OPEN));
        winners.put(KITTY, new MergedSnapshot.Winner("proj", PackLevel.PROJECT, Policy.EXTENDABLE));
        winners.put(QA, new MergedSnapshot.Winner("core", PackLevel.BUILT_IN, Policy.OPEN));
        winners.put(LEGACY, new MergedSnapshot.Winner("core", PackLevel.BUILT_IN, Policy.OPEN));
        winners.put(REVIEW, new MergedSnapshot.Winner("core", PackLevel.BUILT_IN, Policy.OPEN));
        packs.merged(new MergedSnapshot(winners, Map.of(), List.of(), List.of()));
        packs.resolves(query -> answer(Map.of(
                KITTY, state(KITTY, State.ACTIVE, Reason.SELECTED),
                TESTING, state(TESTING, State.ACTIVE, Reason.SELECTED),
                QA, state(QA, State.INACTIVE, Reason.NOT_SELECTED),
                LEGACY, state(LEGACY, State.EXCLUDED, Reason.DEACTIVATED),
                REVIEW, state(REVIEW, State.ACTIVE, Reason.SELECTED))));
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

    private static InForceAnswer answer(Map<ArtifactRef, ArtifactState> states) {
        return new InForceAnswer(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private static ArtifactState state(ArtifactRef ref, State state, Reason reason) {
        return new ArtifactState(ref, state, reason, false, List.of(), List.of(), Optional.empty());
    }

    private static ClosureSnapshot closure() {
        return ClosureSnapshots.snapshot()
                .pack("core", "1.0.0")
                .pack("proj", "1.0.0")
                .held(ArtifactKind.GLOSSARY, "spec-kitty-core")
                .held(ArtifactKind.GLOSSARY, "testing")
                .held(ArtifactKind.GLOSSARY, "qa")
                .held(ArtifactKind.GLOSSARY, "legacy")
                .held(ArtifactKind.SKILL, "review")
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
            case Read.Found<T> answered -> answered.etag();
            case Read.NotModified<T> notModified -> notModified.etag();
        };
    }

    private EffectiveReadService.HeldWinners inForce() {
        return found(service.contextsInForce(ID, EffectiveTag.Read.VOCABULARY_TERMS, null));
    }

    @Test
    void theSelectedContextsAreInForceInIdOrderAndADeselectedOrDeniedOneAndAnotherKindAreNot() {
        EffectiveReadService.HeldWinners answer = inForce();

        assertThat(answer.winners())
                .containsExactly(
                        new EffectiveHeldWinner(KITTY, "proj", PackLevel.PROJECT, Policy.EXTENDABLE),
                        new EffectiveHeldWinner(TESTING, "core", PackLevel.BUILT_IN, Policy.OPEN));
        assertThat(answer.stored()).isEqualTo(stored);
        assertThat(answer.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(answer.governanceFingerprint()).isEqualTo(CLOSURE);
    }

    @Test
    void theProjectCopyThatOverridesTheBuiltInCopyIsTheOneInForce() {
        assertThat(inForce().winners())
                .filteredOn(winner -> winner.ref().equals(KITTY))
                .singleElement()
                .satisfies(winner -> {
                    assertThat(winner.packId()).isEqualTo("proj");
                    assertThat(winner.level()).isEqualTo(PackLevel.PROJECT);
                });
    }

    @Test
    void theContextsComeFromOneSessionWithOneMergeAndOneResolution() {
        inForce();

        assertThat(packs.calls()).as("one session").isEqualTo(1);
        assertThat(packs.merges()).as("one merge").isEqualTo(1);
        assertThat(packs.queries()).as("one in-force resolution").hasSize(1);
        assertThat(packs.heldAsks())
                .as("the closure is not walked a second time")
                .isEmpty();
        assertThat(packs.effectiveAsks()).as("no entity is read").isEmpty();
    }

    @Test
    void anActivationWithNoGlossaryInForceAnswersAnEmptyList() {
        packs.resolves(query -> answer(Map.of(
                KITTY, state(KITTY, State.INACTIVE, Reason.NOT_SELECTED),
                TESTING, state(TESTING, State.EXCLUDED, Reason.DEACTIVATED),
                QA, state(QA, State.INACTIVE, Reason.NOT_SELECTED),
                LEGACY, state(LEGACY, State.EXCLUDED, Reason.DEACTIVATED),
                REVIEW, state(REVIEW, State.ACTIVE, Reason.SELECTED))));

        assertThat(inForce().winners()).isEmpty();
    }

    @Test
    void anUnknownActivationIsActivationNotFoundBeforeThePacksAreReadWhateverIfNoneMatchSays() {
        for (String ifNoneMatch : new String[] {null, "*"}) {
            assertThatThrownBy(() -> service.contextsInForce(
                            new ActivationId("nope"), EffectiveTag.Read.VOCABULARY_TERMS, ifNoneMatch))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        }
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aRefusalOfTheClosureIsTheAnswerWhateverIfNoneMatchSays() {
        CharterRefusal conflict = new CharterRefusal(RefusalCode.PACK_SIBLING_CONFLICT, "two copies of qa");
        packs.resolves(query -> {
            throw conflict;
        });

        assertThatThrownBy(() -> service.contextsInForce(ID, EffectiveTag.Read.VOCABULARY_TABOOS, "*"))
                .isSameAs(conflict);
    }

    @ParameterizedTest
    @EnumSource(
            value = EffectiveTag.Read.class,
            names = {"VOCABULARY_TERMS", "VOCABULARY_CONTEXTIVE", "VOCABULARY_TABOOS"})
    void eachVocabularyReadAnswersUnderItsOwnTagAndAMatchingIfNoneMatchIsNotModified(EffectiveTag.Read read) {
        String etag = etag(service.contextsInForce(ID, read, null));

        assertThat(etag)
                .isEqualTo(EffectiveTag.vocabulary(read, stored.revision(), closure())
                        .token());
        assertThat(service.contextsInForce(ID, read, "\"" + etag + "\""))
                .isEqualTo(new Read.NotModified<EffectiveReadService.HeldWinners>(etag));
    }

    @Test
    void theThreeVocabularyReadsNeverShareATag() {
        Set<String> tags = new HashSet<>(List.of(
                etag(service.contextsInForce(ID, EffectiveTag.Read.VOCABULARY_TERMS, null)),
                etag(service.contextsInForce(ID, EffectiveTag.Read.VOCABULARY_CONTEXTIVE, null)),
                etag(service.contextsInForce(ID, EffectiveTag.Read.VOCABULARY_TABOOS, null)),
                etag(service.heldWinners(ID, ArtifactKind.GLOSSARY, null))));

        assertThat(tags)
                .as("three vocabulary reads and the glossary list: one tag each")
                .hasSize(4);
    }

    @Test
    void aReadThatIsNoVocabularyReadIsAFault() {
        assertThatThrownBy(() -> service.contextsInForce(ID, EffectiveTag.Read.GLOSSARIES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The read GLOSSARIES is no vocabulary read of an activation.");
    }
}
