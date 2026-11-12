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
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The generic activation-scoped read of one artifact, {@link EffectiveReadService#artifact} (WP06, FR-007, decisions
 * {@code 01M70ZFPHZTBH670GF7046XDW0} and {@code 01M710DQEMV7VPKDNQPWKPGD4F}): the layer-merge winner with its state, from
 * one session; the kind gate is the listed kinds of the packs slice and judged after the activation and before the packs
 * are read; an artifact no pack of the closure holds is {@code artifact_not_found}; a matching {@code If-None-Match}
 * asks neither the entity nor the in-force resolution.
 */
class EffectiveReadServiceArtifactTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final ArtifactRef A = new ArtifactRef(ArtifactKind.DIRECTIVE, "A");
    private static final ArtifactRef B = new ArtifactRef(ArtifactKind.DIRECTIVE, "B");
    private static final ArtifactRef C = new ArtifactRef(ArtifactKind.DIRECTIVE, "C");
    private static final ArtifactRef P = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "P");
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";
    private static final List<ArtifactKind> LISTED = List.of(
            ArtifactKind.TACTIC,
            ArtifactKind.DIRECTIVE,
            ArtifactKind.STYLEGUIDE,
            ArtifactKind.PARADIGM,
            ArtifactKind.TOOLGUIDE,
            ArtifactKind.ASSET,
            ArtifactKind.SKILL,
            ArtifactKind.AGENT_PROFILE);

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure(CLOSURE));
        packs.listedKinds(LISTED);
        packs.merged(merged());
        packs.resolves(query -> answer());
        packs.winners(ask -> Optional.of(read(ask.kind(), ask.id())));
        service = new EffectiveReadService(repository, packs);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("main")))
                                .selected(ArtifactKind.DIRECTIVE, List.of("A"))
                                .selected(ArtifactKind.AGENT_PROFILE, List.of("P"))
                                .build()
                                .document())
                .orElseThrow();
    }

    private static ClosureSnapshot closure(String closureFingerprint) {
        return ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .pack("house", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "A", "B", "C")
                .held(ArtifactKind.AGENT_PROFILE, "P")
                .fingerprint(SOURCE)
                .closureFingerprint(closureFingerprint)
                .build();
    }

    /** A is held by main and replaced by house at the project level; B is held by main; C and P are held by main. */
    private static MergedSnapshot merged() {
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        winners.put(A, new MergedSnapshot.Winner("house", PackLevel.PROJECT, Policy.EXTENDABLE));
        winners.put(B, new MergedSnapshot.Winner("main", PackLevel.ORG, Policy.OPEN));
        winners.put(C, new MergedSnapshot.Winner("main", PackLevel.ORG, Policy.OPEN));
        winners.put(P, new MergedSnapshot.Winner("main", PackLevel.ORG, Policy.OPEN));
        MergedSnapshot.LayerOverride override = new MergedSnapshot.LayerOverride(
                NodeRef.of(A),
                new MergedSnapshot.LayerOverride.Side("house", PackLevel.PROJECT),
                new MergedSnapshot.LayerOverride.Side("main", PackLevel.ORG));
        MergedSnapshot.Edge suggests =
                new MergedSnapshot.Edge(P, C, RelationType.SUGGESTS, Optional.of("when"), Optional.of("why"));
        MergedSnapshot.Edge requires =
                new MergedSnapshot.Edge(P, B, RelationType.REQUIRES, Optional.empty(), Optional.empty());
        return new MergedSnapshot(winners, Map.of(), List.of(suggests, requires), List.of(override));
    }

    private static ArtifactState state(ArtifactRef ref, State state, Reason reason) {
        return new ArtifactState(ref, state, reason, false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static InForceAnswer answer() {
        Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>();
        states.put(A, state(A, State.ACTIVE, Reason.SELECTED));
        states.put(B, state(B, State.INACTIVE, Reason.NOT_SELECTED));
        states.put(C, state(C, State.INACTIVE, Reason.NOT_SELECTED));
        states.put(P, state(P, State.ACTIVE, Reason.SELECTED));
        return new InForceAnswer(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    /** What the session reads for the winner: the entity of the pack that holds it, and a profile's governance. */
    private static EffectiveArtifactRead read(ArtifactKind kind, String id) {
        return read(
                kind,
                id,
                List.of(
                        new GovernanceFacts.Entry(
                                B,
                                GovernanceFacts.Step.REQUIRES,
                                RelationType.REQUIRES,
                                P,
                                1,
                                Optional.of("edge when"),
                                Optional.of("edge reason")),
                        new GovernanceFacts.Entry(
                                A,
                                GovernanceFacts.Step.SUGGESTS,
                                RelationType.SUGGESTS,
                                P,
                                1,
                                Optional.of("a when"),
                                Optional.of("a why"))));
    }

    private static EffectiveArtifactRead read(ArtifactKind kind, String id, List<GovernanceFacts.Entry> entries) {
        return read(
                kind,
                id,
                entries,
                List.of(new GovernanceFacts.Candidate(C, P, 1, Optional.of("when"), Optional.of("why"))));
    }

    private static EffectiveArtifactRead read(
            ArtifactKind kind,
            String id,
            List<GovernanceFacts.Entry> entries,
            List<GovernanceFacts.Candidate> candidates) {
        ArtifactRef ref = new ArtifactRef(kind, id);
        String holder = ref.equals(A) ? "house" : "main";
        PackLevel level = ref.equals(A) ? PackLevel.PROJECT : PackLevel.ORG;
        Optional<GovernanceFacts> governance = kind == ArtifactKind.AGENT_PROFILE
                ? Optional.of(new GovernanceFacts(P, entries, candidates))
                : Optional.empty();
        return new EffectiveArtifactRead(entity(kind, id, holder), holder, level, governance);
    }

    private static CharterArtifact entity(ArtifactKind kind, String id, String pack) {
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
                return "Copy of " + id + " in " + pack;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }

    private EffectiveArtifactAnswer answered(ArtifactKind kind, String id, int depth) {
        Read<EffectiveArtifactAnswer> read = service.artifact(ID, kind, id, depth, null);
        assertThat(read).isInstanceOf(Read.Found.class);
        return ((Read.Found<EffectiveArtifactAnswer>) read).value();
    }

    // ---- the winner and its state

    @Test
    void theWinnerIsTheLayerMergeWinnerWithItsPackLevelEntityAndState() {
        EffectiveArtifactAnswer answer = answered(ArtifactKind.DIRECTIVE, "A", 0);

        assertThat(answer.winner().ref()).isEqualTo(A);
        assertThat(answer.winner().packId())
                .as("the project's copy replaces main's")
                .isEqualTo("house");
        assertThat(answer.winner().level()).isEqualTo(PackLevel.PROJECT);
        assertThat(answer.winner().policy()).isEqualTo(Policy.EXTENDABLE);
        assertThat(answer.winner().state()).isEqualTo(State.ACTIVE);
        assertThat(answer.winner().reason()).isEqualTo(Reason.SELECTED);
        assertThat(answer.artifact().label()).isEqualTo("Copy of A in house");
        assertThat(answer.artifact().packId().value()).isEqualTo("house");
        assertThat(answer.governance()).as("a directive has no governance").isEmpty();
        assertThat(answer.stored()).isEqualTo(stored);
        assertThat(answer.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(answer.governanceFingerprint())
                .as("the closure's own digest")
                .isEqualTo(CLOSURE);
    }

    @Test
    void anInactiveWinnerIsAnsweredWithItsStateAndReason() {
        EffectiveArtifactAnswer answer = answered(ArtifactKind.DIRECTIVE, "B", 0);

        assertThat(answer.winner().state()).isEqualTo(State.INACTIVE);
        assertThat(answer.winner().reason()).isEqualTo(Reason.NOT_SELECTED);
        assertThat(answer.winner().packId()).isEqualTo("main");
        assertThat(answer.winner().level()).isEqualTo(PackLevel.ORG);
    }

    @Test
    void aWinnerReachedOnlyThroughASuggestsEdgeCarriesTheSuggestion() {
        EffectiveSet.EffectiveArtifact suggested =
                answered(ArtifactKind.DIRECTIVE, "C", 0).winner();

        assertThat(suggested.suggestion()).isPresent();
        assertThat(suggested.suggestion().orElseThrow().when()).contains("when");
        assertThat(suggested.suggestion().orElseThrow().reason()).contains("why");
    }

    @Test
    void aProfilesGovernanceIsAnsweredFromTheSameSessionWithTheStateOfEachEntry() {
        EffectiveArtifactAnswer answer = answered(ArtifactKind.AGENT_PROFILE, "P", 1);

        assertThat(answer.winner().state()).isEqualTo(State.ACTIVE);
        EffectiveArtifactAnswer.Governance governance = answer.governance().orElseThrow();
        assertThat(governance.entries()).hasSize(2);
        assertThat(governance.entries().getFirst().artifact().ref()).isEqualTo(B);
        assertThat(governance.entries().getFirst().artifact().state()).isEqualTo(State.INACTIVE);
        assertThat(governance.entries().getFirst().step()).isEqualTo(GovernanceFacts.Step.REQUIRES);
        assertThat(governance.entries().getFirst().relation()).isEqualTo(RelationType.REQUIRES);
        assertThat(governance.entries().getFirst().via()).isEqualTo(P);
        assertThat(governance.entries().getFirst().hops()).isEqualTo(1);
        assertThat(governance.suggested())
                .as("an out-of-force candidate is a suggestion of the effective set")
                .extracting(entry -> entry.artifact().ref())
                .containsExactly(C);
        assertThat(governance.suggested().getFirst().artifact().suggestion()).isPresent();
        assertThat(governance.suggested().getFirst().edge())
                .as("an out-of-force suggestion carries the words of its edge, as an entry in force does")
                .isEqualTo(new EffectiveArtifactAnswer.EdgeWords(Optional.of("when"), Optional.of("why")));
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void everyEntryCarriesTheWordsOfItsEdgeWhateverItsRelation() {
        EffectiveArtifactAnswer.Governance governance =
                answered(ArtifactKind.AGENT_PROFILE, "P", 1).governance().orElseThrow();

        EffectiveArtifactAnswer.Entry requires = governance.entries().getFirst();
        assertThat(requires.relation()).isEqualTo(RelationType.REQUIRES);
        assertThat(requires.edge())
                .as("a requires entry carries the words of its edge")
                .isEqualTo(new EffectiveArtifactAnswer.EdgeWords(Optional.of("edge when"), Optional.of("edge reason")));
        EffectiveArtifactAnswer.Entry suggests = governance.entries().get(1);
        assertThat(suggests.artifact().ref()).isEqualTo(A);
        assertThat(suggests.artifact().state()).as("in force").isEqualTo(State.ACTIVE);
        assertThat(suggests.edge())
                .isEqualTo(new EffectiveArtifactAnswer.EdgeWords(Optional.of("a when"), Optional.of("a why")));
    }

    @Test
    void anEntryWhoseEdgeStatesNeitherWordCarriesAnEmptyEdgeInForceAndOutOfForce() {
        packs.winners(ask -> Optional.of(read(
                ask.kind(),
                ask.id(),
                List.of(
                        new GovernanceFacts.Entry(
                                A,
                                GovernanceFacts.Step.SUGGESTS,
                                RelationType.SUGGESTS,
                                P,
                                1,
                                Optional.empty(),
                                Optional.empty()),
                        new GovernanceFacts.Entry(
                                B,
                                GovernanceFacts.Step.REQUIRES,
                                RelationType.REQUIRES,
                                P,
                                1,
                                Optional.empty(),
                                Optional.empty())),
                List.of(new GovernanceFacts.Candidate(C, P, 1, Optional.empty(), Optional.empty())))));

        EffectiveArtifactAnswer.Governance governance =
                answered(ArtifactKind.AGENT_PROFILE, "P", 1).governance().orElseThrow();

        assertThat(governance.entries().get(0).artifact().state())
                .as("A is in force")
                .isEqualTo(State.ACTIVE);
        assertThat(governance.entries().get(0).edge()).isEqualTo(EffectiveArtifactAnswer.EdgeWords.NONE);
        assertThat(governance.entries().get(1).artifact().state())
                .as("B is out of force")
                .isEqualTo(State.INACTIVE);
        assertThat(governance.entries().get(1).edge()).isEqualTo(EffectiveArtifactAnswer.EdgeWords.NONE);
        assertThat(governance.suggested().getFirst().edge())
                .as("the suggested path says the same as the entries do")
                .isEqualTo(EffectiveArtifactAnswer.EdgeWords.NONE);
    }

    @Test
    void anEntryWhoseEdgeStatesOnlyOneWordCarriesThatWord() {
        packs.winners(ask -> Optional.of(read(
                ask.kind(),
                ask.id(),
                List.of(new GovernanceFacts.Entry(
                        A,
                        GovernanceFacts.Step.REQUIRES,
                        RelationType.REQUIRES,
                        P,
                        1,
                        Optional.empty(),
                        Optional.of("only why"))))));

        EffectiveArtifactAnswer.Governance governance =
                answered(ArtifactKind.AGENT_PROFILE, "P", 1).governance().orElseThrow();

        assertThat(governance.entries().getFirst().edge().when()).isEmpty();
        assertThat(governance.entries().getFirst().edge().reason()).contains("only why");
    }

    @Test
    void theDepthAndTheDocumentsRequestAreAskedOfTheSession() {
        answered(ArtifactKind.AGENT_PROFILE, "P", 1);

        assertThat(packs.effectiveAsks()).hasSize(1);
        FakeAvailablePacks.EffectiveAsk ask = packs.effectiveAsks().getFirst();
        assertThat(ask.kind()).isEqualTo(ArtifactKind.AGENT_PROFILE);
        assertThat(ask.id()).isEqualTo("P");
        assertThat(ask.depth()).isEqualTo(1);
        assertThat(ask.query()).isEqualTo(EffectiveSet.queryOf(stored.document()));
    }

    @Test
    void oneReadOpensOneSessionAndAsksTheMergeAndTheResolutionOnce() {
        answered(ArtifactKind.DIRECTIVE, "A", 0);

        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.lastRoots()).contains(List.of("main"));
        assertThat(packs.merges()).isEqualTo(1);
        assertThat(packs.queries()).hasSize(1);
        assertThat(packs.effectiveAsks()).hasSize(1);
    }

    // ---- the refusals

    @Test
    void theKindsAnEntryCanBeLinkedByAreTheListedKindsOfThePacksSliceAndNotAConstantOfThisOne() {
        assertThat(service.listedKinds()).containsExactlyElementsOf(LISTED);

        packs.listedKinds(List.of(ArtifactKind.DIRECTIVE));

        assertThat(service.listedKinds()).containsExactly(ArtifactKind.DIRECTIVE);
    }

    @Test
    void anArtifactNoPackOfTheClosureHoldsIsArtifactNotFoundAndNoEntityIsRead() {
        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.DIRECTIVE, "GHOST", 0, null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refusal.getMessage()).contains("directive").contains("GHOST");
                });

        assertThat(packs.effectiveAsks()).isEmpty();
        assertThat(packs.queries()).isEmpty();
    }

    @Test
    void anIdOfAnotherKindIsNotTheArtifact() {
        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.TACTIC, "A", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
    }

    @Test
    void anUnknownActivationIsActivationNotFoundAndNoPackIsRead() {
        assertThatThrownBy(() -> service.artifact(new ActivationId("nope"), ArtifactKind.DIRECTIVE, "A", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThatThrownBy(() -> service.artifact(new ActivationId("nope"), "not-a-kind", "A", 0, null))
                .as("the activation is judged before the kind")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));

        assertThat(packs.calls()).isZero();
    }

    @Test
    void aKindTheListDoesNotHoldIsKindNotFoundBeforeAnyPackIsRead() {
        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.TEMPLATE, "A", 0, null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND);
                    assertThat(refusal.getMessage()).contains("template");
                });
        assertThatThrownBy(() -> service.artifact(ID, "procedure", "A", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND));
        assertThatThrownBy(() -> service.artifact(ID, "Directive", "A", 0, null))
                .as("a token is exact")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND));

        assertThat(packs.calls()).isZero();
    }

    @Test
    void theKindGateFollowsTheListedKindsOfThePacksSliceAndNotAConstantOfThisOne() {
        packs.listedKinds(List.of(ArtifactKind.DIRECTIVE, ArtifactKind.MISSION_TYPE));

        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.TACTIC, "A", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND));
        assertThat(answered(ArtifactKind.DIRECTIVE, "A", 0).winner().ref()).isEqualTo(A);
        assertThat(service.artifact(ID, "directive", "A", 0, null)).isInstanceOf(Read.Found.class);
    }

    @Test
    void aRefusalOfTheSessionIsTheAnswerUnchanged() {
        packs.winners(ask -> {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, "the packs changed");
        });

        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.DIRECTIVE, "A", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aWinnerTheMergeHoldsAndTheSessionDoesNotAnswerIsAFaultAndNotANotFound() {
        packs.winners(ask -> Optional.empty());

        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.DIRECTIVE, "A", 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directive:A");
    }

    // ---- the tag

    private String tag(ArtifactKind kind, String id, int depth) {
        Read<EffectiveArtifactAnswer> read = service.artifact(ID, kind, id, depth, null);
        return ((Read.Found<EffectiveArtifactAnswer>) read).etag();
    }

    @Test
    void theTagIsTheStrongTagOfTheArtifactReadOverTheRevisionTheClosureAndTheQuery() {
        String expected = EffectiveTag.artifact(
                        stored.revision(),
                        closure(CLOSURE),
                        new EffectiveTag.ArtifactQuery(ArtifactKind.DIRECTIVE, "A", 0))
                .token();

        assertThat(tag(ArtifactKind.DIRECTIVE, "A", 0)).isEqualTo(expected);
    }

    @Test
    void anAgentProfilesTagNamesTheProfileRead() {
        String expected = EffectiveTag.profile(
                        stored.revision(),
                        closure(CLOSURE),
                        new EffectiveTag.ArtifactQuery(ArtifactKind.AGENT_PROFILE, "P", 1))
                .token();

        assertThat(tag(ArtifactKind.AGENT_PROFILE, "P", 1)).isEqualTo(expected);
        assertThat(expected)
                .isNotEqualTo(EffectiveTag.artifact(
                                stored.revision(),
                                closure(CLOSURE),
                                new EffectiveTag.ArtifactQuery(ArtifactKind.AGENT_PROFILE, "P", 1))
                        .token());
        // the generic route answers a different body for the same profile, so it must not share the tag
        Read<EffectiveArtifactAnswer> generic = service.artifact(ID, "agent_profile", "P", 1, null);
        assertThat(((Read.Found<EffectiveArtifactAnswer>) generic).etag()).isNotEqualTo(expected);
        assertThat(((Read.Found<EffectiveArtifactAnswer>) generic).value().governance())
                .isEmpty();
    }

    @Test
    void theTagNamesTheKindTheIdAndTheDepthAndNothingElse() {
        String a = tag(ArtifactKind.DIRECTIVE, "A", 0);

        assertThat(tag(ArtifactKind.DIRECTIVE, "A", 0)).isEqualTo(a);
        assertThat(tag(ArtifactKind.DIRECTIVE, "B", 0)).isNotEqualTo(a);
        assertThat(tag(ArtifactKind.DIRECTIVE, "A", 1)).isNotEqualTo(a);
        packs.merged(merged());
        assertThat(tag(ArtifactKind.AGENT_PROFILE, "P", 0)).isNotEqualTo(tag(ArtifactKind.AGENT_PROFILE, "P", 1));
    }

    @Test
    void theTagChangesWithAPackOfTheClosureAndWithTheRevision() {
        String before = tag(ArtifactKind.DIRECTIVE, "A", 0);

        packs.answer(closure("sha256:moved"));
        assertThat(tag(ArtifactKind.DIRECTIVE, "A", 0)).isNotEqualTo(before);
        packs.answer(closure(CLOSURE));
        assertThat(tag(ArtifactKind.DIRECTIVE, "A", 0)).isEqualTo(before);

        repository.replace(
                ID,
                Optional.of(stored.revision()),
                ActivationDocument.builder()
                        .packs(List.of(PackRef.of("main")))
                        .selected(ArtifactKind.DIRECTIVE, List.of("A", "B"))
                        .build()
                        .document());
        StoredActivation revised = repository.find(ID).orElseThrow();
        assertThat(revised.revision()).isNotEqualTo(stored.revision());
        assertThat(tag(ArtifactKind.DIRECTIVE, "A", 0)).isNotEqualTo(before);
    }

    @Test
    void aMatchingIfNoneMatchIsNotModifiedAndAsksNeitherTheEntityNorTheResolution() {
        String etag = tag(ArtifactKind.DIRECTIVE, "A", 0);
        FakeAvailablePacks before = packs;
        int asks = packs.effectiveAsks().size();
        int resolutions = packs.queries().size();

        Read<EffectiveArtifactAnswer> read = service.artifact(ID, ArtifactKind.DIRECTIVE, "A", 0, "\"" + etag + "\"");

        assertThat(read).isEqualTo(new Read.NotModified<EffectiveArtifactAnswer>(etag));
        assertThat(before.effectiveAsks()).hasSize(asks);
        assertThat(before.queries()).hasSize(resolutions);
        assertThat(service.artifact(ID, ArtifactKind.DIRECTIVE, "A", 0, "*")).isInstanceOf(Read.NotModified.class);
    }

    @Test
    void aTagOfAnotherRepresentationNeverMatches() {
        String other = tag(ArtifactKind.DIRECTIVE, "B", 0);

        assertThat(service.artifact(ID, ArtifactKind.DIRECTIVE, "A", 0, "\"" + other + "\""))
                .isInstanceOf(Read.Found.class);
    }

    @Test
    void anArtifactNoPackHoldsIsNotFoundWhateverIfNoneMatchSays() {
        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.DIRECTIVE, "GHOST", 0, "*"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
    }

    @Test
    void anActivationWhosePackIsNoLongerConfiguredIsPackUnreadable() {
        packs.answer(ClosureSnapshot.unresolved(Set.of("main")));

        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.DIRECTIVE, "A", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }
}
