package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationChange.Code;
import ai.speckitty.charter.activations.domain.ActivationChange.Warning;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveSet.Bucket;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The warnings of a write (IC-17, FR-032, B6/B7): after a write, the in-force resolution of the document as stored is
 * asked once, of the session the write already holds, and each artifact the write applied that a read will leave out of
 * force for want of a target in force ({@code enhanced_target_not_in_force}) or because an artifact in force overrides it
 * ({@code overridden}) is a warning. The write is accepted either way. Any other reason is no warning, and a no-op asks
 * nothing.
 */
class ActivationWriteWarningsTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final ArtifactRef T = new ArtifactRef(ArtifactKind.TACTIC, "t");
    private static final ArtifactRef E = new ArtifactRef(ArtifactKind.TACTIC, "e");
    private static final ArtifactRef H = new ArtifactRef(ArtifactKind.TACTIC, "h");
    private static final ArtifactRef F = new ArtifactRef(ArtifactKind.TACTIC, "f");

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private final Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>();
    private FakeAvailablePacks packs;
    private ActivationSelectionService selections;
    private ActivationExclusionService exclusions;
    private ActivationPresetService presets;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.TACTIC, "t", "e", "h", "f")
                .build());
        packs.resolves(
                query -> new InForceAnswer(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()));
        ActivationWrites writes = new ActivationWrites(repository, packs);
        selections = new ActivationSelectionService(writes);
        exclusions = new ActivationExclusionService(writes);
        presets = new ActivationPresetService(writes);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("main")))
                                .build()
                                .document())
                .orElseThrow();
    }

    private static ArtifactState inactive(ArtifactRef ref, Reason reason) {
        return new ArtifactState(
                ref, State.INACTIVE, reason, false, List.of(), List.of(), Optional.of(Bucket.OVERRIDDEN));
    }

    private static String quoted(StoredActivation activation) {
        return "\"" + activation.revision().token() + "\"";
    }

    private ActivationWrites.Outcome select(ArtifactRef ref) {
        return selections.select(
                ID,
                ref.kind().token(),
                ref.id(),
                Optional.empty(),
                false,
                List.of(),
                quoted(repository.find(ID).orElseThrow()));
    }

    @Test
    void aSelectOfAnEnhancerWithoutATargetInForceWarnsWithEnhancedTargetNotInForce() {
        states.put(E, inactive(E, Reason.ENHANCED_TARGET_NOT_IN_FORCE));

        ActivationWrites.Outcome outcome = select(E);

        assertThat(outcome.change().applied()).containsExactly(E);
        assertThat(outcome.change().warnings()).containsExactly(new Warning(Code.ENHANCED_TARGET_NOT_IN_FORCE, E));
    }

    @Test
    void aSelectOfAnArtifactAnActiveOverrideHidesWarnsWithOverridden() {
        states.put(H, inactive(H, Reason.OVERRIDDEN));

        assertThat(select(H).change().warnings()).containsExactly(new Warning(Code.OVERRIDDEN, H));
    }

    @Test
    void anAppliedArtifactInForceIsNoWarning() {
        states.put(
                T, new ArtifactState(T, State.ACTIVE, Reason.SELECTED, false, List.of(), List.of(), Optional.empty()));

        assertThat(select(T).change().warnings()).isEmpty();
    }

    @Test
    void onlyAnAppliedArtifactWarnsNotOneThatMerelyStaysOutOfForce() {
        states.put(E, inactive(E, Reason.ENHANCED_TARGET_NOT_IN_FORCE));
        states.put(H, inactive(H, Reason.OVERRIDDEN));

        assertThat(select(F).change().warnings()).isEmpty();
    }

    @Test
    void aDenyEntryAndAnUnselectAreWhatTheWriteAskedForAndWarnOfNothing() {
        states.put(E, inactive(E, Reason.DEACTIVATED));
        ActivationWrites.Outcome denied = exclusions.deactivate(
                ID, "tactic", "e", Optional.empty(), OptionalInt.empty(), List.of(), quoted(stored));
        states.put(T, inactive(T, Reason.NOT_SELECTED));
        select(T);
        ActivationWrites.Outcome unselected = selections.unselect(
                ID, "tactic", "t", quoted(repository.find(ID).orElseThrow()));

        assertThat(denied.change().applied()).containsExactly(E);
        assertThat(denied.change().warnings()).isEmpty();
        assertThat(unselected.change().applied()).containsExactly(T);
        assertThat(unselected.change().warnings()).isEmpty();
    }

    @Test
    void aDenyEntryOnAnEnhancerWithoutATargetInForceWarnsOfNothingThoughItsReasonIsTheEnhancersOne() {
        states.put(E, inactive(E, Reason.ENHANCED_TARGET_NOT_IN_FORCE));

        ActivationWrites.Outcome denied = exclusions.deactivate(
                ID, "tactic", "e", Optional.empty(), OptionalInt.empty(), List.of(), quoted(stored));

        assertThat(denied.change().applied()).containsExactly(E);
        assertThat(denied.change().warnings())
                .as("a deny is no addition: E9 puts the enhancer row before the deny row, and the write asked for it")
                .isEmpty();
    }

    @Test
    void anUnselectOfAnEnhancerWithoutATargetInForceWarnsOfNothingThoughItsReasonIsTheEnhancersOne() {
        states.put(E, inactive(E, Reason.ENHANCED_TARGET_NOT_IN_FORCE));
        select(E);

        ActivationWrites.Outcome unselected = selections.unselect(
                ID, "tactic", "e", quoted(repository.find(ID).orElseThrow()));

        assertThat(unselected.change().applied()).containsExactly(E);
        assertThat(unselected.change().warnings())
                .as("a removal is no addition")
                .isEmpty();
    }

    @Test
    void aDenyOfAnOverriddenArtifactWarnsOfNothing() {
        states.put(H, inactive(H, Reason.OVERRIDDEN));

        ActivationWrites.Outcome denied = exclusions.deactivate(
                ID, "tactic", "h", Optional.empty(), OptionalInt.empty(), List.of(), quoted(stored));

        assertThat(denied.change().warnings()).isEmpty();
    }

    @Test
    void theResolutionIsAskedOnceOfTheSessionTheWriteHoldsWithTheDocumentAsStored() {
        states.put(E, inactive(E, Reason.ENHANCED_TARGET_NOT_IN_FORCE));

        ActivationWrites.Outcome outcome = select(E);

        assertThat(packs.calls()).as("the write's one verified read").isEqualTo(1);
        assertThat(packs.queries()).as("one in-force question").hasSize(1);
        assertThat(packs.queries().getFirst().selected()).containsExactly(E);
        assertThat(outcome.stored().document().selected(ArtifactKind.TACTIC).orElseThrow())
                .as("the write is accepted whatever the read will show")
                .containsExactly("e");
    }

    @Test
    void aNoOpAsksNoQuestionAndWarnsOfNothing() {
        states.put(E, inactive(E, Reason.ENHANCED_TARGET_NOT_IN_FORCE));
        select(E);
        int asked = packs.queries().size();

        ActivationWrites.Outcome again = select(E);

        assertThat(again.change().applied()).isEmpty();
        assertThat(again.change().warnings()).isEmpty();
        assertThat(packs.queries()).hasSize(asked);
    }

    @Test
    void aClosureWhoseResolutionRefusesRefusesTheWriteAndStoresNothing() {
        packs.resolves(query -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the overrides edges form a cycle");
        });

        assertThatThrownBy(() -> select(E))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        assertThat(repository.find(ID)).contains(stored);
    }

    @Test
    void thePresetWriteWarnsToo() {
        packs.answer(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.TACTIC, "t", "e", "h", "f")
                .preset(new ClosureSnapshot.PresetView("team", Map.of(ArtifactKind.TACTIC, java.util.Set.of("e", "t"))))
                .build());
        states.put(E, inactive(E, Reason.ENHANCED_TARGET_NOT_IN_FORCE));

        ActivationWrites.Outcome outcome = presets.apply(
                ID, "team", false, List.of(), quoted(repository.find(ID).orElseThrow()));

        assertThat(outcome.change().applied()).containsExactly(E, T);
        assertThat(outcome.change().warnings()).containsExactly(new Warning(Code.ENHANCED_TARGET_NOT_IN_FORCE, E));
    }
}
