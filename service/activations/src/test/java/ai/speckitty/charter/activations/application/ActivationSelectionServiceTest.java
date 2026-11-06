package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Select and unselect over an in-memory register and a counting fake of the packs: one read of the packs per write
 * (NFR-007), none when a precondition or the request's shape is refused; {@code applied} and {@code cleared} by the
 * contract's rule; a no-op answers the stored revision and writes nothing.
 */
class ActivationSelectionServiceTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final ArtifactRef D = new ArtifactRef(ArtifactKind.DIRECTIVE, "D");
    private static final ArtifactRef T = new ArtifactRef(ArtifactKind.TACTIC, "t");
    private static final ArtifactRef X = new ArtifactRef(ArtifactKind.TACTIC, "x");
    private static final Optional<List<String>> ALL = Optional.of(List.of("all"));

    private final CountingRepository repository = new CountingRepository();
    private FakeAvailablePacks packs;
    private ActivationSelectionService service;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure().build());
        service = new ActivationSelectionService(new ActivationWrites(repository, packs));
        packs.walks(ActivationSelectionServiceTest::walk);
    }

    /** D requires t and x: x is stopped at when the plan stops at it, and reached otherwise; e is not reached. */
    private static Reach walk(ReachPlan plan) {
        if (!plan.starts().contains(D)) {
            return Reach.empty();
        }
        List<Reach.Via> byD = List.of(new Reach.Via(D, RelationType.REQUIRES));
        Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
        reached.put(D, List.of());
        reached.put(T, byD);
        Map<ArtifactRef, List<Reach.Via>> stopped = new LinkedHashMap<>();
        (plan.stopAt().contains(X) ? stopped : reached).put(X, byD);
        return new Reach(reached, stopped, Set.of(), Set.of(), Map.of());
    }

    private static ClosureSnapshots closure() {
        return ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D", "D2")
                .held(ArtifactKind.TACTIC, "t", "x", "e")
                .missionTypes("research");
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
    void aCascadingSelectSelectsWhatTheWalkReachedReportsTheStopAndReadsThePacksOnce() {
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("x")));

        ActivationWrites.Outcome outcome = service.select(ID, "directive", "D", ALL, false, List.of(), quoted(before));

        assertThat(outcome.stored().revision()).isNotEqualTo(before.revision());
        assertThat(outcome.change().applied()).containsExactly(D, T);
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(outcome.change().skipped().skippedDeactivated())
                .containsExactly(new SkippedReport.SkippedDeactivated(X, true, List.of(D)));
        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow())
                .as("an enhancer is never selected (ADR-0059 D2.2)")
                .containsExactly("t");
        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.walked()).hasSize(1);
    }

    @Test
    void includeDeactivatedNeverClearsTheDenyEntryOfAnEnhancerTheWalkDoesNotReach() {
        // ADR-0059 D2 (operator ruling of 2026-11-06): e enhances t runs from e to t, so the walk from D never reaches
        // e, and no enhances pull runs at write time.
        StoredActivation before = stored(ActivationDocument.builder()
                .activatedKinds(List.of(ArtifactKind.TACTIC))
                .deactivated(ArtifactKind.TACTIC, List.of("e", "x")));

        ActivationWrites.Outcome outcome = service.select(ID, "directive", "D", ALL, true, List.of(), quoted(before));

        assertThat(outcome.change().cleared()).containsExactly(X);
        assertThat(outcome.change().applied()).containsExactly(D, T, X);
        assertThat(now().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("t", "x");
        assertThat(now().deactivated(ArtifactKind.TACTIC)).containsExactly("e");
        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.walked()).hasSize(1);
    }

    @Test
    void aSelectWithoutACascadeWalksNothingAndClearsTheTargetsOwnDenyEntry() {
        StoredActivation before =
                stored(ActivationDocument.builder().deactivated(ArtifactKind.DIRECTIVE, List.of("D")));

        ActivationWrites.Outcome outcome =
                service.select(ID, "directive", "D", Optional.empty(), false, List.of(), quoted(before));

        assertThat(packs.walked()).isEmpty();
        assertThat(outcome.change().applied()).containsExactly(D);
        assertThat(outcome.change().cleared()).containsExactly(D);
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("D");
    }

    @Test
    void aMissionTypeJoinsMissionTypesAndNeverCascades() {
        StoredActivation before = stored(ActivationDocument.builder());

        ActivationWrites.Outcome outcome =
                service.select(ID, "mission_type", "research", ALL, true, List.of(), quoted(before));

        assertThat(outcome.change().applied()).containsExactly(new ArtifactRef(ArtifactKind.MISSION_TYPE, "research"));
        assertThat(now().missionTypes().orElseThrow()).containsExactly("research");
        assertThat(packs.walked()).isEmpty();
    }

    @Test
    void selectingWhatIsSelectedIsANoOpThatKeepsTheRevisionAndWritesNothing() {
        StoredActivation before = stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("D")));

        ActivationWrites.Outcome outcome =
                service.select(ID, "directive", "D", Optional.empty(), false, List.of(), quoted(before));

        assertThat(outcome.stored()).isEqualTo(before);
        assertThat(outcome.change().applied()).isEmpty();
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void unselectingRemovesTheIdKeepsTheEmptiedListAndClearsNothing() {
        StoredActivation before = stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("D")));

        ActivationWrites.Outcome outcome = service.unselect(ID, "directive", "D", quoted(before));

        assertThat(outcome.change().applied()).containsExactly(D);
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(now().selected(ArtifactKind.DIRECTIVE))
                .hasValueSatisfying(ids -> assertThat(ids).isEmpty());
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void unselectingAMissionTypeRemovesItFromMissionTypes() {
        StoredActivation before = stored(ActivationDocument.builder().missionTypes(List.of("research")));

        ActivationWrites.Outcome outcome = service.unselect(ID, "mission_type", "research", quoted(before));

        assertThat(outcome.change().applied()).containsExactly(new ArtifactRef(ArtifactKind.MISSION_TYPE, "research"));
        assertThat(now().missionTypes())
                .hasValueSatisfying(ids -> assertThat(ids).isEmpty());
    }

    @Test
    void unselectingWhatIsNotSelectedIsANoOp() {
        StoredActivation before = stored(ActivationDocument.builder());

        ActivationWrites.Outcome outcome = service.unselect(ID, "directive", "D", quoted(before));

        assertThat(outcome.stored().revision()).isEqualTo(before.revision());
        assertThat(outcome.change().applied()).isEmpty();
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aMissingIfMatchIs428BeforeAnythingIsRead() {
        stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.select(ID, "template", "NO_SUCH", ALL, false, List.of(), null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.MISSING));
        assertThatThrownBy(() -> service.unselect(ID, "directive", "D", null))
                .isInstanceOf(PreconditionException.class);
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aStaleIfMatchIs412BeforeThePathIsJudged() {
        stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.select(
                        ID, "template", "NO_SUCH", ALL, false, List.of(), "\"sha256:" + "1f".repeat(32) + "\""))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void anActivationThatIsNotStoredIs404() {
        assertThatThrownBy(() -> service.select(ID, "directive", "D", ALL, false, List.of(), "*"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
    }

    @Test
    void aKindOutsideTheSelectableKindsIs422AtThePathBeforeThePacksAreRead() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.select(ID, "template", "D", ALL, false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(
                                    violation -> assertThat(violation.pointer()).isEqualTo("/kind"));
                });
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aCascadeMixingAllWithKindsIs422BeforeThePacksAreRead() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.select(
                        ID, "directive", "D", Optional.of(List.of("all", "tactic")), false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(
                                    violation -> assertThat(violation.pointer()).isEqualTo("/cascade"));
                });
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aPathIdTheClosureDoesNotHoldIs404ByItsKind() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.select(ID, "directive", "NO_SUCH", ALL, false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThatThrownBy(() -> service.unselect(ID, "mission_type", "no-such", quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aPathIdInAnyKnownSpellingSelectsAndUnselectsTheDeclaredId() {
        // ADR-0020 D4 (operator ruling of 2026-11-06): the path id is read as a document's id is.
        StoredActivation before = stored(ActivationDocument.builder());

        ActivationWrites.Outcome selected =
                service.select(ID, "directive", "directive:d", Optional.empty(), false, List.of(), quoted(before));
        ActivationWrites.Outcome again =
                service.select(ID, "directive", "d", Optional.empty(), false, List.of(), quoted(selected.stored()));
        ActivationWrites.Outcome mission = service.select(
                ID, "mission_type", "mission_type:RESEARCH", Optional.empty(), false, List.of(), quoted(again.stored()));

        assertThat(selected.change().applied()).containsExactly(D);
        assertThat(again.stored().revision()).as("another spelling is a no-op").isEqualTo(selected.stored().revision());
        assertThat(mission.change().applied()).containsExactly(new ArtifactRef(ArtifactKind.MISSION_TYPE, "research"));
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("D");
        assertThat(now().missionTypes().orElseThrow()).containsExactly("research");

        ActivationWrites.Outcome unselected = service.unselect(ID, "directive", "directive:d", quoted(mission.stored()));

        assertThat(unselected.change().applied()).containsExactly(D);
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).isEmpty();
    }

    @Test
    void aUrnOfAnotherKindIs404AndASpellingOfTwoDeclaredIdsIs422AtThePath() {
        packs = new FakeAvailablePacks(closure().held(ArtifactKind.DIRECTIVE, "Ab", "AB").build());
        service = new ActivationSelectionService(new ActivationWrites(repository, packs));
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.select(
                        ID, "directive", "tactic:D", Optional.empty(), false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThatThrownBy(() ->
                        service.select(ID, "directive", "ab", Optional.empty(), false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(violation -> {
                                assertThat(violation.pointer()).isEqualTo("/artifactId");
                                assertThat(violation.code()).isEqualTo("ambiguous_artifact");
                            });
                });
        ActivationWrites.Outcome exact =
                service.select(ID, "directive", "AB", Optional.empty(), false, List.of(), quoted(before));
        assertThat(exact.change().applied()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "AB"));
    }

    @Test
    void anUnselectOfAStaleIdInAnotherSpellingDropsIt() {
        StoredActivation before = stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("GONE")));

        ActivationWrites.Outcome outcome = service.unselect(ID, "directive", "directive:gone", quoted(before));

        assertThat(outcome.change().dropped()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"));
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).isEmpty();
    }

    @Test
    void aPackNoLongerConfiguredIs422AtTheWholeRequest() {
        StoredActivation before = stored(ActivationDocument.builder());
        packs.answer(ClosureSnapshot.unresolved(Set.of("main")));

        assertThatThrownBy(() -> service.select(ID, "directive", "D", ALL, false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEmpty();
                        assertThat(violation.code()).isEqualTo("unknown_pack");
                    });
                });
    }

    @Test
    void aStoredDenyEntryAPackNowRequiresIsToleratedByAWriteThatDoesNotAddIt() {
        StoredActivation before =
                stored(ActivationDocument.builder().deactivated(ArtifactKind.DIRECTIVE, List.of("D2")));
        packs.answer(closure().requiredBy("directive:D2", "main").build());

        ActivationWrites.Outcome selected =
                service.select(ID, "tactic", "t", Optional.empty(), false, List.of(), quoted(before));
        ActivationWrites.Outcome unselected = service.unselect(ID, "tactic", "t", quoted(selected.stored()));

        assertThat(selected.change().applied()).containsExactly(T);
        assertThat(unselected.change().applied()).containsExactly(T);
        assertThat(now().deactivated(ArtifactKind.DIRECTIVE))
                .as("a stored deny entry on the floor is tolerated, not applied and not removed")
                .containsExactly("D2");
    }

    @Test
    void aWriterThatCameInBetweenMakesTheWrite412() {
        StoredActivation before = stored(ActivationDocument.builder());
        repository.loseNextWrite();

        assertThatThrownBy(
                        () -> service.select(ID, "directive", "D", Optional.empty(), false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.STALE));
    }

    // Stale ids are dropped on write (ADR-0059 amendment R5, operator ruling of 2026-11-06).

    @Test
    void aSelectDropsTheStoredIdsTheClosureNoLongerDeclaresAndReportsThem() {
        StoredActivation before = stored(ActivationDocument.builder()
                .missionTypes(List.of("gone-type"))
                .selected(ArtifactKind.DIRECTIVE, List.of("GONE"))
                .deactivated(ArtifactKind.TACTIC, List.of("old")));

        ActivationWrites.Outcome outcome =
                service.select(ID, "directive", "D2", Optional.empty(), false, List.of(), quoted(before));

        assertThat(outcome.change().dropped())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"),
                        new ArtifactRef(ArtifactKind.MISSION_TYPE, "gone-type"),
                        new ArtifactRef(ArtifactKind.TACTIC, "old"));
        assertThat(outcome.change().applied()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "D2"));
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("D2");
        assertThat(now().missionTypes().orElseThrow()).isEmpty();
        assertThat(now().deactivated()).isEmpty();
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void aWriteThatOnlyDropsStaleIdsIsNotANoOpAndStoresANewRevision() {
        StoredActivation before =
                stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("D", "GONE")));

        ActivationWrites.Outcome outcome =
                service.select(ID, "directive", "D", Optional.empty(), false, List.of(), quoted(before));

        assertThat(outcome.change().applied()).isEmpty();
        assertThat(outcome.change().dropped()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"));
        assertThat(outcome.stored().revision()).isNotEqualTo(before.revision());
        assertThat(repository.replaces()).isEqualTo(1);
    }

    @Test
    void aWriteThatDropsNothingReportsNothingDropped() {
        StoredActivation before = stored(ActivationDocument.builder());

        ActivationWrites.Outcome outcome =
                service.select(ID, "directive", "D", Optional.empty(), false, List.of(), quoted(before));

        assertThat(outcome.change().dropped()).isEmpty();
    }

    @Test
    void anUnselectOfAStaleIdTheDocumentSelectsDropsItAndReportsItInDroppedOnly() {
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D", "GONE"))
                .deactivated(ArtifactKind.TACTIC, List.of("old")));

        ActivationWrites.Outcome outcome = service.unselect(ID, "directive", "GONE", quoted(before));

        assertThat(outcome.change().dropped())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"), new ArtifactRef(ArtifactKind.TACTIC, "old"));
        assertThat(outcome.change().applied()).isEmpty();
        assertThat(outcome.change().cleared()).isEmpty();
        assertThat(outcome.stored().revision()).isNotEqualTo(before.revision());
        assertThat(now().selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("D");
        assertThat(now().deactivated()).isEmpty();
        assertThat(repository.replaces()).isEqualTo(1);
    }

    @Test
    void anUnselectOfAStaleMissionTypeTheDocumentNamesDropsIt() {
        StoredActivation before = stored(ActivationDocument.builder().missionTypes(List.of("gone-type")));

        ActivationWrites.Outcome outcome = service.unselect(ID, "mission_type", "gone-type", quoted(before));

        assertThat(outcome.change().dropped()).containsExactly(new ArtifactRef(ArtifactKind.MISSION_TYPE, "gone-type"));
        assertThat(outcome.change().applied()).isEmpty();
        assertThat(now().missionTypes())
                .hasValueSatisfying(ids -> assertThat(ids).isEmpty());
    }

    @Test
    void anUnselectOfAnIdNeitherTheClosureNorTheDocumentNamesIsStill404AndDropsNothing() {
        StoredActivation before = stored(ActivationDocument.builder()
                .missionTypes(List.of("gone-type"))
                .selected(ArtifactKind.DIRECTIVE, List.of("GONE")));

        assertThatThrownBy(() -> service.unselect(ID, "directive", "NO_SUCH", quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThatThrownBy(() -> service.unselect(ID, "mission_type", "no-such", quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void anUnselectOfAStaleIdTheDocumentOnlyDeniesIs404() {
        // The unselect removes from selected: a stale id named only in deactivated is not the unselect's to drop.
        StoredActivation before = stored(ActivationDocument.builder().deactivated(ArtifactKind.TACTIC, List.of("old")));

        assertThatThrownBy(() -> service.unselect(ID, "tactic", "old", quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aSelectOfAStaleIdTheDocumentSelectsIsStill404() {
        StoredActivation before =
                stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("GONE")));

        assertThatThrownBy(() ->
                        service.select(ID, "directive", "GONE", Optional.empty(), false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aStaleIfMatchOnAnUnselectOfAStaleIdIs412BeforeTheArtifactIsJudged() {
        stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("GONE")));

        assertThatThrownBy(() -> service.unselect(ID, "directive", "NO_SUCH", "\"other\""))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aPinnedVersionTheClosureDoesNotHoldIsStill422AndNotDropped() {
        StoredActivation before = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.pinned("main", "9.9.9")))
                                .build()
                                .document())
                .orElseThrow();

        assertThatThrownBy(
                        () -> service.select(ID, "directive", "D", Optional.empty(), false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(violation -> assertThat(violation.code()).isEqualTo("version_not_held"));
                });
        assertThat(repository.replaces()).isZero();
    }
}
