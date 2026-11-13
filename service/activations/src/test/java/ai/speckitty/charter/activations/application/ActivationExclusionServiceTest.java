package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deactivate and re-activate over an in-memory register and a counting fake of the packs. The fixture's graph: D
 * requires t, t2 and t3; D2 requires t2; e2 enhances t3. Every write reads the packs once (NFR-007) for the target's
 * walk and the shared walk; a target on the floor is refused before any walk.
 */
class ActivationExclusionServiceTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final ArtifactRef D = new ArtifactRef(ArtifactKind.DIRECTIVE, "D");
    private static final ArtifactRef D2 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D2");
    private static final ArtifactRef T = new ArtifactRef(ArtifactKind.TACTIC, "t");
    private static final ArtifactRef T2 = new ArtifactRef(ArtifactKind.TACTIC, "t2");
    private static final ArtifactRef T3 = new ArtifactRef(ArtifactKind.TACTIC, "t3");
    private static final ArtifactRef E2 = new ArtifactRef(ArtifactKind.TACTIC, "e2");
    private static final ArtifactRef RQ = new ArtifactRef(ArtifactKind.DIRECTIVE, "RQ");
    private static final ArtifactRef F = new ArtifactRef(ArtifactKind.DIRECTIVE, "F");
    private static final Optional<List<String>> ALL = Optional.of(List.of("all"));

    private final CountingRepository repository = new CountingRepository();
    private FakeAvailablePacks packs;
    private ActivationExclusionService service;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D", "D2", "LK", "RQ", "F")
                .held(ArtifactKind.TACTIC, "t", "t2", "t3", "e2")
                .held(ArtifactKind.MISSION_TYPE, "research", "mt-lk", "mt-rq")
                .lockedBy("directive:LK", "main")
                .requiredBy("directive:RQ", "main")
                .lockedBy("mission_type:mt-lk", "main")
                .requiredBy("mission_type:mt-rq", "main")
                .build());
        service = new ActivationExclusionService(new ActivationWrites(repository, packs));
        packs.walks(plan -> walk(plan, FIXTURE));
    }

    /** The fixture's graph: each source with its typed edges. */
    private static final Map<ArtifactRef, List<Reach.Via>> FIXTURE = Map.of(
            D,
            List.of(
                    new Reach.Via(T, RelationType.REQUIRES),
                    new Reach.Via(T2, RelationType.REQUIRES),
                    new Reach.Via(T3, RelationType.REQUIRES)),
            D2,
            List.of(new Reach.Via(T2, RelationType.REQUIRES)),
            E2,
            List.of(new Reach.Via(T3, RelationType.ENHANCES)),
            RQ,
            List.of(new Reach.Via(F, RelationType.SUGGESTS)));

    private StoredActivation stored(ActivationDocument.Builder builder) {
        return repository
                .create(ID, builder.packs(List.of(PackRef.of("main"))).build().document())
                .orElseThrow();
    }

    private static String quoted(StoredActivation stored) {
        return "\"" + stored.revision().token() + "\"";
    }

    private ActivationDocument now() {
        return repository.find(ID).orElseThrow().document();
    }

    @Test
    void aCascadingDeactivateUnselectsWhatOnlyTheTargetKeptAndKeepsWhatAnotherSelectedOneReaches() {
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D", "D2"))
                .selected(ArtifactKind.TACTIC, List.of("t", "t2")));

        ActivationWrites.Outcome outcome = service.deactivate(ID, "directive", "D", ALL, List.of(), quoted(before));

        assertThat(outcome.change().applied()).containsExactly(D, T);
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(outcome.change().skipped().skippedShared()).containsExactly(new SkippedReport.SharedSkip(T2, D2));
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("D2");
        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("t2");
        assertThat(now().deactivated()).containsOnlyKeys(ArtifactKind.DIRECTIVE);
        assertThat(packs.calls())
                .as("one read for the target's walk and the shared walk")
                .isEqualTo(1);
        assertThat(packs.walked()).hasSize(2);
    }

    @Test
    void aCascadingDeactivateKeepsWhatASelectedEnhancerReferences() {
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D"))
                .selected(ArtifactKind.TACTIC, List.of("e2", "t3")));

        ActivationWrites.Outcome outcome = service.deactivate(ID, "directive", "D", ALL, List.of(), quoted(before));

        assertThat(outcome.change().applied()).containsExactly(D);
        assertThat(outcome.change().skipped().skippedShared()).containsExactly(new SkippedReport.SharedSkip(T3, E2));
        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("e2", "t3");
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void aDeactivateWithoutACascadeDeniesAndUnselectsTheTargetAloneAndWalksNothing() {
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D"))
                .selected(ArtifactKind.TACTIC, List.of("t")));

        ActivationWrites.Outcome outcome =
                service.deactivate(ID, "directive", "D", Optional.empty(), List.of(), quoted(before));

        assertThat(outcome.change().applied()).containsExactly(D);
        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("t");
        assertThat(now().deactivated(ArtifactKind.DIRECTIVE)).containsExactly("D");
        assertThat(packs.walked()).isEmpty();
    }

    @Test
    void aLockedOrRequiredTargetIs409BeforeAnyWalkAndNothingIsStored() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.deactivate(ID, "directive", "LK", ALL, List.of(), quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED);
                    assertThat(refusal.requiredBy())
                            .singleElement()
                            .satisfies(pack -> assertThat(pack.packId()).isEqualTo("main"));
                });
        assertThatThrownBy(() -> service.deactivate(ID, "directive", "RQ", ALL, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(packs.walked()).isEmpty();
        assertThat(repository.replaces()).isZero();
        assertThat(repository.find(ID).orElseThrow().revision()).isEqualTo(before.revision());
    }

    @Test
    void aStoredDenyEntryIsJudgedAgainstTheDocumentTheCascadeComputes() {
        // RQ is required, F overrides it and is selected, and RQ suggests F. RQ's entry was accepted for F's sake. The
        // facts answer "satisfied" only for a document that selects F, as the resolution does.
        packs.resolves(query -> query.selected().contains(F)
                ? new InForceAnswer(
                        Map.of(
                                RQ,
                                new ArtifactState(
                                        RQ,
                                        State.INACTIVE,
                                        Reason.DEACTIVATED,
                                        true,
                                        List.of(F),
                                        List.of(),
                                        Optional.empty())),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of())
                : InForceAnswer.empty());
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("F"))
                .deactivated(ArtifactKind.DIRECTIVE, List.of("RQ")));

        service.deactivate(ID, "directive", "RQ", Optional.empty(), List.of(), quoted(before));
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow())
                .as("without a cascade F stays selected and satisfies RQ")
                .containsExactly("F");
        assertThatThrownBy(() -> service.deactivate(ID, "directive", "RQ", ALL, List.of(), quoted(before)))
                .as("the cascade unselects F, so the document it computes leaves RQ unsatisfied")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(repository.find(ID).orElseThrow().revision()).isEqualTo(before.revision());
    }

    @Test
    void deactivatingWhatIsDeniedAndNotSelectedIsANoOp() {
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("t")));

        ActivationWrites.Outcome outcome =
                service.deactivate(ID, "tactic", "t", Optional.empty(), List.of(), quoted(before));

        assertThat(outcome.stored()).isEqualTo(before);
        assertThat(outcome.change().applied()).isEmpty();
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aMissionTypeIsTakenAtThePathAsAnyKindAndARequiredOneIs409() {
        // A7 Q2: the path takes kind=mission_type; the deny itself is planned by CascadeRules (WP05, T022).
        packs.answer(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.MISSION_TYPE, "research", "software-dev")
                .requiredBy("mission_type:software-dev", "main")
                .build());
        StoredActivation before =
                stored(ActivationDocument.builder().deactivated(ArtifactKind.MISSION_TYPE, List.of("research")));

        assertThatThrownBy(() -> service.deactivate(
                        ID, "mission_type", "software-dev", Optional.empty(), List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThatThrownBy(() ->
                        service.deactivate(ID, "mission_type", "nope", Optional.empty(), List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        ActivationWrites.Outcome reactivated =
                service.reactivate(ID, "mission_type", "Mission_Type:Research", quoted(before));

        assertThat(reactivated.change().cleared())
                .containsExactly(new ArtifactRef(ArtifactKind.MISSION_TYPE, "research"));
        assertThat(reactivated.stored().document().deactivated()).isEmpty();
        assertThat(repository.replaces()).isOne();
    }

    @Test
    void aMissionTypeIsDeniedAndReactivatedLikeAnyKind() {
        // A7 Q2: deactivated.mission_type is a deny list like any other.
        ArtifactRef research = new ArtifactRef(ArtifactKind.MISSION_TYPE, "research");
        StoredActivation before =
                stored(ActivationDocument.builder().selected(ArtifactKind.MISSION_TYPE, List.of("research")));

        ActivationWrites.Outcome denied =
                service.deactivate(ID, "mission_type", "research", Optional.empty(), List.of(), quoted(before));

        assertThat(denied.change().applied()).containsExactly(research);
        assertThat(now().selected(ArtifactKind.MISSION_TYPE)).isEmpty();
        assertThat(now().deactivated(ArtifactKind.MISSION_TYPE)).containsExactly("research");

        ActivationWrites.Outcome reactivated =
                service.reactivate(ID, "mission_type", "research", quoted(denied.stored()));

        assertThat(reactivated.change().cleared()).containsExactly(research);
        assertThat(now().deactivated()).isEmpty();
    }

    @Test
    void aLockedOrRequiredMissionTypeIs409LikeAnyKindAndNothingIsStored() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.deactivate(ID, "mission_type", "mt-lk", ALL, List.of(), quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED);
                    assertThat(refusal.requiredBy())
                            .singleElement()
                            .satisfies(pack -> assertThat(pack.packId()).isEqualTo("main"));
                });
        assertThatThrownBy(() -> service.deactivate(ID, "mission_type", "mt-rq", ALL, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(packs.walked()).isEmpty();
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void anUnknownMissionTypeIs404MissionTypeNotFound() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.deactivate(ID, "mission_type", "nope", ALL, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void reactivatingClearsTheDenyEntryListsItInAppliedAndDropsTheEmptiedList() {
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("t")));

        ActivationWrites.Outcome outcome = service.reactivate(ID, "tactic", "t", quoted(before));

        assertThat(outcome.change().cleared()).containsExactly(T);
        assertThat(outcome.change().applied()).containsExactly(T);
        assertThat(now().deactivated()).isEmpty();
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void aTargetOnTheFloorWhoseDenyEntryIsAlreadyStoredIsRefusedAgainAndNothingIsStored() {
        // The named target is judged whether or not its entry is stored (WP29 fix cycle 1): the entry of LK stays
        // locked, and the entry of RQ has no override to satisfy it.
        StoredActivation before =
                stored(ActivationDocument.builder().deactivated(ArtifactKind.DIRECTIVE, List.of("LK", "RQ")));

        assertThatThrownBy(() -> service.deactivate(ID, "directive", "LK", Optional.empty(), List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED));
        assertThatThrownBy(() -> service.deactivate(ID, "directive", "RQ", Optional.empty(), List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(packs.walked()).isEmpty();
        assertThat(repository.replaces()).isZero();
        assertThat(repository.find(ID).orElseThrow().revision()).isEqualTo(before.revision());
    }

    @Test
    void aStoredDenyEntryOnTheFloorIsToleratedByADeactivateAndAReactivateOfAnotherId() {
        // Stored before an upgrade made RQ required: the entry is tolerated, only a deny the write adds is judged.
        StoredActivation before =
                stored(ActivationDocument.builder().deactivated(ArtifactKind.DIRECTIVE, List.of("RQ")));

        ActivationWrites.Outcome denied =
                service.deactivate(ID, "tactic", "t", Optional.empty(), List.of(), quoted(before));
        ActivationWrites.Outcome reactivated = service.reactivate(ID, "tactic", "t", quoted(denied.stored()));

        assertThat(denied.change().applied()).containsExactly(T);
        assertThat(reactivated.change().cleared()).containsExactly(T);
        assertThat(now().deactivated(ArtifactKind.DIRECTIVE)).containsExactly("RQ");
    }

    @Test
    void reactivatingWhatIsNotDeniedIsANoOp() {
        StoredActivation before = stored(ActivationDocument.builder());

        ActivationWrites.Outcome outcome = service.reactivate(ID, "tactic", "t", quoted(before));

        assertThat(outcome.stored().revision()).isEqualTo(before.revision());
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void theWritesRequireIfMatchAndAPathIdTheClosureHolds() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.reactivate(ID, "tactic", "t", null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.MISSING));
        assertThatThrownBy(() -> service.deactivate(ID, "tactic", "no-such", ALL, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
    }

    @Test
    void aKeptCandidateThatEnhancesAnotherCandidateKeepsItInTheSameSharedWalk() {
        // D requires c, c3 and c5; c3 requires c4. The selected styleguide e2 enhances c, and c enhances c3. The shared
        // walk from e2 crosses enhances to c, and goes on through c to c3 and c4; c5 leaves.
        ArtifactRef c = new ArtifactRef(ArtifactKind.TACTIC, "c");
        ArtifactRef c3 = new ArtifactRef(ArtifactKind.TACTIC, "c3");
        ArtifactRef c4 = new ArtifactRef(ArtifactKind.TACTIC, "c4");
        ArtifactRef c5 = new ArtifactRef(ArtifactKind.TACTIC, "c5");
        ArtifactRef e2 = new ArtifactRef(ArtifactKind.STYLEGUIDE, "e2");
        Map<ArtifactRef, List<Reach.Via>> edges = Map.of(
                D,
                List.of(
                        new Reach.Via(c, RelationType.REQUIRES),
                        new Reach.Via(c3, RelationType.REQUIRES),
                        new Reach.Via(c5, RelationType.REQUIRES)),
                c3,
                List.of(new Reach.Via(c4, RelationType.REQUIRES)),
                e2,
                List.of(new Reach.Via(c, RelationType.ENHANCES)),
                c,
                List.of(new Reach.Via(c3, RelationType.ENHANCES)));
        FakeAvailablePacks chain = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D")
                .held(ArtifactKind.TACTIC, "c", "c3", "c4", "c5")
                .held(ArtifactKind.STYLEGUIDE, "e2")
                .build());
        chain.walks(plan -> walk(plan, edges));
        ActivationExclusionService chained = new ActivationExclusionService(new ActivationWrites(repository, chain));
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D"))
                .selected(ArtifactKind.TACTIC, List.of("c", "c3", "c4", "c5"))
                .selected(ArtifactKind.STYLEGUIDE, List.of("e2")));

        ActivationWrites.Outcome outcome = chained.deactivate(ID, "directive", "D", ALL, List.of(), quoted(before));

        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("c", "c3", "c4");
        assertThat(now().selected(ArtifactKind.DIRECTIVE)).isEmpty();
        assertThat(now().deactivated(ArtifactKind.DIRECTIVE)).containsExactly("D");
        assertThat(outcome.change().applied()).containsExactly(D, c5);
        assertThat(outcome.change().skipped().skippedShared())
                .containsExactly(
                        new SkippedReport.SharedSkip(c, e2),
                        new SkippedReport.SharedSkip(c3, c),
                        new SkippedReport.SharedSkip(c4, c3));
        assertThat(chain.calls())
                .as("one read of the packs for the whole request")
                .isEqualTo(1);
        assertThat(chain.walked())
                .as("the target's own walk and one shared walk")
                .hasSize(1 + 1);
    }

    /**
     * A forward walk from the plan's starts over the typed edges whose relation the plan crosses, stopping at (and not
     * passing) the plan's stops. Each edge is held as a {@link Reach.Via} whose {@code from} is the edge's target.
     */
    private static Reach walk(ReachPlan plan, Map<ArtifactRef, List<Reach.Via>> edges) {
        Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
        Map<ArtifactRef, List<Reach.Via>> stopped = new LinkedHashMap<>();
        ArrayDeque<ArtifactRef> frontier = new ArrayDeque<>(plan.starts());
        plan.starts().forEach(start -> reached.put(start, List.of()));
        while (!frontier.isEmpty()) {
            ArtifactRef from = frontier.removeFirst();
            for (Reach.Via edge : edges.getOrDefault(from, List.of())) {
                ArtifactRef to = edge.from();
                Reach.Via via = new Reach.Via(from, edge.relation());
                if (!plan.relations().contains(edge.relation())) {
                    continue;
                }
                if (plan.stopAt().contains(to)) {
                    stopped.put(to, List.of(via));
                } else if (reached.putIfAbsent(to, List.of(via)) == null) {
                    frontier.addLast(to);
                }
            }
        }
        return new Reach(reached, stopped, Set.of(), Set.of(), Map.of());
    }

    @Test
    void aReactivateDropsTheStoredIdsTheClosureNoLongerDeclares() {
        // ADR-0059 amendment A5: GONE and old are stale; the write drops them and names them.
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("GONE"))
                .deactivated(ArtifactKind.TACTIC, List.of("old", "t")));

        ActivationWrites.Outcome outcome = service.reactivate(ID, "tactic", "t", quoted(before));

        assertThat(outcome.change().dropped())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"), new ArtifactRef(ArtifactKind.TACTIC, "old"));
        assertThat(outcome.change().cleared()).containsExactly(T);
        assertThat(outcome.change().applied()).containsExactly(T);
        assertThat(now().deactivated()).isEmpty();
        assertThat(now().selected(ArtifactKind.DIRECTIVE)).isEmpty();
    }

    @Test
    void aDeactivateDropsTheStoredIdsTheClosureNoLongerDeclares() {
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("old")));

        ActivationWrites.Outcome outcome =
                service.deactivate(ID, "tactic", "t", Optional.empty(), List.of(), quoted(before));

        assertThat(outcome.change().dropped()).containsExactly(new ArtifactRef(ArtifactKind.TACTIC, "old"));
        assertThat(outcome.change().applied()).containsExactly(T);
        assertThat(now().deactivated(ArtifactKind.TACTIC)).containsExactly("t");
    }

    @Test
    void aReactivateOfAStaleIdTheDocumentDeniesDropsItAndReportsItInDroppedNotCleared() {
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("GONE"))
                .deactivated(ArtifactKind.TACTIC, List.of("old", "t")));

        ActivationWrites.Outcome outcome = service.reactivate(ID, "tactic", "old", quoted(before));

        assertThat(outcome.change().dropped())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"), new ArtifactRef(ArtifactKind.TACTIC, "old"));
        assertThat(outcome.change().applied()).isEmpty();
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(outcome.stored().revision()).isNotEqualTo(before.revision());
        assertThat(now().deactivated(ArtifactKind.TACTIC)).containsExactly("t");
        assertThat(repository.replaces()).isEqualTo(1);
    }

    @Test
    void aDeactivateAndAReactivateInAnotherSpellingWriteTheDeclaredId() {
        // ADR-0020 D4 (operator ruling of 2026-11-06): the path id is read as a document's id is.
        StoredActivation before = stored(ActivationDocument.builder());

        ActivationWrites.Outcome denied =
                service.deactivate(ID, "directive", "directive:d2", Optional.empty(), List.of(), quoted(before));
        ActivationWrites.Outcome cleared = service.reactivate(ID, "directive", "d2", quoted(denied.stored()));

        assertThat(denied.change().applied()).containsExactly(D2);
        assertThat(cleared.change().applied()).containsExactly(D2);
        assertThat(now().deactivated(ArtifactKind.DIRECTIVE)).isEmpty();
    }

    @Test
    void aReactivateOfAStaleIdInAnotherSpellingDropsIt() {
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("old")));

        ActivationWrites.Outcome outcome = service.reactivate(ID, "tactic", "tactic:OLD", quoted(before));

        assertThat(outcome.change().dropped()).containsExactly(new ArtifactRef(ArtifactKind.TACTIC, "old"));
        assertThat(now().deactivated()).isEmpty();
    }

    @Test
    void aReactivateOfAnIdNeitherTheClosureNorTheDenyListNamesIsStill404() {
        // old is stale and selected, not denied: the re-activate removes from deactivated, so it is not its to drop.
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.TACTIC, List.of("old"))
                .deactivated(ArtifactKind.TACTIC, List.of("gone")));

        assertThatThrownBy(() -> service.reactivate(ID, "tactic", "no-such", quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThatThrownBy(() -> service.reactivate(ID, "tactic", "old", quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aDeactivateOfAStaleIdTheDocumentDeniesIsStill404() {
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("old")));

        assertThatThrownBy(() -> service.deactivate(ID, "tactic", "old", Optional.empty(), List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }
}
