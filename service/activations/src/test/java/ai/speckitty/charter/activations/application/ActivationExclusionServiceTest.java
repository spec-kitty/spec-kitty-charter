package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
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
 * requires t, t2 and t3; D2 requires t2; e2 enhances t3. Every write reads the packs once (NFR-007), however many steps
 * the shared walk's fixed point takes; a target on the floor is refused before any walk.
 */
class ActivationExclusionServiceTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final ArtifactRef D = new ArtifactRef(ArtifactKind.DIRECTIVE, "D");
    private static final ArtifactRef D2 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D2");
    private static final ArtifactRef T = new ArtifactRef(ArtifactKind.TACTIC, "t");
    private static final ArtifactRef T2 = new ArtifactRef(ArtifactKind.TACTIC, "t2");
    private static final ArtifactRef T3 = new ArtifactRef(ArtifactKind.TACTIC, "t3");
    private static final ArtifactRef E2 = new ArtifactRef(ArtifactKind.TACTIC, "e2");
    private static final Optional<List<String>> ALL = Optional.of(List.of("all"));

    private final CountingRepository repository = new CountingRepository();
    private FakeAvailablePacks packs;
    private ActivationExclusionService service;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D", "D2", "LK", "RQ")
                .held(ArtifactKind.TACTIC, "t", "t2", "t3", "e2")
                .lockedBy("directive:LK", "main")
                .requiredBy("directive:RQ", "main")
                .build());
        service = new ActivationExclusionService(new ActivationWrites(repository, packs));
        packs.walks(ActivationExclusionServiceTest::walk);
        packs.enhances(E2, T3);
    }

    /** The fixture's graph, walked forward from the plan's starts, stopping nowhere. */
    private static Reach walk(ReachPlan plan) {
        Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
        plan.starts().forEach(start -> reached.put(start, List.of()));
        if (plan.starts().contains(D) && !plan.stopAt().contains(D)) {
            List.of(T, T2, T3).forEach(to -> reached.put(to, List.of(new Reach.Via(D, RelationType.REQUIRES))));
        }
        if (plan.starts().contains(D2)) {
            reached.put(T2, List.of(new Reach.Via(D2, RelationType.REQUIRES)));
        }
        return new Reach(reached, Map.of(), Set.of(), Set.of());
    }

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

        ActivationWrites.Outcome outcome = service.deactivate(ID, "directive", "D", ALL, quoted(before));

        assertThat(outcome.change().applied()).containsExactly(D, T);
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(outcome.change().skipped().skippedShared()).containsExactly(new SkippedReport.SharedSkip(T2, D2));
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("D2");
        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("t2");
        assertThat(now().deactivated()).containsOnlyKeys(ArtifactKind.DIRECTIVE);
        assertThat(packs.calls())
                .as("one read for the target's walk and every shared step")
                .isEqualTo(1);
        assertThat(packs.walked()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void aCascadingDeactivateKeepsWhatASelectedEnhancerReferences() {
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D"))
                .selected(ArtifactKind.TACTIC, List.of("e2", "t3")));

        ActivationWrites.Outcome outcome = service.deactivate(ID, "directive", "D", ALL, quoted(before));

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

        ActivationWrites.Outcome outcome = service.deactivate(ID, "directive", "D", Optional.empty(), quoted(before));

        assertThat(outcome.change().applied()).containsExactly(D);
        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("t");
        assertThat(now().deactivated(ArtifactKind.DIRECTIVE)).containsExactly("D");
        assertThat(packs.walked()).isEmpty();
    }

    @Test
    void aLockedOrRequiredTargetIs409BeforeAnyWalkAndNothingIsStored() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.deactivate(ID, "directive", "LK", ALL, quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED);
                    assertThat(refusal.requiredBy())
                            .singleElement()
                            .satisfies(pack -> assertThat(pack.packId()).isEqualTo("main"));
                });
        assertThatThrownBy(() -> service.deactivate(ID, "directive", "RQ", ALL, quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(packs.walked()).isEmpty();
        assertThat(repository.replaces()).isZero();
        assertThat(repository.find(ID).orElseThrow().revision()).isEqualTo(before.revision());
    }

    @Test
    void deactivatingWhatIsDeniedAndNotSelectedIsANoOp() {
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("t")));

        ActivationWrites.Outcome outcome = service.deactivate(ID, "tactic", "t", Optional.empty(), quoted(before));

        assertThat(outcome.stored()).isEqualTo(before);
        assertThat(outcome.change().applied()).isEmpty();
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aMissionTypeHasNoDenyListSoItsKindIs422AtThePath() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.deactivate(ID, "mission_type", "research", ALL, quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(
                                    violation -> assertThat(violation.pointer()).isEqualTo("/kind"));
                });
        assertThatThrownBy(() -> service.reactivate(ID, "mission_type", "research", quoted(before)))
                .isInstanceOf(CharterRefusal.class);
        assertThat(packs.calls()).isZero();
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
        assertThatThrownBy(() -> service.deactivate(ID, "tactic", "no-such", ALL, quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
    }

    @Test
    void aKeptCandidateThatEnhancesAnotherCandidateKeepsItOnTheSecondSharedStep() {
        // D requires c, c3 and c5; c3 requires c4. The selected styleguide e2 enhances c, and c enhances c3. The first
        // shared step keeps c (its enhancer e2 is active); only the second, from c, keeps c3 and what c3 reaches.
        ArtifactRef c = new ArtifactRef(ArtifactKind.TACTIC, "c");
        ArtifactRef c3 = new ArtifactRef(ArtifactKind.TACTIC, "c3");
        ArtifactRef c4 = new ArtifactRef(ArtifactKind.TACTIC, "c4");
        ArtifactRef c5 = new ArtifactRef(ArtifactKind.TACTIC, "c5");
        ArtifactRef e2 = new ArtifactRef(ArtifactKind.STYLEGUIDE, "e2");
        Map<ArtifactRef, List<ArtifactRef>> requires = Map.of(D, List.of(c, c3, c5), c3, List.of(c4));
        FakeAvailablePacks chain = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D")
                .held(ArtifactKind.TACTIC, "c", "c3", "c4", "c5")
                .held(ArtifactKind.STYLEGUIDE, "e2")
                .build());
        chain.walks(plan -> requiresWalk(plan, requires));
        chain.enhances(e2, c);
        chain.enhances(c, c3);
        ActivationExclusionService chained = new ActivationExclusionService(new ActivationWrites(repository, chain));
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D"))
                .selected(ArtifactKind.TACTIC, List.of("c", "c3", "c4", "c5"))
                .selected(ArtifactKind.STYLEGUIDE, List.of("e2")));

        ActivationWrites.Outcome outcome = chained.deactivate(ID, "directive", "D", ALL, quoted(before));

        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("c", "c3", "c4");
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).isEmpty();
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
                .as("the target's own walk and two shared steps")
                .hasSize(1 + 2);
    }

    /** A forward walk over requires from the plan's starts, stopping at (and not passing) the plan's stops. */
    private static Reach requiresWalk(ReachPlan plan, Map<ArtifactRef, List<ArtifactRef>> requires) {
        Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
        Map<ArtifactRef, List<Reach.Via>> stopped = new LinkedHashMap<>();
        ArrayDeque<ArtifactRef> frontier = new ArrayDeque<>(plan.starts());
        plan.starts().forEach(start -> reached.put(start, List.of()));
        while (!frontier.isEmpty()) {
            ArtifactRef from = frontier.removeFirst();
            for (ArtifactRef to : requires.getOrDefault(from, List.of())) {
                Reach.Via via = new Reach.Via(from, RelationType.REQUIRES);
                if (plan.stopAt().contains(to)) {
                    stopped.put(to, List.of(via));
                } else if (reached.putIfAbsent(to, List.of(via)) == null) {
                    frontier.addLast(to);
                }
            }
        }
        return new Reach(reached, stopped, Set.of(), Set.of());
    }
}
