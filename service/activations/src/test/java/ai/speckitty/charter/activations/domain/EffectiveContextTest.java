package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActionContext.AppliesThrough;
import ai.speckitty.charter.activations.domain.ActionContext.Reached;
import ai.speckitty.charter.activations.domain.ActionContext.SuggestionCandidate;
import ai.speckitty.charter.activations.domain.EffectiveContext.Enhancement;
import ai.speckitty.charter.activations.domain.EffectiveContext.Item;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSet.Suggestion;
import ai.speckitty.charter.activations.domain.EffectiveSet.Summary;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The effective context assembled from the effective set and what the walk reached (IC-11, FR-019, rule E16): the listed
 * artifacts are the entries as the list gives them, and each suggestion the walk reached through the effective set's
 * carrier, all in URN order whatever order the walk gave them in; the enhancements are read from the enhanced node's side;
 * and a mission type that is not in force answers nothing and names itself in the skipped report. Each item carries the
 * {@code appliesThrough} of the pass that listed it, a suggestion its candidate's (FR-009, research R4). Every absence is
 * read beside a positive control in the same answer.
 */
class EffectiveContextTest {

    private static final ArtifactRef MISSION_TYPE = new ArtifactRef(ArtifactKind.MISSION_TYPE, "M");
    private static final NodeRef STEP = new NodeRef(NodeKind.ACTION, "M/s");

    private static ArtifactRef ref(String urn) {
        return ArtifactRef.of(Urn.parse(urn).orElseThrow()).orElseThrow();
    }

    /** An artifact as the list gives it: a not-selected one carries the suggestion the list's rule found for it. */
    private static EffectiveArtifact artifact(String urn, State state, Reason reason) {
        return new EffectiveArtifact(
                ref(urn),
                "alpha",
                PackLevel.ORG,
                state,
                false,
                List.of(),
                Policy.OPEN,
                reason,
                reason == Reason.NOT_SELECTED
                        ? Optional.of(new Suggestion(Optional.empty(), Optional.empty()))
                        : Optional.empty());
    }

    private static EffectiveSet set(EffectiveSkippedReport skipped, EffectiveArtifact... artifacts) {
        return new EffectiveSet(List.of(artifacts), skipped, new Summary(0, 0, 0, 0, 0, 0));
    }

    private static EffectiveSet set(EffectiveArtifact... artifacts) {
        return set(EffectiveSkippedReport.empty(), artifacts);
    }

    private static ActionContext walked(
            List<Reached> entries, List<SuggestionCandidate> candidates, List<ArtifactRef> stepEnhancedBy) {
        return new ActionContext(true, entries, candidates, stepEnhancedBy);
    }

    private static Reached reached(String urn, String... enhancers) {
        return reached(urn, AppliesThrough.ACTION, enhancers);
    }

    private static Reached reached(String urn, AppliesThrough appliesThrough, String... enhancers) {
        return new Reached(
                ref(urn),
                java.util.Arrays.stream(enhancers)
                        .map(EffectiveContextTest::ref)
                        .toList(),
                appliesThrough);
    }

    private static SuggestionCandidate candidate(String urn, AppliesThrough appliesThrough) {
        return new SuggestionCandidate(ref(urn), Optional.empty(), Optional.empty(), appliesThrough);
    }

    private static String urnOf(Item item) {
        return item.artifact().ref().urn();
    }

    @Test
    void theContextListsTheEntriesAndTheSuggestionsInUrnOrderWhateverOrderTheWalkGaveThem() {
        EffectiveSet set = set(
                artifact("directive:O", State.ACTIVE, Reason.SELECTED),
                artifact("directive:D2", State.ACTIVE, Reason.SELECTED),
                artifact("directive:D", State.ACTIVE, Reason.SELECTED),
                artifact("tactic:S", State.INACTIVE, Reason.NOT_SELECTED));
        SuggestionCandidate candidate = new SuggestionCandidate(
                ref("tactic:S"), Optional.of("when"), Optional.of("why"), AppliesThrough.ACTION);

        EffectiveContext forward = EffectiveContext.of(
                set,
                walked(List.of(reached("directive:D"), reached("directive:D2")), List.of(candidate), List.of()),
                MISSION_TYPE,
                STEP);
        EffectiveContext backward = EffectiveContext.of(
                set,
                walked(List.of(reached("directive:D2"), reached("directive:D")), List.of(candidate), List.of()),
                MISSION_TYPE,
                STEP);

        assertThat(forward.artifacts())
                .extracting(EffectiveContextTest::urnOf)
                .as("D and D2 are entries, S a suggestion; O is in force but the walk does not list it")
                .containsExactly("directive:D", "directive:D2", "tactic:S");
        assertThat(backward).isEqualTo(forward);
        assertThat(forward.artifacts().get(2).artifact().suggestion())
                .contains(new Suggestion(Optional.of("when"), Optional.of("why")));
        assertThat(forward.artifacts().get(0).artifact().suggestion()).isEmpty();
    }

