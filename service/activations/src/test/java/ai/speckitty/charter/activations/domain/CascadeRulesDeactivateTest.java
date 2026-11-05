package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.STYLEGUIDE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * FR-015, US4 AS1 and AS2: a cascading deactivate unselects what only the target reached, keeps what another active
 * artifact (selected or on the floor) reaches or an active enhancer references, and denies only the target. The
 * fixture: D requires T, T2 and T3; D2 requires T2; E2 enhances T3.
 */
class CascadeRulesDeactivateTest {

    private static final ArtifactRef D = ref("directive:D");
    private static final ArtifactRef T = ref("tactic:T");
    private static final ArtifactRef T2 = ref("tactic:T2");
    private static final ArtifactRef T3 = ref("tactic:T3");
    private static final ArtifactRef E2 = ref("styleguide:E2");

    private static final Reach OWN = reach().start("directive:D")
            .reached("tactic:T", "directive:D", REQUIRES)
            .reached("tactic:T2", "directive:D", REQUIRES)
            .reached("tactic:T3", "directive:D", REQUIRES)
            .build();

    /** D2 reaches T2; E2 is a start of its own that reaches nothing over the five relations. */
    private static final Reach OTHERS = reach().start("directive:D2")
            .start("styleguide:E2")
            .reached("tactic:T2", "directive:D2", REQUIRES)
            .build();

    private static ActivationDocument selecting(String... urns) {
        ActivationDocument document = ActivationDocument.builder().build().document();
        for (ArtifactRef ref : refs(urns)) {
            document = document.withSelected(ref.kind(), ref.id());
        }
        return document;
    }

    private static final ActivationDocument US4 =
            selecting("directive:D", "directive:D2", "tactic:T", "tactic:T2", "tactic:T3", "styleguide:E2");

    private static ActivationChange deactivate(
            ActivationDocument document,
            CascadeScope scope,
            Reach others,
            Map<ArtifactRef, Set<ArtifactRef>> enhancers,
            Set<ArtifactRef> floor) {
        return CascadeRules.applyDeactivate(document, D, scope, OWN, others, enhancers, floor);
    }

    private static ActivationChange deactivateAll(Map<ArtifactRef, Set<ArtifactRef>> enhancers) {
        return deactivate(US4, CascadeScope.all(), OTHERS, enhancers, Set.of());
    }

    @Test
    void theOwnPlanWalksTheFiveRelationsAndStopsNowhere() {
        ReachPlan plan = CascadeRules.planDeactivate(US4, D, CascadeScope.all());

        assertThat(plan.starts()).containsExactly(D);
        assertThat(plan.relations()).hasSize(5);
        assertThat(plan.stopAt()).isEmpty();
    }

    @Test
    void theSharedPlanStartsFromTheOtherActiveArtifactsAndStopsAtTheTarget() {
        ReachPlan plan =
                CascadeRules.planShared(US4.withDeny(TACTIC, "Z"), D, CascadeScope.all(), OWN, refs("procedure:F"));

        assertThat(plan.starts()).containsExactly(ref("directive:D2"), ref("procedure:F"), E2);
        assertThat(plan.stopAt()).containsExactly(D, ref("tactic:Z"));
        assertThat(plan.lineage()).isTrue();
    }

    @Test
    void whatOnlyTheTargetReachedLeavesAndWhatAnotherReachesIsKept() {
        ActivationChange change = deactivateAll(Map.of(T3, Set.of(E2)));

        assertThat(change.after().deactivated()).containsOnlyKeys(DIRECTIVE);
        assertThat(change.after().deactivated(DIRECTIVE)).containsExactly("D");
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("D2");
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2", "T3");
        assertThat(change.skipped().skippedShared())
                .containsExactly(
                        new SkippedReport.SharedSkip(T2, ref("directive:D2")), new SkippedReport.SharedSkip(T3, E2));
        assertThat(change.applied()).containsExactly(D, T);
        assertThat(change.cleared()).isEmpty();
    }

    @Test
    void anEnhancerThatIsNotActiveKeepsNothing() {
        ActivationChange change = deactivateAll(Map.of(T3, Set.of(ref("styleguide:E9"))));

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2");
        assertThat(change.applied()).containsExactly(D, T, T3);
        assertThat(change.skipped().skippedShared())
                .containsExactly(new SkippedReport.SharedSkip(T2, ref("directive:D2")));
    }

    @Test
    void anEnhancerThatIsItselfACandidateKeepsNothing() {
        ActivationDocument document = US4.withSelected(STYLEGUIDE, "E3");
        Reach own = reach().start("directive:D")
                .reached("tactic:T3", "directive:D", REQUIRES)
                .reached("styleguide:E3", "directive:D", REQUIRES)
                .build();

        ActivationChange change = CascadeRules.applyDeactivate(
                document, D, CascadeScope.all(), own, OTHERS, Map.of(T3, Set.of(ref("styleguide:E3"))), Set.of());

        assertThat(change.applied()).containsExactly(D, ref("styleguide:E3"), T3);
    }

    @Test
    void aFloorArtifactKeepsWhatItReaches() {
        Reach others = reach().start("directive:D2")
                .start("procedure:F")
                .reached("tactic:T2", "directive:D2", REQUIRES)
                .reached("tactic:T3", "procedure:F", REQUIRES)
                .build();

        ActivationChange change = deactivate(US4, CascadeScope.all(), others, Map.of(), refs("procedure:F"));

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2", "T3");
        assertThat(change.skipped().skippedShared()).contains(new SkippedReport.SharedSkip(T3, ref("procedure:F")));
    }

    @Test
    void withoutACascadeOnlyTheTargetLeavesAndIsDenied() {
        ReachPlan plan = CascadeRules.planDeactivate(US4, D, CascadeScope.none());

        ActivationChange change = deactivate(US4, CascadeScope.none(), Reach.empty(), Map.of(), Set.of());

        assertThat(plan.walks()).isFalse();
        assertThat(CascadeRules.planShared(US4, D, CascadeScope.none(), OWN, Set.of())
                        .walks())
                .isFalse();
        assertThat(change.applied()).containsExactly(D);
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T", "T2", "T3");
        assertThat(change.after().deactivated(DIRECTIVE)).containsExactly("D");
        assertThat(change.skipped()).isEqualTo(SkippedReport.empty());
    }

    @Test
    void aCandidateOutsideTheScopeIsLeftAloneAndReported() {
        CascadeScope directives =
                CascadeScope.parse(Optional.of(List.of("directive"))).scope();

        ActivationChange change = deactivate(US4, directives, OTHERS, Map.of(), Set.of());

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T", "T2", "T3");
        assertThat(change.skipped().skippedByScope()).containsExactly(T, T2, T3);
        assertThat(change.applied()).containsExactly(D);
        assertThat(CascadeRules.planShared(US4, D, directives, OWN, Set.of()).walks())
                .isFalse();
    }

    @Test
    void anUnselectedTargetIsStillDenied() {
        ActivationChange change =
                deactivate(selecting("directive:D2"), CascadeScope.none(), Reach.empty(), Map.of(), Set.of());

        assertThat(change.applied()).containsExactly(D);
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("D2");
    }

    @Test
    void aMissionTypeCannotBeDenied() {
        ArtifactRef missionType = new ArtifactRef(MISSION_TYPE, "software-dev");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> CascadeRules.planDeactivate(US4, missionType, CascadeScope.none()));
    }
}
