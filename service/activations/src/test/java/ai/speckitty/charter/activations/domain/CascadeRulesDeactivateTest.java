package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.STYLEGUIDE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static ai.speckitty.charter.kernel.domain.RelationType.SPECIALIZES_FROM;
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
        ReachPlan plan = CascadeRules.planShared(
                US4.withDeny(TACTIC, "Z"), D, CascadeScope.all(), OWN, Map.of(), refs("procedure:F"), Reach.empty());

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
        assertThat(CascadeRules.planShared(US4, D, CascadeScope.none(), OWN, Map.of(), Set.of(), Reach.empty())
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
        assertThat(CascadeRules.planShared(US4, D, directives, OWN, Map.of(), Set.of(), Reach.empty())
                        .walks())
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

    // The fixed point: a kept candidate is active after the write, so what it reaches stays too (FR-015).

    /** Drives planShared to its fixed point as the application does, over one closure; answers the last walk. */
    private static Reach fixedPoint(
            ActivationDocument document,
            Reach own,
            Map<ArtifactRef, Set<ArtifactRef>> enhancers,
            Reaches.Graph closure) {
        ReachPlan plan =
                CascadeRules.planShared(document, D, CascadeScope.all(), own, enhancers, Set.of(), Reach.empty());
        Reach answer = closure.apply(plan);
        ReachPlan next = CascadeRules.planShared(document, D, CascadeScope.all(), own, enhancers, Set.of(), answer);
        while (!next.equals(plan)) {
            plan = next;
            answer = closure.apply(plan);
            next = CascadeRules.planShared(document, D, CascadeScope.all(), own, enhancers, Set.of(), answer);
        }
        return answer;
    }

    @Test
    void aCandidateKeptByAnActiveEnhancerKeepsWhatItRequires() {
        Reaches.Graph closure =
                new Reaches.Graph().edge("directive:D", REQUIRES, "tactic:C").edge("tactic:C", REQUIRES, "tactic:C2");
        ActivationDocument document = selecting("directive:D", "tactic:C", "tactic:C2", "styleguide:E2");
        Reach own = closure.apply(CascadeRules.planDeactivate(document, D, CascadeScope.all()));
        Map<ArtifactRef, Set<ArtifactRef>> enhancers = Map.of(ref("tactic:C"), Set.of(E2));

        Reach others = fixedPoint(document, own, enhancers, closure);
        ActivationChange change =
                CascadeRules.applyDeactivate(document, D, CascadeScope.all(), own, others, enhancers, Set.of());

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("C", "C2");
        assertThat(change.skipped().skippedShared())
                .containsExactly(
                        new SkippedReport.SharedSkip(ref("tactic:C"), E2),
                        new SkippedReport.SharedSkip(ref("tactic:C2"), ref("tactic:C")));
        assertThat(change.applied()).containsExactly(D);
    }

    @Test
    void aKeptCandidateThatEnhancesAnotherCandidateKeepsItAndWhatItReaches() {
        Reaches.Graph closure = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:C")
                .edge("directive:D", REQUIRES, "tactic:C3")
                .edge("tactic:C3", REQUIRES, "tactic:C4")
                .edge("directive:D", REQUIRES, "tactic:C5");
        ActivationDocument document =
                selecting("directive:D", "tactic:C", "tactic:C3", "tactic:C4", "tactic:C5", "styleguide:E2");
        Reach own = closure.apply(CascadeRules.planDeactivate(document, D, CascadeScope.all()));
        Map<ArtifactRef, Set<ArtifactRef>> enhancers =
                Map.of(ref("tactic:C"), Set.of(E2), ref("tactic:C3"), Set.of(ref("tactic:C")));

        Reach others = fixedPoint(document, own, enhancers, closure);
        ActivationChange change =
                CascadeRules.applyDeactivate(document, D, CascadeScope.all(), own, others, enhancers, Set.of());

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("C", "C3", "C4");
        assertThat(change.skipped().skippedShared())
                .containsExactly(
                        new SkippedReport.SharedSkip(ref("tactic:C"), E2),
                        new SkippedReport.SharedSkip(ref("tactic:C3"), ref("tactic:C")),
                        new SkippedReport.SharedSkip(ref("tactic:C4"), ref("tactic:C3")));
        assertThat(change.applied()).containsExactly(D, ref("tactic:C5"));
        assertThat(closure.walks()).as("the own walk and two shared steps").isEqualTo(1 + 2);
    }

    @Test
    void stoppingAfterTheFirstStepWouldDropWhatTheFixedPointKeeps() {
        Reaches.Graph closure = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:C")
                .edge("directive:D", REQUIRES, "tactic:C3")
                .edge("tactic:C3", REQUIRES, "tactic:C4");
        ActivationDocument document = selecting("directive:D", "tactic:C", "tactic:C3", "tactic:C4", "styleguide:E2");
        Reach own = closure.apply(CascadeRules.planDeactivate(document, D, CascadeScope.all()));
        Map<ArtifactRef, Set<ArtifactRef>> enhancers =
                Map.of(ref("tactic:C"), Set.of(E2), ref("tactic:C3"), Set.of(ref("tactic:C")));
        ReachPlan first =
                CascadeRules.planShared(document, D, CascadeScope.all(), own, enhancers, Set.of(), Reach.empty());

        Reach once = closure.apply(first);

        assertThat(first.starts()).containsExactly(E2, ref("tactic:C"));
        assertThat(CascadeRules.planShared(document, D, CascadeScope.all(), own, enhancers, Set.of(), once)
                        .starts())
                .containsExactly(E2, ref("tactic:C"), ref("tactic:C3"));
        assertThat(CascadeRules.applyDeactivate(document, D, CascadeScope.all(), own, once, enhancers, Set.of())
                        .after()
                        .selected(TACTIC)
                        .orElseThrow())
                .doesNotContain("C4");
    }

    /** Drives {@link CascadeRules#planShared} to its fixed point over a graph, as the exclusion service does. */
    private static Reach fixedPoint(
            ActivationDocument document, Reach own, Set<ArtifactRef> floor, Reaches.Graph graph) {
        ReachPlan plan = CascadeRules.planShared(document, D, CascadeScope.all(), own, Map.of(), floor, Reach.empty());
        for (int step = 0; step <= own.reached().size(); step++) {
            Reach answer = plan.walks() ? graph.apply(plan) : Reach.empty();
            ReachPlan next = CascadeRules.planShared(document, D, CascadeScope.all(), own, Map.of(), floor, answer);
            if (next.equals(plan)) {
                return answer;
            }
            plan = next;
        }
        throw new AssertionError("The shared walk found no fixed point.");
    }

    @Test
    void aFloorMemberIsNeverACandidateSoItAndWhatItReachesAreKept() {
        // D requires F, which is on the floor and selected; F requires T2, which is selected.
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "directive:F")
                .edge("directive:F", REQUIRES, "tactic:T2");
        ArtifactRef f = ref("directive:F");
        ActivationDocument document = selecting("directive:D", "directive:F", "tactic:T2");
        Reach own = graph.apply(CascadeRules.planDeactivate(document, D, CascadeScope.all()));

        ActivationChange change = CascadeRules.applyDeactivate(
                document, D, CascadeScope.all(), own, fixedPoint(document, own, Set.of(f), graph), Map.of(), Set.of(f));

        assertThat(change.applied()).containsExactly(D);
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("F");
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2");
        assertThat(change.skipped().skippedShared())
                .as("the floor keeps F itself; F keeps T2")
                .containsExactly(new SkippedReport.SharedSkip(f, f), new SkippedReport.SharedSkip(T2, f));
    }

    @Test
    void aSharedCandidateReachedThroughALineageAncestorIsReferencedByTheDescendant() {
        // D requires T2; the selected profile P specializes from A, which requires T2. The shared walk follows the
        // lineage, so T2 is kept, and its referencedBy is P, never the ancestor A.
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:T2")
                .edge("agent_profile:P", SPECIALIZES_FROM, "agent_profile:A")
                .edge("agent_profile:A", REQUIRES, "tactic:T2");
        ActivationDocument document = selecting("directive:D", "agent_profile:P", "tactic:T2");
        Reach own = graph.apply(CascadeRules.planDeactivate(document, D, CascadeScope.all()));

        ActivationChange change = CascadeRules.applyDeactivate(
                document, D, CascadeScope.all(), own, fixedPoint(document, own, Set.of(), graph), Map.of(), Set.of());

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2");
        assertThat(change.skipped().skippedShared())
                .containsExactly(new SkippedReport.SharedSkip(T2, ref("agent_profile:P")));
    }

    @Test
    void aSelectedMissionTypeTheTargetReachesIsOutsideEveryScopeAndStaysSelected() {
        ArtifactRef m = ref("mission_type:m");
        Reach own = reach().start("directive:D")
                .reached("mission_type:m", "directive:D", REQUIRES)
                .build();
        ActivationDocument document = ActivationDocument.builder()
                .missionTypes(List.of("m"))
                .selected(DIRECTIVE, List.of("D"))
                .build()
                .document();

        ActivationChange change =
                CascadeRules.applyDeactivate(document, D, CascadeScope.all(), own, Reach.empty(), Map.of(), Set.of());

        assertThat(change.applied()).containsExactly(D);
        assertThat(change.after().missionTypes().orElseThrow()).containsExactly("m");
        assertThat(change.skipped().skippedByScope()).containsExactly(m);
    }

    @Test
    void theOwnWalkFollowsNoLineageWhileTheSelectAndSharedWalksDo() {
        // Deliberate (FR-015 names five relations, literally): the target's own walk does not pass through ancestors,
        // so what only an ancestor of a reached profile requires is not a candidate. Awaiting an operator ruling.
        ArtifactRef p = ref("agent_profile:P");
        ActivationDocument document = selecting("agent_profile:P", "tactic:T2");

        assertThat(CascadeRules.planSelect(document, p, CascadeScope.all(), false)
                        .lineage())
                .isTrue();
        assertThat(CascadeRules.planDeactivate(document, p, CascadeScope.all()).lineage())
                .isFalse();
        Reach own = reach().start("agent_profile:P")
                .reached("tactic:T2", "agent_profile:P", REQUIRES)
                .build();
        assertThat(CascadeRules.planShared(document, p, CascadeScope.all(), own, Map.of(), Set.of(), Reach.empty())
                        .lineage())
                .isTrue();
    }
}
