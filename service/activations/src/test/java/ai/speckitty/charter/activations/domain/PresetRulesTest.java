package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ClosureSnapshot.PresetView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Applying a preset to a document (ADR-0059 D6, ADR-0020 D6, FR-016): each key the preset lists is replaced, the others
 * are untouched; a key counts as listed only when the preset holds at least one member for it (A7); {@code force}
 * clears the deny entries of the members; {@code applied} is {@link ActivationChange#of}'s (A1).
 */
class PresetRulesTest {

    private static final ArtifactRef A = new ArtifactRef(ArtifactKind.DIRECTIVE, "A");
    private static final ArtifactRef B = new ArtifactRef(ArtifactKind.DIRECTIVE, "B");
    private static final ArtifactRef C = new ArtifactRef(ArtifactKind.DIRECTIVE, "C");
    private static final ArtifactRef M = new ArtifactRef(ArtifactKind.DIRECTIVE, "M");

    private static PresetView preset(
            Set<ArtifactKind> kinds, Map<ArtifactKind, Set<String>> members, Set<String> missionTypes) {
        return new PresetView("p", "PID-main", kinds, members, Map.of(), missionTypes, Set.of());
    }

    private static PresetView directives(String... ids) {
        return preset(Set.of(), Map.of(ArtifactKind.DIRECTIVE, Set.of(ids)), Set.of());
    }

    private static ActivationDocument.Builder document() {
        return ActivationDocument.builder().packs(List.of(PackRef.of("main")));
    }

    @Test
    void aListedKindIsReplacedByExactlyTheMembersAndAnUnlistedOneIsUntouched() {
        ActivationDocument before = document()
                .selected(ArtifactKind.DIRECTIVE, List.of("A"))
                .selected(ArtifactKind.TACTIC, List.of("t"))
                .build()
                .document();
        PresetView team =
                preset(Set.of(ArtifactKind.DIRECTIVE), Map.of(ArtifactKind.DIRECTIVE, Set.of("C", "B")), Set.of());

        ActivationChange change = PresetRules.apply(before, team, false);

        ActivationDocument after = change.after();
        assertThat(after.selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("B", "C");
        assertThat(after.selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("t");
        assertThat(after.activatedKinds().orElseThrow()).containsExactly(ArtifactKind.DIRECTIVE);
        assertThat(after.packs()).isEqualTo(before.packs());
        assertThat(change.applied())
                .as("A was removed, B and C added (A1); the kind gate's change is no entry")
                .containsExactly(A, B, C);
        assertThat(change.cleared()).isEmpty();
        assertThat(change.skipped()).isEqualTo(SkippedReport.empty());
    }

    @Test
    void anEmptyKindGateIsNotListedSoTheDocumentsGateStays() {
        // A7: the packs read activated_kinds: [] and an absent key alike, as an empty set.
        ActivationDocument before =
                document().activatedKinds(List.of(ArtifactKind.TACTIC)).build().document();

        ActivationChange change = PresetRules.apply(before, directives("A"), false);

        assertThat(change.after().activatedKinds().orElseThrow()).containsExactly(ArtifactKind.TACTIC);
    }

    @Test
    void anAbsentKindGateStaysAbsentWhenThePresetListsNone() {
        ActivationDocument before = document().build().document();

        ActivationChange change = PresetRules.apply(before, directives("A"), false);

        assertThat(change.after().activatedKinds()).isEmpty();
        assertThat(change.after().missionTypes()).isEmpty();
    }

    @Test
    void anEmptyMemberListIsNotListedSoTheDocumentsListStays() {
        ActivationDocument before =
                document().selected(ArtifactKind.TACTIC, List.of("t")).build().document();
        PresetView emptyTactics =
                preset(Set.of(), Map.of(ArtifactKind.TACTIC, Set.of(), ArtifactKind.DIRECTIVE, Set.of("A")), Set.of());

        ActivationChange change = PresetRules.apply(before, emptyTactics, false);

        assertThat(change.after().selected(ArtifactKind.TACTIC).orElseThrow()).containsExactly("t");
        assertThat(change.applied()).containsExactly(A);
    }

    @Test
    void listedMissionTypesReplaceTheDocumentsAndTheDroppedOneIsApplied() {
        ActivationDocument before =
                document().missionTypes(List.of("old-type")).build().document();
        PresetView missionTypes = preset(Set.of(), Map.of(), Set.of("software-dev"));

        ActivationChange change = PresetRules.apply(before, missionTypes, false);

        assertThat(change.after().missionTypes().orElseThrow()).containsExactly("software-dev");
        assertThat(change.applied())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.MISSION_TYPE, "old-type"),
                        new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev"));
    }

    @Test
    void unlistedMissionTypesAreUntouched() {
        ActivationDocument before =
                document().missionTypes(List.of("old-type")).build().document();

        ActivationChange change = PresetRules.apply(before, directives("A"), false);

        assertThat(change.after().missionTypes().orElseThrow()).containsExactly("old-type");
    }

    @Test
    void withoutForceADeniedMemberIsNotSelectedAndItsDenyEntryStays() {
        ActivationDocument before = document()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("M"))
                .build()
                .document();

        ActivationChange change = PresetRules.apply(before, directives("B", "M"), false);

        assertThat(change.after().selected(ArtifactKind.DIRECTIVE).orElseThrow())
                .containsExactly("B");
        assertThat(change.after().deactivated(ArtifactKind.DIRECTIVE)).containsExactly("M");
        assertThat(change.applied()).containsExactly(B);
        assertThat(change.cleared()).isEmpty();
    }

    @Test
    void withoutForceAKindWhoseMembersAreAllDeniedBecomesEmptyNotAbsent() {
        ActivationDocument before = document()
                .selected(ArtifactKind.DIRECTIVE, List.of("A"))
                .deactivated(ArtifactKind.DIRECTIVE, List.of("M"))
                .build()
                .document();

        ActivationChange change = PresetRules.apply(before, directives("M"), false);

        assertThat(change.after().selected(ArtifactKind.DIRECTIVE))
                .hasValueSatisfying(ids -> assertThat(ids).isEmpty());
        assertThat(change.applied()).containsExactly(A);
    }

    @Test
    void withForceTheMembersDenyEntriesAreClearedAndAnEmptiedDenyListIsDropped() {
        ActivationDocument before = document()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("M"))
                .deactivated(ArtifactKind.TACTIC, List.of("x"))
                .build()
                .document();

        ActivationChange change = PresetRules.apply(before, directives("B", "M"), true);

        assertThat(change.after().selected(ArtifactKind.DIRECTIVE).orElseThrow())
                .containsExactly("B", "M");
        assertThat(change.after().deactivated()).containsOnlyKeys(ArtifactKind.TACTIC);
        assertThat(change.cleared()).containsExactly(M);
        assertThat(change.applied()).containsExactly(B, M);
    }

    @Test
    void forceClearsNoDenyEntryOfAnIdThePresetDoesNotList() {
        ActivationDocument before = document()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("C"))
                .build()
                .document();

        ActivationChange change = PresetRules.apply(before, directives("B"), true);

        assertThat(change.after().deactivated(ArtifactKind.DIRECTIVE)).containsExactly("C");
        assertThat(change.cleared()).isEmpty();
    }

    @Test
    void aPresetNeverAddsADenyEntrySoItsMustHaveFloorCannotBeBroken() {
        // The must-have items are the floor (WP04); a preset only removes deny entries, so no 409 arises from one.
        ActivationDocument before = document()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("M"))
                .build()
                .document();
        PresetView governing = new PresetView(
                "g",
                "PID-main",
                Set.of(),
                Map.of(ArtifactKind.DIRECTIVE, Set.of("RQ")),
                Map.of(ArtifactKind.DIRECTIVE, Set.of("RQ")),
                Set.of(),
                Set.of());

        for (boolean force : List.of(false, true)) {
            ActivationDocument after =
                    PresetRules.apply(before, governing, force).after();
            assertThat(after.deactivated()).isEqualTo(before.deactivated());
            assertThat(after.selected(ArtifactKind.DIRECTIVE).orElseThrow()).containsExactly("RQ");
        }
    }

    @Test
    void aKindGateTheDocumentCannotHoldIsLeftOutOfIt() {
        // A preset's gate may name templates or assets, which an activation document gates nothing of.
        ActivationDocument before = document().build().document();
        PresetView gate = preset(Set.of(ArtifactKind.TEMPLATE, ArtifactKind.TACTIC), Map.of(), Set.of());

        ActivationChange change = PresetRules.apply(before, gate, false);

        assertThat(change.after().activatedKinds().orElseThrow()).containsExactly(ArtifactKind.TACTIC);
    }

    @Test
    void applyingThePresetAgainChangesNothing() {
        ActivationDocument before = document()
                .selected(ArtifactKind.DIRECTIVE, List.of("A"))
                .languages(List.of("en"))
                .build()
                .document();
        PresetView team = preset(
                Set.of(ArtifactKind.DIRECTIVE), Map.of(ArtifactKind.DIRECTIVE, Set.of("B", "C")), Set.of("research"));

        ActivationDocument once = PresetRules.apply(before, team, false).after();
        ActivationChange twice = PresetRules.apply(once, team, false);

        assertThat(twice.after()).isEqualTo(once);
        assertThat(twice.applied()).isEmpty();
        assertThat(once.languages().orElseThrow()).containsExactly("en");
    }
}
