package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.STYLEGUIDE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static ai.speckitty.charter.kernel.domain.RelationType.SUGGESTS;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** FR-011, FR-012; US3 AS1, AS2, AS6, AS8. The fixture: D requires T, D requires X, X denied, E enhances T. */
class CascadeRulesSelectTest {

    private static final ArtifactRef D = ref("directive:D");

    private static final ActivationDocument X_DENIED = ActivationDocument.builder()
            .deactivated(TACTIC, List.of("X"))
            .build()
            .document();

    /** What packs answers for the fixture: E enhances T runs from E to T, so a walk from D never reaches E. */
    private static final Reach US3 = reach().start("directive:D")
            .reached("tactic:T", "directive:D", REQUIRES)
            .stopped("tactic:X", "directive:D", REQUIRES)
            .build();

    private static ActivationChange select(ActivationDocument document, CascadeScope scope, Reach reach) {
        return CascadeRules.applySelect(document, D, scope, false, reach);
    }

    @Test
    void theCascadePlanWalksTheSixRelationsWithTheLineageAndStopsAtTheDenyEntries() {
        ReachPlan plan = CascadeRules.planSelect(X_DENIED, D, CascadeScope.all(), false);

        assertThat(plan.starts()).containsExactly(D);
        assertThat(plan.relations())
                .containsExactlyInAnyOrder(
                        REQUIRES,
                        SUGGESTS,
                        RelationType.REFINES,
                        RelationType.SCOPE,
                        RelationType.INSTANTIATES,
                        RelationType.ENHANCES);
        assertThat(plan.stopAt()).containsExactly(ref("tactic:X"));
        assertThat(plan.lineage()).isTrue();
        assertThat(plan.walks()).isTrue();
    }

    @Test
    void theTargetsOwnDenyEntryIsNoStop() {
        ActivationDocument denied = X_DENIED.withDeny(DIRECTIVE, "D");

        assertThat(CascadeRules.planSelect(denied, D, CascadeScope.all(), false).stopAt())
                .containsExactly(ref("tactic:X"));
    }

    @Test
    void scopeAllSelectsWhatTheWalkReachesAndReportsTheDeniedOne() {
        ActivationChange change = select(X_DENIED, CascadeScope.all(), US3);

        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("D");
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T");
        assertThat(change.applied()).containsExactly(D, ref("tactic:T"));
        assertThat(change.cleared()).isEmpty();
        assertThat(change.skipped().skippedDeactivated())
                .containsExactly(new SkippedReport.SkippedDeactivated(ref("tactic:X"), true, List.of(D)));
        assertThat(change.skipped().skippedByScope()).isEmpty();
        assertThat(change.skipped().unreachedBehindDeactivated()).isEmpty();
        assertThat(change.skipped().skippedShared()).isEmpty();
        assertThat(change.toString()).doesNotContain("styleguide");
        assertThat(change.after().selected()).doesNotContainKey(STYLEGUIDE);
    }

    @Test
    void aKindScopeReportsTheReachedArtifactsOutsideIt() {
        ActivationChange change = select(
                X_DENIED, CascadeScope.parse(Optional.of(List.of("directive"))).scope(), US3);

        assertThat(change.after().selected()).containsOnlyKeys(DIRECTIVE);
        assertThat(change.applied()).containsExactly(D);
        assertThat(change.skipped().skippedByScope()).containsExactly(ref("tactic:T"));
    }

    @Test
    void withoutACascadeOnlyTheTargetIsSelectedAndNoWalkIsAsked() {
        ReachPlan plan = CascadeRules.planSelect(X_DENIED, D, CascadeScope.none(), false);
        ActivationChange change = select(X_DENIED, CascadeScope.none(), US3);

        assertThat(plan.walks()).isFalse();
        assertThat(plan.relations()).isEmpty();
        assertThat(plan.lineage()).isFalse();
        assertThat(change.after().selected()).containsOnlyKeys(DIRECTIVE);
        assertThat(change.applied()).containsExactly(D);
        assertThat(change.skipped()).isEqualTo(SkippedReport.empty());
    }

    @Test
    void anAbsentAllowListBecomesExactlyTheTarget() {
        ActivationDocument document = ActivationDocument.builder().build().document();

        assertThat(select(document, CascadeScope.none(), Reach.empty())
                        .after()
                        .selected(DIRECTIVE)
                        .orElseThrow())
                .containsExactly("D");
    }

    @Test
    void selectClearsTheTargetsOwnDenyEntry() {
        ActivationChange change = select(X_DENIED.withDeny(DIRECTIVE, "D"), CascadeScope.none(), Reach.empty());

        assertThat(change.cleared()).containsExactly(D);
        assertThat(change.applied()).containsExactly(D);
        assertThat(change.after().deactivated(DIRECTIVE)).isEmpty();
        assertThat(change.after().deactivated()).containsOnlyKeys(TACTIC);
    }

    @Test
    void viaRequiresIsTrueWhenAnyWayIsRequiresAndRequiredByListsEverySourceSorted() {
        Reach reach = reach().start("directive:D")
                .reached("directive:B", "directive:D", SUGGESTS)
                .stopped("tactic:X", "directive:D", SUGGESTS)
                .stopped("tactic:X", "directive:B", REQUIRES)
                .stopped("tactic:Y", "directive:D", SUGGESTS)
                .build();

        assertThat(select(X_DENIED, CascadeScope.all(), reach).skipped().skippedDeactivated())
                .containsExactly(
                        new SkippedReport.SkippedDeactivated(ref("tactic:X"), true, List.of(ref("directive:B"), D)),
                        new SkippedReport.SkippedDeactivated(ref("tactic:Y"), false, List.of(D)));
    }

