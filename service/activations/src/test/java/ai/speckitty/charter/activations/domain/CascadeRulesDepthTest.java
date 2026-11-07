package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static ai.speckitty.charter.kernel.domain.RelationType.SUGGESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * FR-043, IC-21 (issue #408 ruling 1): {@code depth} counts the {@code suggests} hops of a cascading select and of a
 * cascading deactivate's own walk. The plan of the target's walk carries the bound; the shared walk of the other active
 * artifacts never does (decision DM-01M6TF3BA808JMW0W0A0CPXBNW), so a bound never drops a candidate another active
 * artifact still reaches. {@code skippedByDepth} is the next {@code suggests} frontier, reported only when a cascade
 * and a depth were sent. The fixture: LEAD requires CORE and suggests HA; O suggests H; T requires H.
 */
class CascadeRulesDepthTest {

    private static final ArtifactRef LEAD = ref("agent_profile:LEAD");
    private static final ArtifactRef T = ref("directive:T");
    private static final ArtifactRef H = ref("directive:H");
    private static final ArtifactRef O = ref("directive:O");
    private static final ActivationDocument EMPTY =
            ActivationDocument.builder().build().document();

    private static Reach withBeyond(Reach walked, String... beyond) {
        return new Reach(
                walked.reached(),
                walked.stopped(),
                walked.behindStopped(),
                walked.ancestorsPassed(),
                walked.ancestry(),
                refs(beyond));
    }

    private static ActivationDocument selecting(String... urns) {
        ActivationDocument document = EMPTY;
        for (ArtifactRef ref : refs(urns)) {
            document = document.withSelected(ref.kind(), ref.id());
        }
        return document;
    }

    @Test
    void aSelectPlanCarriesTheBoundAndStillWalksEveryCascadeRelation() {
        ReachPlan plan = CascadeRules.planSelect(EMPTY, LEAD, CascadeScope.all(), false, OptionalInt.of(1));

        assertThat(plan.suggestsBound()).isEqualTo(OptionalInt.of(1));
        assertThat(plan.relations()).isEqualTo(CascadeRules.CASCADE_RELATIONS);
        assertThat(plan.lineage()).isTrue();
        assertThat(plan.walks()).isTrue();
    }

    @Test
    void aSelectPlanWithoutDepthIsUnlimited() {
        assertThat(CascadeRules.planSelect(EMPTY, LEAD, CascadeScope.all(), false)
                        .suggestsBound())
                .isEmpty();
        assertThat(CascadeRules.planSelect(EMPTY, LEAD, CascadeScope.all(), false, OptionalInt.empty())
                        .suggestsBound())
                .isEmpty();
    }

    @Test
    void aDepthWithoutACascadeIsAcceptedAndWalksNothing() {
        ReachPlan plan = CascadeRules.planSelect(EMPTY, LEAD, CascadeScope.none(), false, OptionalInt.of(0));

        assertThat(plan.walks()).isFalse();
        assertThat(plan.suggestsBound()).isEmpty();
    }

    @Test
    void aDepthOfZeroIsABoundNotAnAbsence() {
        ReachPlan plan = CascadeRules.planSelect(EMPTY, LEAD, CascadeScope.all(), false, OptionalInt.of(0));

        assertThat(plan.suggestsBound()).isEqualTo(OptionalInt.of(0));
    }

    @Test
    void aNegativeBoundIsRefusedByThePlan() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReachPlan(Set.of(LEAD), Set.of(SUGGESTS), Set.of(), true, OptionalInt.of(-1)))
                .withMessageContaining("-1");
    }

    @Test
    void theDeactivateOwnPlanCarriesTheBoundAndTheSharedPlanNever() {
        ActivationDocument document = selecting("directive:O", "directive:T", "directive:H");
        Reach own = reach().start("directive:T")
                .reached("directive:H", "directive:T", REQUIRES)
                .build();

        ReachPlan ownPlan = CascadeRules.planDeactivate(document, T, CascadeScope.all(), OptionalInt.of(0));
        ReachPlan sharedPlan = CascadeRules.planShared(document, T, CascadeScope.all(), own, Set.of());

        assertThat(ownPlan.suggestsBound()).isEqualTo(OptionalInt.of(0));
        assertThat(sharedPlan.walks()).isTrue();
        assertThat(sharedPlan.suggestsBound()).isEmpty();
        assertThat(CascadeRules.planDeactivate(document, T, CascadeScope.all()).suggestsBound())
                .isEmpty();
    }

    @Test
    void aSelectReportsTheFrontierPastTheBoundSorted() {
        Reach walked = withBeyond(
                reach().start("agent_profile:LEAD")
                        .reached("directive:CORE", "agent_profile:LEAD", REQUIRES)
                        .build(),
                "directive:HB",
                "directive:HA");

        ActivationChange change =
                CascadeRules.applySelect(EMPTY, LEAD, CascadeScope.all(), false, OptionalInt.of(0), walked);

        assertThat(change.skipped().skippedByDepth()).contains(List.of(ref("directive:HA"), ref("directive:HB")));
        assertThat(change.applied()).containsExactly(LEAD, ref("directive:CORE"));
    }

    @Test
    void aFrontierThatNoReferenceCanNameIsLeftOut() {
        Reach walked = withBeyond(reach().start("agent_profile:LEAD").build(), "template:tpl.md", "directive:HA");

        ActivationChange change =
                CascadeRules.applySelect(EMPTY, LEAD, CascadeScope.all(), false, OptionalInt.of(0), walked);

        assertThat(change.skipped().skippedByDepth()).contains(List.of(ref("directive:HA")));
    }

    @Test
    void aBoundThatCutNothingReportsAnEmptyFrontier() {
        Reach walked = reach().start("agent_profile:LEAD").build();

        ActivationChange change =
                CascadeRules.applySelect(EMPTY, LEAD, CascadeScope.all(), false, OptionalInt.of(2), walked);

        assertThat(change.skipped().skippedByDepth()).contains(List.of());
    }

    @Test
    void aSelectWithoutDepthReportsNoFrontier() {
        Reach walked = withBeyond(reach().start("agent_profile:LEAD").build(), "directive:HA");

        ActivationChange without = CascadeRules.applySelect(EMPTY, LEAD, CascadeScope.all(), false, walked);
        ActivationChange absent =
                CascadeRules.applySelect(EMPTY, LEAD, CascadeScope.all(), false, OptionalInt.empty(), walked);

        assertThat(without.skipped().skippedByDepth()).isEmpty();
        assertThat(absent.skipped().skippedByDepth()).isEmpty();
    }

    @Test
    void aSelectWithDepthButNoCascadeReportsNoFrontier() {
        ActivationChange change = CascadeRules.applySelect(
                EMPTY, LEAD, CascadeScope.none(), false, OptionalInt.of(0), withBeyond(Reach.empty(), "directive:HA"));

        assertThat(change.skipped().skippedByDepth()).isEmpty();
        assertThat(change.applied()).containsExactly(LEAD);
    }

    @Test
    void aDeactivateReportsTheFrontierOfItsOwnWalk() {
        ActivationDocument document = selecting("directive:O", "directive:T", "directive:H", "directive:HA");
        Reach own = withBeyond(
                reach().start("directive:T")
                        .reached("directive:H", "directive:T", REQUIRES)
                        .build(),
                "directive:HA");

        ActivationChange change = CascadeRules.applyDeactivate(
                document, T, CascadeScope.all(), OptionalInt.of(0), own, Reach.empty(), Set.of());

        assertThat(change.skipped().skippedByDepth()).contains(List.of(ref("directive:HA")));
    }

    @Test
    void aDeactivateWithoutDepthReportsNoFrontier() {
        ActivationDocument document = selecting("directive:T");
        Reach own = reach().start("directive:T").build();

        ActivationChange change =
                CascadeRules.applyDeactivate(document, T, CascadeScope.all(), own, Reach.empty(), Set.of());

        assertThat(change.skipped().skippedByDepth()).isEmpty();
    }

    /**
     * Decision DM-01M6TF3BA808JMW0W0A0CPXBNW: O (selected at depth 0) suggests H, and T (selected at depth 0) requires H.
     * The deactivate of T at depth 0 has H as a candidate through its own walk, and the unlimited shared walk of O
     * reaches H over {@code suggests}, so H is kept and reported, referenced by O.
     */
    @Test
    void theRoundTripIsNotExactWhenAnotherActiveArtifactSuggestsACandidate() {
        ActivationDocument document = selecting("directive:O", "directive:T", "directive:H");
        Reaches.Graph graph = new Reaches.Graph()
                .edge("directive:T", REQUIRES, "directive:H")
                .edge("directive:O", SUGGESTS, "directive:H");
        OptionalInt depth = OptionalInt.of(0);
        Reach own = graph.apply(CascadeRules.planDeactivate(document, T, CascadeScope.all(), depth));
        ReachPlan shared = CascadeRules.planShared(document, T, CascadeScope.all(), own, Set.of());
        Reach others = graph.apply(shared);

        ActivationChange change =
                CascadeRules.applyDeactivate(document, T, CascadeScope.all(), depth, own, others, Set.of());

        assertThat(shared.suggestsBound()).isEmpty();
        assertThat(change.applied()).containsExactly(T);
        assertThat(change.after().selected(H.kind()).orElseThrow()).containsExactly("H", "O");
        assertThat(change.skipped().skippedShared()).containsExactly(new SkippedReport.SharedSkip(H, O));
        assertThat(change.skipped().skippedByDepth()).contains(List.of());
    }

    @Test
    void aSelectReportsNoFrontierThatADeeperWalkWouldNeverSelect() {
        ActivationDocument document = EMPTY.withDeny(ref("directive:DENIED").kind(), "DENIED")
                .withSelected(ref("directive:SELECTED").kind(), "SELECTED");
        CascadeScope directivesOnly =
                CascadeScope.parse(Optional.of(List.of("directive"))).scope();
        Reach walked = withBeyond(
                reach().start("agent_profile:LEAD").build(), "directive:DENIED", "tactic:OUT", "directive:SELECTED");

        ActivationChange change =
                CascadeRules.applySelect(document, LEAD, directivesOnly, false, OptionalInt.of(0), walked);

        assertThat(change.skipped().skippedByDepth()).contains(List.of());
    }

    @Test
    void aSelectStillReportsAFreshInScopeFrontier() {
        ActivationDocument document = EMPTY.withDeny(ref("directive:DENIED").kind(), "DENIED");
        Reach walked = withBeyond(reach().start("agent_profile:LEAD").build(), "directive:DENIED", "directive:HA");

        ActivationChange change =
                CascadeRules.applySelect(document, LEAD, CascadeScope.all(), false, OptionalInt.of(0), walked);

        assertThat(change.skipped().skippedByDepth()).contains(List.of(ref("directive:HA")));
    }

    @Test
    void aDeniedFrontierIsReportedWhenIncludeDeactivatedTakesItBack() {
        ActivationDocument document = EMPTY.withDeny(ref("directive:DENIED").kind(), "DENIED");
        Reach walked = withBeyond(reach().start("agent_profile:LEAD").build(), "directive:DENIED");

        ActivationChange change =
                CascadeRules.applySelect(document, LEAD, CascadeScope.all(), true, OptionalInt.of(0), walked);

        assertThat(change.skipped().skippedByDepth()).contains(List.of(ref("directive:DENIED")));
    }

    @Test
    void aDeactivateReportsNoFrontierThatIsNotActiveOrNotInScope() {
        ActivationDocument document = selecting("directive:T", "tactic:OUT");
        CascadeScope directivesOnly =
                CascadeScope.parse(Optional.of(List.of("directive"))).scope();
        Reach own =
                withBeyond(reach().start("directive:T").build(), "directive:NOT_ACTIVE", "tactic:OUT", "directive:HA");

        ActivationChange change = CascadeRules.applyDeactivate(
                document, T, directivesOnly, OptionalInt.of(0), own, Reach.empty(), Set.of());

        assertThat(change.skipped().skippedByDepth()).contains(List.of());
    }
}