    @Test
    void aCandidateThatIsOutOfForceForAnotherReasonThanASelectionIsNotListedAndASuggestionIs() {
        EffectiveSet set = set(
                artifact("directive:D", State.ACTIVE, Reason.SELECTED),
                artifact("tactic:Denied", State.EXCLUDED, Reason.DEACTIVATED),
                artifact("tactic:Hidden", State.INACTIVE, Reason.OVERRIDDEN),
                artifact("tactic:S", State.INACTIVE, Reason.NOT_SELECTED));
        List<SuggestionCandidate> candidates = List.of(
                candidate("tactic:Denied", AppliesThrough.ACTION),
                candidate("tactic:Hidden", AppliesThrough.ACTION),
                candidate("tactic:S", AppliesThrough.ACTION));

        EffectiveContext context = EffectiveContext.of(
                set, walked(List.of(reached("directive:D")), candidates, List.of()), MISSION_TYPE, STEP);

        assertThat(context.artifacts())
                .extracting(EffectiveContextTest::urnOf)
                .containsExactly("directive:D", "tactic:S");
    }

    @Test
    void eachItemCarriesTheAppliesThroughOfThePassThatListedIt() {
        EffectiveSet set = set(
                artifact("directive:A", State.ACTIVE, Reason.SELECTED),
                artifact("directive:T", State.ACTIVE, Reason.SELECTED),
                artifact("directive:P", State.ACTIVE, Reason.SELECTED));

        EffectiveContext context = EffectiveContext.of(
                set,
                walked(
                        List.of(
                                reached("directive:P", AppliesThrough.PROJECT),
                                reached("directive:T", AppliesThrough.MISSION_TYPE),
                                reached("directive:A", AppliesThrough.ACTION)),
                        List.of(),
                        List.of()),
                MISSION_TYPE,
                STEP);

        assertThat(context.artifacts())
                .extracting(EffectiveContextTest::urnOf, Item::appliesThrough)
                .as("each pass keeps its own value, whatever the URN order puts first")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("directive:A", AppliesThrough.ACTION),
                        org.assertj.core.groups.Tuple.tuple("directive:P", AppliesThrough.PROJECT),
                        org.assertj.core.groups.Tuple.tuple("directive:T", AppliesThrough.MISSION_TYPE));
    }

    @Test
    void aSuggestionTakesTheAppliesThroughOfItsCandidate() {
        EffectiveSet set = set(
                artifact("directive:A", State.ACTIVE, Reason.SELECTED),
                artifact("directive:P", State.ACTIVE, Reason.SELECTED),
                artifact("tactic:S1", State.INACTIVE, Reason.NOT_SELECTED),
                artifact("tactic:S2", State.INACTIVE, Reason.NOT_SELECTED));

        EffectiveContext context = EffectiveContext.of(
                set,
                walked(
                        List.of(reached("directive:A"), reached("directive:P", AppliesThrough.PROJECT)),
                        List.of(
                                candidate("tactic:S2", AppliesThrough.PROJECT),
                                candidate("tactic:S1", AppliesThrough.ACTION)),
                        List.of()),
                MISSION_TYPE,
                STEP);

        assertThat(context.artifacts())
                .extracting(EffectiveContextTest::urnOf, Item::appliesThrough)
                .as("the suggestion of a project-wide entry is project, the one of an action entry is action")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("directive:A", AppliesThrough.ACTION),
                        org.assertj.core.groups.Tuple.tuple("directive:P", AppliesThrough.PROJECT),
                        org.assertj.core.groups.Tuple.tuple("tactic:S1", AppliesThrough.ACTION),
                        org.assertj.core.groups.Tuple.tuple("tactic:S2", AppliesThrough.PROJECT));
        assertThat(context.artifacts().get(3).artifact().suggestion())
                .as("the item is still the list's suggestion")
                .isPresent();
    }

    @Test
    void eachAppliesThroughHasTheTokenTheContractSpells() {
        assertThat(java.util.Arrays.stream(AppliesThrough.values()).map(AppliesThrough::token))
                .containsExactly("action", "mission_type", "project");
    }

    @Test
    void theEnhancementsAreReadFromTheNodesSideAndOnlyANodeWithAnEnhancerInForceIsListed() {
        EffectiveSet set = set(
                artifact("directive:D", State.ACTIVE, Reason.SELECTED),
                artifact("directive:D2", State.ACTIVE, Reason.SELECTED),
                artifact("tactic:e1", State.ACTIVE, Reason.SELECTED),
                artifact("tactic:e2", State.ACTIVE, Reason.SELECTED),
                artifact("step_enhancement:x", State.ACTIVE, Reason.SELECTED));

        EffectiveContext context = EffectiveContext.of(
                set,
                walked(
                        List.of(reached("directive:D2"), reached("directive:D", "tactic:e2", "tactic:e1")),
                        List.of(),
                        List.of(ref("step_enhancement:x"))),
                MISSION_TYPE,
                STEP);

        assertThat(context.enhancements())
                .containsExactly(
                        new Enhancement(STEP, List.of(ref("step_enhancement:x"))),
                        new Enhancement(
                                new NodeRef(NodeKind.DIRECTIVE, "D"), List.of(ref("tactic:e1"), ref("tactic:e2"))));
        assertThat(context.enhancements())
                .extracting(Enhancement::target)
                .as("D2 has no enhancer in force and is no enhancement")
                .doesNotContain(new NodeRef(NodeKind.DIRECTIVE, "D2"));
    }

    @Test
    void aMissionTypeNotInForceAnswersNothingAndNamesItselfInTheSkippedReport() {
        EffectiveSkippedReport others = new EffectiveSkippedReport(
                List.of(ref("directive:Gone")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(ref("mission_step_contract:other")),
                List.of(),
                List.of());
        EffectiveSet set = set(others, artifact("directive:D", State.ACTIVE, Reason.SELECTED));

        EffectiveContext context = EffectiveContext.of(set, ActionContext.notInForce(), MISSION_TYPE, STEP);

        assertThat(context.artifacts()).isEmpty();
        assertThat(context.enhancements()).isEmpty();
        assertThat(context.skipped().missionTypeNotInForce())
                .as("in an action context the bucket names the mission type itself")
                .containsExactly(MISSION_TYPE);
        assertThat(context.skipped().staleIds())
                .as("the other buckets are the activation's own")
                .containsExactly(ref("directive:Gone"));
    }

    @Test
    void aMissionTypeInForceLeavesTheMissionTypeBucketEmptyAndTheOtherBucketsAsTheyAre() {
        EffectiveSkippedReport others = new EffectiveSkippedReport(
                List.of(ref("directive:Gone")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(ref("mission_step_contract:other")),
                List.of(),
                List.of());
        EffectiveSet set = set(others, artifact("directive:D", State.ACTIVE, Reason.SELECTED));

        EffectiveContext context = EffectiveContext.of(
                set, walked(List.of(reached("directive:D")), List.of(), new ArrayList<>()), MISSION_TYPE, STEP);

        assertThat(context.artifacts()).extracting(EffectiveContextTest::urnOf).containsExactly("directive:D");
        assertThat(context.skipped().missionTypeNotInForce())
                .as(
                        "the bucket is the requested mission type or nothing: the artifact bound to another mission type"
                                + " that is out of force, which the activation-wide report lists, is absent (carrier exception)")
                .isEmpty();
        assertThat(context.skipped().staleIds()).containsExactly(ref("directive:Gone"));
    }
}
