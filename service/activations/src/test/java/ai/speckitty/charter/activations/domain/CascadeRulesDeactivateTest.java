package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.ENHANCES;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static ai.speckitty.charter.kernel.domain.RelationType.SPECIALIZES_FROM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * FR-015, US4 AS1 and AS2: a cascading deactivate unselects what only the target reached, keeps what another active
 * artifact (selected or on the floor) reaches, and denies only the target. {@code enhances} is a cascade relation like
 * the others (ADR-0059 D2 as amended on 2026-11-06), so an active enhancer keeps its target through the shared walk.
 * The fixture: D requires T, T2 and T3; D2 requires T2; E2 enhances T3.
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

    /** D2 reaches T2; E2 reaches T3 over enhances. */
    private static final Reach OTHERS = reach().start("directive:D2")
            .start("styleguide:E2")
            .reached("tactic:T2", "directive:D2", REQUIRES)
            .reached("tactic:T3", "styleguide:E2", ENHANCES)
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
            ActivationDocument document, CascadeScope scope, Reach others, Set<ArtifactRef> floor) {
        return CascadeRules.applyDeactivate(document, D, scope, OWN, others, floor);
    }

    /** Deactivates D with a cascade over a graph, walking the target's reach and the shared reach as the service does. */
    private static ActivationChange deactivate(
            ActivationDocument document, Set<ArtifactRef> floor, Reaches.Graph graph) {
        Reach own = graph.apply(CascadeRules.planDeactivate(document, D, CascadeScope.all()));
        ReachPlan shared = CascadeRules.planShared(document, D, CascadeScope.all(), own, floor);
        Reach others = shared.walks() ? graph.apply(shared) : Reach.empty();
        return CascadeRules.applyDeactivate(document, D, CascadeScope.all(), own, others, floor);
    }

    @Test
    void theOwnPlanWalksTheSixRelationsAndStopsNowhere() {
        ReachPlan plan = CascadeRules.planDeactivate(US4, D, CascadeScope.all());

        assertThat(plan.starts()).containsExactly(D);
        assertThat(plan.relations()).isEqualTo(CascadeRules.CASCADE_RELATIONS).hasSize(6);
        assertThat(plan.stopAt()).isEmpty();
    }

    @Test
    void theSharedPlanStartsFromTheOtherActiveArtifactsAndStopsAtTheTarget() {
        ReachPlan plan =
                CascadeRules.planShared(US4.withDeny(TACTIC, "Z"), D, CascadeScope.all(), OWN, refs("procedure:F"));

        assertThat(plan.starts()).containsExactly(ref("directive:D2"), ref("procedure:F"), E2);
        assertThat(plan.relations()).isEqualTo(CascadeRules.CASCADE_RELATIONS);
        assertThat(plan.stopAt()).containsExactly(D, ref("tactic:Z"));
        assertThat(plan.lineage()).isTrue();
    }

    @Test
    void whatOnlyTheTargetReachedLeavesAndWhatAnotherReachesIsKept() {
        ActivationChange change = deactivate(US4, CascadeScope.all(), OTHERS, Set.of());

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
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:T3")
                .edge("styleguide:E9", ENHANCES, "tactic:T3");

        ActivationChange change = deactivate(selecting("directive:D", "tactic:T3"), Set.of(), graph);

        assertThat(change.applied()).containsExactly(D, T3);
        assertThat(change.skipped().skippedShared()).isEmpty();
    }

    @Test
    void anEnhancerThatIsItselfACandidateKeepsNothing() {
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:T3")
                .edge("directive:D", REQUIRES, "styleguide:E3")
                .edge("styleguide:E3", ENHANCES, "tactic:T3");

        ActivationChange change = deactivate(selecting("directive:D", "styleguide:E3", "tactic:T3"), Set.of(), graph);

        assertThat(change.applied()).containsExactly(D, ref("styleguide:E3"), T3);
    }

    @Test
    void aFloorArtifactKeepsWhatItReaches() {
        Reach others = reach().start("directive:D2")
                .start("procedure:F")
                .reached("tactic:T2", "directive:D2", REQUIRES)
                .reached("tactic:T3", "procedure:F", REQUIRES)
                .build();

        ActivationChange change = deactivate(US4, CascadeScope.all(), others, refs("procedure:F"));

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2", "T3");
        assertThat(change.skipped().skippedShared()).contains(new SkippedReport.SharedSkip(T3, ref("procedure:F")));
    }

    @Test
    void withoutACascadeOnlyTheTargetLeavesAndIsDenied() {
        ReachPlan plan = CascadeRules.planDeactivate(US4, D, CascadeScope.none());

        ActivationChange change = deactivate(US4, CascadeScope.none(), Reach.empty(), Set.of());

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

        ActivationChange change = deactivate(US4, directives, OTHERS, Set.of());

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T", "T2", "T3");
        assertThat(change.skipped().skippedByScope()).containsExactly(T, T2, T3);
        assertThat(change.applied()).containsExactly(D);
        assertThat(CascadeRules.planShared(US4, D, directives, OWN, Set.of()).walks())
                .isFalse();
    }

    @Test
    void anUnselectedTargetIsStillDenied() {
        ActivationChange change = deactivate(selecting("directive:D2"), CascadeScope.none(), Reach.empty(), Set.of());

        assertThat(change.applied()).containsExactly(D);
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("D2");
    }

    @Test
    void aMissionTypeIsDeniedLikeAnyKind() {
        // A7 Q2: a mission type has a deny list; the floor refusal is DocumentRules', before any plan.
        ArtifactRef missionType = new ArtifactRef(MISSION_TYPE, "software-dev");
        ActivationDocument document = ActivationDocument.builder()
                .selected(MISSION_TYPE, List.of("software-dev"))
                .build()
                .document();

        ReachPlan plan = CascadeRules.planDeactivate(document, missionType, CascadeScope.none());
        ActivationChange change = CascadeRules.applyDeactivate(
                document, missionType, CascadeScope.none(), Reach.empty(), Reach.empty(), Set.of());

        assertThat(plan.walks()).isFalse();
        assertThat(change.applied()).containsExactly(missionType);
        assertThat(change.after().selected(MISSION_TYPE)).isEmpty();
        assertThat(change.after().deactivated(MISSION_TYPE)).containsExactly("software-dev");
    }

    @Test
    void aKindWithoutADenyListIsStillRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CascadeRules.planDeactivate(US4, ref("template:t"), CascadeScope.none()));
    }

    // One shared walk: it goes on through a kept candidate, so what that candidate reaches stays too (FR-015).

    @Test
    void aCandidateKeptByAnActiveEnhancerKeepsWhatItRequires() {
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:C")
                .edge("tactic:C", REQUIRES, "tactic:C2")
                .edge("styleguide:E2", ENHANCES, "tactic:C");

        ActivationChange change =
                deactivate(selecting("directive:D", "tactic:C", "tactic:C2", "styleguide:E2"), Set.of(), graph);

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("C", "C2");
        assertThat(change.skipped().skippedShared())
                .containsExactly(
                        new SkippedReport.SharedSkip(ref("tactic:C"), E2),
                        new SkippedReport.SharedSkip(ref("tactic:C2"), ref("tactic:C")));
        assertThat(change.applied()).containsExactly(D);
    }

    @Test
    void aKeptCandidateThatEnhancesAnotherCandidateKeepsItAndWhatItReachesInOneSharedWalk() {
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:C")
                .edge("directive:D", REQUIRES, "tactic:C3")
                .edge("tactic:C3", REQUIRES, "tactic:C4")
                .edge("directive:D", REQUIRES, "tactic:C5")
                .edge("styleguide:E2", ENHANCES, "tactic:C")
                .edge("tactic:C", ENHANCES, "tactic:C3");
        ActivationDocument document =
                selecting("directive:D", "tactic:C", "tactic:C3", "tactic:C4", "tactic:C5", "styleguide:E2");

        ActivationChange change = deactivate(document, Set.of(), graph);

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("C", "C3", "C4");
        assertThat(change.skipped().skippedShared())
                .containsExactly(
                        new SkippedReport.SharedSkip(ref("tactic:C"), E2),
                        new SkippedReport.SharedSkip(ref("tactic:C3"), ref("tactic:C")),
                        new SkippedReport.SharedSkip(ref("tactic:C4"), ref("tactic:C3")));
        assertThat(change.applied()).containsExactly(D, ref("tactic:C5"));
        assertThat(graph.walks()).as("the own walk and one shared walk").isEqualTo(2);
    }

    @Test
    void aFloorMemberIsNeverACandidateSoItAndWhatItReachesAreKept() {
        // D requires F, which is on the floor and selected; F requires T2, which is selected.
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "directive:F")
                .edge("directive:F", REQUIRES, "tactic:T2");
        ArtifactRef f = ref("directive:F");

        ActivationChange change = deactivate(selecting("directive:D", "directive:F", "tactic:T2"), Set.of(f), graph);

        assertThat(change.applied()).containsExactly(D);
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("F");
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2");
        assertThat(change.skipped().skippedShared())
                .as("no other active artifact reaches F, so F is not reported; F keeps T2")
                .containsExactly(new SkippedReport.SharedSkip(T2, f));
    }

    @Test
    void aFloorMemberAnotherActiveArtifactReachesIsReportedNamedByThatArtifact() {
        // D and E both require F, which is on the floor and selected; E stays selected, so E keeps F.
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "directive:F")
                .edge("directive:E", REQUIRES, "directive:F");
        ArtifactRef f = ref("directive:F");
        ArtifactRef e = ref("directive:E");

        ActivationChange change = deactivate(selecting("directive:D", "directive:E", "directive:F"), Set.of(f), graph);

        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("E", "F");
        assertThat(change.skipped().skippedShared())
                .as("never F itself: the active artifact that still references it")
                .containsExactly(new SkippedReport.SharedSkip(f, e));
    }

    @Test
    void aSharedCandidateReachedThroughALineageAncestorIsReferencedByTheDescendant() {
        // D requires T2; the selected profile P specializes from A, which requires T2. The shared walk follows the
        // lineage, so T2 is kept, and its referencedBy is P, never the ancestor A.
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:D", REQUIRES, "tactic:T2")
                .edge("agent_profile:P", SPECIALIZES_FROM, "agent_profile:A")
                .edge("agent_profile:A", REQUIRES, "tactic:T2");

        ActivationChange change = deactivate(selecting("directive:D", "agent_profile:P", "tactic:T2"), Set.of(), graph);

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T2");
        assertThat(change.skipped().skippedShared())
                .containsExactly(new SkippedReport.SharedSkip(T2, ref("agent_profile:P")));
    }

    @Test
    void aSelectedMissionTypeOnlyTheTargetReachesIsACandidateAndLeaves() {
        // Rule W21/W23 (A7): mission_type is in the all scope, so a selected one only the target reaches leaves.
        ArtifactRef m = ref("mission_type:m");
        Reach own = reach().start("directive:D")
                .reached("mission_type:m", "directive:D", REQUIRES)
                .build();
        ActivationDocument document = ActivationDocument.builder()
                .selected(MISSION_TYPE, List.of("m"))
                .selected(DIRECTIVE, List.of("D"))
                .build()
                .document();

        ActivationChange change =
                CascadeRules.applyDeactivate(document, D, CascadeScope.all(), own, Reach.empty(), Set.of());

        assertThat(change.applied()).containsExactly(D, m);
        assertThat(change.after().selected(MISSION_TYPE)).isEmpty();
        assertThat(change.after().deactivated(MISSION_TYPE)).isEmpty();
        assertThat(change.skipped().skippedByScope()).isEmpty();
    }

    @Test
    void aCascadingDeactivateOfAMissionTypeUnselectsWhatOnlyItsStepsKept() {
        ArtifactRef m = ref("mission_type:m");
        Reach own = reach().start("mission_type:m")
                .reached("directive:S", "mission_type:m", REQUIRES)
                .build();
        ActivationDocument document = ActivationDocument.builder()
                .selected(MISSION_TYPE, List.of("m"))
                .selected(DIRECTIVE, List.of("S"))
                .build()
                .document();

        ActivationChange change =
                CascadeRules.applyDeactivate(document, m, CascadeScope.all(), own, Reach.empty(), Set.of());

        assertThat(change.applied()).containsExactly(ref("directive:S"), m);
        assertThat(change.after().selected()).isEmpty();
        assertThat(change.after().deactivated(MISSION_TYPE)).containsExactly("m");
    }

    @Test
    void theOwnWalkFollowsTheLineageAsTheSelectAndSharedWalksDo() {
        // Operator ruling of 2026-11-06 (ADR-0059 D2 amendment, FR-015): the deactivate walks the lineage exactly as
        // the select does, so a cascading deactivate undoes a cascading select.
        ArtifactRef p = ref("agent_profile:P");
        ActivationDocument document = selecting("agent_profile:P", "tactic:T2");

        assertThat(CascadeRules.planSelect(document, p, CascadeScope.all(), false)
                        .lineage())
                .isTrue();
        assertThat(CascadeRules.planDeactivate(document, p, CascadeScope.all()).lineage())
                .isTrue();
        Reach own = reach().start("agent_profile:P")
                .reached("tactic:T2", "agent_profile:P", REQUIRES)
                .build();
        assertThat(CascadeRules.planShared(document, p, CascadeScope.all(), own, Set.of())
                        .lineage())
                .isTrue();
    }

    @Test
    void aCascadingDeactivateUnselectsWhatTheTargetReachedThroughItsAncestor() {
        // P specializes from Q, and Q requires R: selecting P with a cascade selected R, so deactivating it unselects
        // R.
        ActivationChange change = deactivateProfile(selecting("agent_profile:P", "directive:R"));

        assertThat(change.applied()).containsExactly(ref("agent_profile:P"), ref("directive:R"));
        assertThat(change.after().selected(DIRECTIVE)).isEmpty();
        assertThat(change.skipped().skippedShared()).isEmpty();
    }

    @Test
    void whatTheAncestorReachesIsKeptWhenAnotherActiveArtifactNeedsIt() {
        // Q is selected on its own and requires R, so R stays, named by Q.
        ActivationChange change = deactivateProfile(selecting("agent_profile:P", "agent_profile:Q", "directive:R"));

        assertThat(change.applied()).containsExactly(ref("agent_profile:P"));
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("R");
        assertThat(change.skipped().skippedShared())
                .containsExactly(new SkippedReport.SharedSkip(ref("directive:R"), ref("agent_profile:Q")));
    }

    /** Deactivates P with a cascade over a closure where P specializes from Q and Q requires R. */
    private static ActivationChange deactivateProfile(ActivationDocument document) {
        ArtifactRef p = ref("agent_profile:P");
        Reaches.Graph graph = new Reaches.Graph()
                .edge("agent_profile:P", SPECIALIZES_FROM, "agent_profile:Q")
                .edge("agent_profile:Q", REQUIRES, "directive:R");
        Reach own = graph.apply(CascadeRules.planDeactivate(document, p, CascadeScope.all()));
        Reach others = graph.apply(CascadeRules.planShared(document, p, CascadeScope.all(), own, Set.of()));
        return CascadeRules.applyDeactivate(document, p, CascadeScope.all(), own, others, Set.of());
    }
}
