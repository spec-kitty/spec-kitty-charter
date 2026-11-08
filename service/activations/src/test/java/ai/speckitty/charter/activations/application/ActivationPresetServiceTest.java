package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot.PresetView;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Applying a preset over an in-memory register and a counting fake of the packs: the shared write flow's preconditions
 * come first, the preset is resolved in the one read of the closure (NFR-007), and a preset the closure does not hold
 * is {@code 404 preset_not_found}.
 */
class ActivationPresetServiceTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String STALE = "\"sha256:" + "1f".repeat(32) + "\"";

    private final CountingRepository repository = new CountingRepository();
    private FakeAvailablePacks packs;
    private ActivationPresetService service;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "A", "B", "C", "M")
                .held(ArtifactKind.SKILL, "pr-review")
                .held(ArtifactKind.GLOSSARY_PACK, "core-terms")
                .held(ArtifactKind.MISSION_TYPE, "software-dev")
                .preset(new PresetView("team", Map.of(ArtifactKind.DIRECTIVE, Set.of("B", "C"))))
                .preset(new PresetView("stale", Map.of(ArtifactKind.DIRECTIVE, Set.of("GONE"))))
                .preset(new PresetView(
                        "every-kind",
                        Map.of(
                                ArtifactKind.SKILL, Set.of("pr-review"),
                                ArtifactKind.GLOSSARY_PACK, Set.of("core-terms"),
                                ArtifactKind.MISSION_TYPE, Set.of("software-dev"))))
                .build());
        service = new ActivationPresetService(new ActivationWrites(repository, packs));
    }

    private StoredActivation stored(ActivationDocument.Builder builder) {
        return repository
                .create(ID, builder.packs(List.of(PackRef.of("main"))).build().document())
                .orElseThrow();
    }

    private static String quoted(StoredActivation stored) {
        return "\"" + stored.revision().token() + "\"";
    }

    @Test
    void aPresetReplacesTheKeysItListsAndStoresTheResultAfterOneRead() {
        StoredActivation before = stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("A")));

        ActivationWrites.Outcome outcome = service.apply(ID, "team", false, List.of(), quoted(before));

        assertThat(outcome.change().applied())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "A"),
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "B"),
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "C"));
        assertThat(outcome.stored().revision()).isNotEqualTo(before.revision());
        assertThat(repository
                        .find(ID)
                        .orElseThrow()
                        .document()
                        .selected(ArtifactKind.DIRECTIVE)
                        .orElseThrow())
                .containsExactly("B", "C");
        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.lastRoots()).contains(List.of("main"));
    }

    @Test
    void aPresetOfSkillsGlossaryPacksAndMissionTypesAppliesAndReportsEachKind() {
        // FR-041 (Q3): a preset may name any kind, and the change reports what was applied per kind.
        StoredActivation before = stored(ActivationDocument.builder());

        ActivationWrites.Outcome outcome = service.apply(ID, "every-kind", false, List.of(), quoted(before));

        assertThat(outcome.change().applied())
                .containsExactlyInAnyOrder(
                        new ArtifactRef(ArtifactKind.SKILL, "pr-review"),
                        new ArtifactRef(ArtifactKind.GLOSSARY_PACK, "core-terms"),
                        new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev"));
        ActivationDocument stored = repository.find(ID).orElseThrow().document();
        assertThat(stored.selected(ArtifactKind.SKILL).orElseThrow()).containsExactly("pr-review");
        assertThat(stored.selected(ArtifactKind.GLOSSARY_PACK).orElseThrow()).containsExactly("core-terms");
        assertThat(stored.selected(ArtifactKind.MISSION_TYPE).orElseThrow()).containsExactly("software-dev");
    }

    @Test
    void forceClearsTheMembersDenyEntries() {
        StoredActivation before =
                stored(ActivationDocument.builder().deactivated(ArtifactKind.DIRECTIVE, List.of("B")));

        ActivationWrites.Outcome outcome = service.apply(ID, "team", true, List.of(), quoted(before));

        assertThat(outcome.change().cleared()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "B"));
        assertThat(repository.find(ID).orElseThrow().document().deactivated()).isEmpty();
    }

    @Test
    void aPresetTheClosureDoesNotHoldIs404PresetNotFoundAndNothingIsStored() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.apply(ID, "no-such", false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PRESET_NOT_FOUND));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aPresetWhoseMemberTheClosureDoesNotHoldIs422() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.apply(ID, "stale", false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID));
        assertThat(repository.replaces()).isZero();
    }

    @Test
    void aStalePresetMemberTheDocumentAlreadyNamesIs422NotANoOp() {
        StoredActivation before =
                stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("GONE")));

        assertThatThrownBy(() -> service.apply(ID, "stale", false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID));
        assertThat(repository.replaces()).isZero();
        assertThat(repository.find(ID).orElseThrow()).isEqualTo(before);
    }

    @Test
    void applyingTheSamePresetTwiceAnswersTheSameRevisionAndWritesOnce() {
        StoredActivation before = stored(ActivationDocument.builder());
        ActivationWrites.Outcome first = service.apply(ID, "team", false, List.of(), quoted(before));

        ActivationWrites.Outcome second = service.apply(ID, "team", false, List.of(), quoted(first.stored()));

        assertThat(second.stored().revision()).isEqualTo(first.stored().revision());
        assertThat(second.change().applied()).isEmpty();
        assertThat(repository.replaces()).isEqualTo(1);
    }

    @Test
    void thePreconditionsComeBeforeThePacksAreRead() {
        StoredActivation before = stored(ActivationDocument.builder());

        assertThatThrownBy(() -> service.apply(ID, "no-such", false, List.of(), null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        missing -> assertThat(missing.kind()).isEqualTo(PreconditionException.Kind.MISSING));
        assertThatThrownBy(() -> service.apply(ID, "no-such", false, List.of(), STALE))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThatThrownBy(() -> service.apply(new ActivationId("nope"), "team", false, List.of(), quoted(before)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aPresetDropsTheStoredIdsTheClosureNoLongerDeclaresAndReportsThem() {
        // ADR-0059 amendment A5: GONE was selected before a pack upgrade removed it.
        StoredActivation before =
                stored(ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of("A", "GONE")));

        ActivationWrites.Outcome outcome = service.apply(ID, "team", false, List.of(), quoted(before));

        assertThat(outcome.change().dropped()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"));
        assertThat(outcome.change().applied())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "A"),
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "B"),
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "C"));
        assertThat(repository
                        .find(ID)
                        .orElseThrow()
                        .document()
                        .selected(ArtifactKind.DIRECTIVE)
                        .orElseThrow())
                .containsExactly("B", "C");
    }
}