    @Test
    void anAlreadySelectedArtifactIsNotApplied() {
        ActivationDocument document = X_DENIED.withSelected(TACTIC, "T");

        assertThat(select(document, CascadeScope.all(), US3).applied()).containsExactly(D);
    }

    @Test
    void selectingAMissionTypeAddsItToItsAllowList() {
        ArtifactRef software = new ArtifactRef(MISSION_TYPE, "software-dev");

        ActivationChange change =
                CascadeRules.applySelect(X_DENIED, software, CascadeScope.none(), false, Reach.empty());

        assertThat(change.after().selected(MISSION_TYPE).orElseThrow()).containsExactly("software-dev");
        assertThat(change.after().selected()).containsOnlyKeys(MISSION_TYPE);
        assertThat(change.applied()).containsExactly(software);
    }

    @Test
    void aReachedMissionTypeInScopeIsSelected() {
        // Rule W21 (A7): mission_type is a kind like any other; W32 is withdrawn.
        Reach reach = reach().start("directive:D")
                .reached("mission_type:m", "directive:D", REQUIRES)
                .build();

        ActivationChange change = select(X_DENIED, CascadeScope.all(), reach);

        assertThat(change.after().selected(MISSION_TYPE).orElseThrow()).containsExactly("m");
        assertThat(change.applied()).contains(new ArtifactRef(MISSION_TYPE, "m"));
        assertThat(change.skipped().skippedByScope()).isEmpty();
    }

    @Test
    void aReachedMissionTypeOutsideANamedScopeIsSkippedByScope() {
        Reach reach = reach().start("directive:D")
                .reached("mission_type:m", "directive:D", REQUIRES)
                .build();
        CascadeScope directives =
                CascadeScope.parse(Optional.of(List.of("directive"))).scope();

        ActivationChange change = select(X_DENIED, directives, reach);

        assertThat(change.after().selected(MISSION_TYPE)).isEmpty();
        assertThat(change.skipped().skippedByScope()).containsExactly(new ArtifactRef(MISSION_TYPE, "m"));
    }

    @Test
    void aCascadingSelectOfAMissionTypeSelectsWhatItsStepsReach() {
        // A7 Q1, rule W20: the packs walk crosses from m to its steps like requires; a step is an action, which no
        // reference names, so the activation sees what the steps reach.
        ArtifactRef m = new ArtifactRef(MISSION_TYPE, "m");
        Reach reach = reach().start("mission_type:m")
                .reached("directive:S", "mission_type:m", REQUIRES)
                .build();

        ReachPlan plan = CascadeRules.planSelect(X_DENIED, m, CascadeScope.all(), false);
        ActivationChange change = CascadeRules.applySelect(X_DENIED, m, CascadeScope.all(), false, reach);

        assertThat(plan.walks()).isTrue();
        assertThat(plan.starts()).containsExactly(m);
        assertThat(change.applied()).containsExactly(ref("directive:S"), m);
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("S");
    }

    @Test
    void aReachedNodeOfANonSelectableKindIsNeitherSelectedNorReported() {
        Reach reach = reach().start("directive:D")
                .reached("template:t", "directive:D", REQUIRES)
                .build();

        ActivationChange change = select(X_DENIED, CascadeScope.all(), reach);

        assertThat(change.applied()).containsExactly(D);
        assertThat(change.skipped()).isEqualTo(SkippedReport.empty());
    }

    // ActivationChange.of: the one definition of applied and cleared (A1).

    @Test
    void anUnselectAppliesTheId() {
        ActivationDocument before = X_DENIED.withSelected(DIRECTIVE, "D");
        ActivationDocument after = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of())
                .deactivated(TACTIC, List.of("X"))
                .build()
                .document();

        ActivationChange change = ActivationChange.of(before, after, SkippedReport.empty());

        assertThat(change.applied()).containsExactly(D);
        assertThat(change.cleared()).isEmpty();
    }

    @Test
    void aDeactivateAppliesEveryIdThatChangedState() {
        ActivationDocument before = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("D"))
                .selected(TACTIC, List.of("T"))
                .build()
                .document();
        ActivationDocument after = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of())
                .selected(TACTIC, List.of())
                .deactivated(DIRECTIVE, List.of("D"))
                .build()
                .document();

        assertThat(ActivationChange.of(before, after, SkippedReport.empty()).applied())
                .containsExactly(D, ref("tactic:T"));
    }

    @Test
    void aReactivateAppliesAndClearsTheId() {
        ActivationChange change =
                ActivationChange.of(X_DENIED, X_DENIED.withoutDeny(TACTIC, "X"), SkippedReport.empty());

        assertThat(change.applied()).containsExactly(ref("tactic:X"));
        assertThat(change.cleared()).containsExactly(ref("tactic:X"));
        assertThat(change.after().deactivated()).isEmpty();
    }

    @Test
    void aNoOpAppliesAndClearsNothingAndKeepsTheRevision() {
        ActivationDocument document = X_DENIED.withSelected(DIRECTIVE, "D");

        ActivationChange change = select(document, CascadeScope.none(), Reach.empty());

        assertThat(change.applied()).isEmpty();
        assertThat(change.cleared()).isEmpty();
        assertThat(change.after().revision()).isEqualTo(document.revision());
    }

    @Test
    void aMissionTypeChangeIsApplied() {
        ActivationDocument after = ActivationDocument.builder()
                .selected(MISSION_TYPE, List.of("software-dev"))
                .build()
                .document();

        assertThat(ActivationChange.of(ActivationDocument.builder().build().document(), after, SkippedReport.empty())
                        .applied())
                .containsExactly(new ArtifactRef(MISSION_TYPE, "software-dev"));
    }
}
