package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActionContextWalk.Context;
import ai.speckitty.charter.packs.application.ActionContextWalk.Entry;
import ai.speckitty.charter.packs.application.ActionContextWalk.Step;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The action-context walk (ADR-0018 D2 and D3, rule E16): five breadth-first steps from {@code action:M/a} over the merged
 * index, {@code depth} counting {@code suggests} hops, a node the predicate refuses neither listed nor walked through, each
 * entry with the enhancers in force at either end. One test per step, then the rules the steps share.
 */
class ActionContextWalkTest {

    private static final String START = "action:M/a";

    private static Predicate<Urn> inForce(String... urns) {
        Set<Urn> set = Set.copyOf(Stream.of(urns).map(EdgeIndexTest::urn).toList());
        return set::contains;
    }

    private static boolean everything(Urn urn) {
        return true;
    }

    /** A closure of one pack that holds mission type {@code M}, the step {@code a}, the artifacts and the edges. */
    private static Closure closure(List<String> artifacts, Relationship... edges) {
        List<String> held = new ArrayList<>(List.of("mission_type:M", START));
        held.addAll(artifacts);
        return new Closure().pack("alpha", held, edges);
    }

    private static Context walk(Closure closure, int depth, Predicate<Urn> inForce) {
        return new ActionContextWalk(closure.merged()).walk("M", "a", depth, inForce);
    }

    private static Entry entry(Context context, String urn) {
        Optional<Entry> found = context.entries().stream()
                .filter(candidate -> candidate.urn().equals(urn(urn)))
                .findFirst();
        assertThat(found).as(urn + " is listed").isPresent();
        return found.orElseThrow();
    }

    private static List<String> listed(Context context) {
        return context.entries().stream().map(entry -> entry.urn().toString()).toList();
    }

    // ---- step 1, scope: the action node, one hop -------------------------------------------------------------------

    @Test
    void theScopeStepListsTheArtifactsTheActionNodeScopesAtOneHopFromTheAction() {
        Closure closure = closure(
                List.of("tactic:T2", "tactic:T1", "tactic:Far"),
                edge(START, RelationType.SCOPE, "tactic:T2"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.SCOPE, "tactic:Far"));

        Context context = walk(closure, 0, ActionContextWalkTest::everything);

        assertThat(context.action()).isEqualTo(urn(START));
        assertThat(context.missionTypeInForce()).isTrue();
        assertThat(context.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("tactic:T1"), Step.SCOPE, urn(START), 1),
                        tuple(urn("tactic:T2"), Step.SCOPE, urn(START), 1));
    }

    // ---- step 2, requires: the scoped artifacts, transitive --------------------------------------------------------

    @Test
    void theRequiresStepListsWhatTheScopedArtifactsRequireTransitivelyWithTheirParentAndHops() {
        Closure closure = closure(
                List.of("tactic:T1", "directive:R2", "directive:R3"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.REQUIRES, "directive:R2"),
                edge("directive:R2", RelationType.REQUIRES, "directive:R3"));

        Context context = walk(closure, 0, ActionContextWalkTest::everything);

        assertThat(context.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("directive:R2"), Step.REQUIRES, urn("tactic:T1"), 2),
                        tuple(urn("directive:R3"), Step.REQUIRES, urn("directive:R2"), 3),
                        tuple(urn("tactic:T1"), Step.SCOPE, urn(START), 1));
    }

    // ---- step 3, suggests: the scoped artifacts, depth suggests-steps ----------------------------------------------

    private static Closure suggesting() {
        return closure(
                List.of("tactic:T1", "directive:R2", "tactic:S1", "tactic:S2", "tactic:S3"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.REQUIRES, "directive:R2"),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:S1"),
                edge("directive:R2", RelationType.SUGGESTS, "tactic:S2"),
                edge("tactic:S1", RelationType.SUGGESTS, "tactic:S3"));
    }

    @Test
    void theSuggestsStepAtDepthOneListsTheSuggestionsOfTheScopedArtifactsOnly() {
        Context context = walk(suggesting(), 1, ActionContextWalkTest::everything);

        assertThat(listed(context))
                .as("S2 is suggested by a required artifact and S3 by a suggestion: neither is followed")
                .containsExactly("directive:R2", "tactic:S1", "tactic:T1");
        assertThat(entry(context, "tactic:S1"))
                .extracting(Entry::step, Entry::via, Entry::hops)
                .containsExactly(Step.SUGGESTS, urn("tactic:T1"), 2);
    }

    @Test
    void depthZeroFollowsNoSuggestsEdge() {
        assertThat(listed(walk(suggesting(), 0, ActionContextWalkTest::everything)))
                .containsExactly("directive:R2", "tactic:T1");
    }

    @Test
    void aSuggestedEntryCarriesTheWhenAndTheReasonOfTheEdgeThatReachedIt() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:S1", "tactic:S2"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                new Relationship(
                        urn("tactic:T1"),
                        urn("tactic:S1"),
                        RelationType.SUGGESTS,
                        Optional.of("the change is large"),
                        Optional.of("S1 checks it")),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:S2"));

        Context context = walk(closure, 1, ActionContextWalkTest::everything);

        assertThat(entry(context, "tactic:S1").when()).contains("the change is large");
        assertThat(entry(context, "tactic:S1").reason()).contains("S1 checks it");
        assertThat(entry(context, "tactic:S2").when())
                .as("an edge may state neither")
                .isEmpty();
        assertThat(entry(context, "tactic:S2").reason()).isEmpty();
        assertThat(entry(context, "tactic:T1").when())
                .as("only a suggested entry carries them")
                .isEmpty();
    }

    @Test
    void theWordsOfAScopeRequiresOrRequiresOfSuggestedEdgeNeverReachTheActionContext() {
        Closure closure = closure(
                List.of("tactic:T1", "directive:R2", "tactic:S1", "directive:Q1"),
                worded(START, RelationType.SCOPE, "tactic:T1"),
                worded("tactic:T1", RelationType.REQUIRES, "directive:R2"),
                worded("tactic:T1", RelationType.SUGGESTS, "tactic:S1"),
                worded("tactic:S1", RelationType.REQUIRES, "directive:Q1"));

        Context context = walk(closure, 1, ActionContextWalkTest::everything);

        assertThat(context.entries())
                .extracting(Entry::urn, Entry::step)
                .containsExactly(
                        tuple(urn("directive:Q1"), Step.REQUIRES_OF_SUGGESTED),
                        tuple(urn("directive:R2"), Step.REQUIRES),
                        tuple(urn("tactic:S1"), Step.SUGGESTS),
                        tuple(urn("tactic:T1"), Step.SCOPE));
        for (String unworded : List.of("tactic:T1", "directive:R2", "directive:Q1")) {
            assertThat(entry(context, unworded).when())
                    .as("the when of the edge to %s is not an action-context word", unworded)
                    .isEmpty();
            assertThat(entry(context, unworded).reason())
                    .as("the reason of the edge to %s is not an action-context word", unworded)
                    .isEmpty();
        }
        assertThat(entry(context, "tactic:S1").when()).contains("when " + "tactic:S1");
        assertThat(entry(context, "tactic:S1").reason()).contains("because " + "tactic:S1");
    }

    private static Relationship worded(String from, RelationType relation, String to) {
        return new Relationship(urn(from), urn(to), relation, Optional.of("when " + to), Optional.of("because " + to));
    }

    // ---- step 4, requires of the suggested artifacts, transitive; never walked for suggests ------------------------

    @Test
    void theRequiresOfSuggestedStepListsWhatTheSuggestedArtifactsRequireAndWalksNoSuggestsFromThem() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:S1", "directive:Q1", "directive:Q2", "tactic:Never"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:S1"),
                edge("tactic:S1", RelationType.REQUIRES, "directive:Q1"),
                edge("directive:Q1", RelationType.REQUIRES, "directive:Q2"),
                edge("directive:Q1", RelationType.SUGGESTS, "tactic:Never"));

        Context withSuggestions = walk(closure, 1, ActionContextWalkTest::everything);
        Context without = walk(closure, 0, ActionContextWalkTest::everything);

        assertThat(withSuggestions.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("directive:Q1"), Step.REQUIRES_OF_SUGGESTED, urn("tactic:S1"), 3),
                        tuple(urn("directive:Q2"), Step.REQUIRES_OF_SUGGESTED, urn("directive:Q1"), 4),
                        tuple(urn("tactic:S1"), Step.SUGGESTS, urn("tactic:T1"), 2),
                        tuple(urn("tactic:T1"), Step.SCOPE, urn(START), 1));
        assertThat(listed(without)).as("no suggestion, so nothing to require").containsExactly("tactic:T1");
    }

    // ---- step 5, vocabulary: every artifact steps 1 to 4 list, one hop ---------------------------------------------

    @Test
    void theVocabularyStepListsOneHopFromEveryArtifactTheEarlierStepsListAndNoFurther() {
        Closure closure = closure(
                List.of(
                        "tactic:T1",
                        "directive:R2",
                        "tactic:S1",
                        "directive:Q1",
                        "glossary_pack:G1",
                        "glossary_pack:G2",
                        "glossary_pack:G3",
                        "glossary_pack:G4",
                        "glossary_pack:Deep"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.REQUIRES, "directive:R2"),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:S1"),
                edge("tactic:S1", RelationType.REQUIRES, "directive:Q1"),
                edge("tactic:T1", RelationType.VOCABULARY, "glossary_pack:G1"),
                edge("directive:R2", RelationType.VOCABULARY, "glossary_pack:G2"),
                edge("tactic:S1", RelationType.VOCABULARY, "glossary_pack:G3"),
                edge("directive:Q1", RelationType.VOCABULARY, "glossary_pack:G4"),
                edge("glossary_pack:G1", RelationType.VOCABULARY, "glossary_pack:Deep"));

        Context context = walk(closure, 1, ActionContextWalkTest::everything);

        assertThat(context.entries())
                .filteredOn(entry -> entry.step() == Step.VOCABULARY)
                .extracting(Entry::urn, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("glossary_pack:G1"), urn("tactic:T1"), 2),
                        tuple(urn("glossary_pack:G2"), urn("directive:R2"), 3),
                        tuple(urn("glossary_pack:G3"), urn("tactic:S1"), 3),
                        tuple(urn("glossary_pack:G4"), urn("directive:Q1"), 4));
        assertThat(listed(context)).doesNotContain("glossary_pack:Deep");
    }

    // ---- the rules the steps share ---------------------------------------------------------------------------------

    @Test
    void theEntriesAreSortedByFullUrnStringWhateverStepListedThem() {
        Closure closure = closure(
                List.of("tactic:B", "tactic:A", "directive:R", "tactic:S", "glossary_pack:G"),
                edge(START, RelationType.SCOPE, "tactic:B"),
                edge(START, RelationType.SCOPE, "tactic:A"),
                edge("tactic:A", RelationType.REQUIRES, "directive:R"),
                edge("tactic:B", RelationType.SUGGESTS, "tactic:S"),
                edge("tactic:B", RelationType.VOCABULARY, "glossary_pack:G"));

        assertThat(listed(walk(closure, 1, ActionContextWalkTest::everything)))
                .as("ADR-0018 D3: sorted by full URN string, not by step")
                .containsExactly("directive:R", "glossary_pack:G", "tactic:A", "tactic:B", "tactic:S");
    }

    @Test
    void anArtifactAnEarlierStepListedIsNeverListedAgainAndStillSeedsTheLaterSteps() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:T2", "glossary_pack:G"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge(START, RelationType.SCOPE, "tactic:T2"),
                edge("tactic:T1", RelationType.REQUIRES, "tactic:T2"),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:T2"),
                edge("tactic:T2", RelationType.VOCABULARY, "glossary_pack:G"));

        Context context = walk(closure, 1, ActionContextWalkTest::everything);

        assertThat(listed(context)).containsExactly("glossary_pack:G", "tactic:T1", "tactic:T2");
        assertThat(entry(context, "tactic:T2").step()).isEqualTo(Step.SCOPE);
        assertThat(entry(context, "glossary_pack:G").via()).isEqualTo(urn("tactic:T2"));
    }

    @Test
    void aNodeTakesAsParentTheOneWithTheFewestHopsThenTheSmallestUrn() {
        Closure closure = closure(
                List.of("tactic:T1", "directive:R2", "glossary_pack:G", "glossary_pack:H"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.REQUIRES, "directive:R2"),
                edge("directive:R2", RelationType.VOCABULARY, "glossary_pack:G"),
                edge("tactic:T1", RelationType.VOCABULARY, "glossary_pack:G"),
                edge("tactic:T1", RelationType.VOCABULARY, "glossary_pack:H"),
                edge("directive:R2", RelationType.VOCABULARY, "glossary_pack:H"));

        Context context = walk(closure, 0, ActionContextWalkTest::everything);

        assertThat(entry(context, "glossary_pack:G").via())
                .as("T1 has 1 hop, R2 has 2")
                .isEqualTo(urn("tactic:T1"));
        assertThat(entry(context, "glossary_pack:G").hops()).isEqualTo(2);
        assertThat(entry(context, "glossary_pack:H").via()).isEqualTo(urn("tactic:T1"));
    }

    @Test
    void aSiblingParentOfEqualHopsIsBrokenBySmallestUrn() {
        Closure closure = closure(
                List.of("tactic:A", "tactic:B", "directive:X"),
                edge(START, RelationType.SCOPE, "tactic:B"),
                edge(START, RelationType.SCOPE, "tactic:A"),
                edge("tactic:B", RelationType.REQUIRES, "directive:X"),
                edge("tactic:A", RelationType.REQUIRES, "directive:X"));

        assertThat(entry(walk(closure, 0, ActionContextWalkTest::everything), "directive:X")
                        .via())
                .isEqualTo(urn("tactic:A"));
    }

    @Test
    void aNodeThePredicateRefusesIsNeitherListedNorWalkedThrough() {
        Closure closure = closure(
                List.of("tactic:T1", "directive:Out", "directive:Behind", "tactic:Refused", "tactic:S1"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge(START, RelationType.SCOPE, "tactic:Refused"),
                edge("tactic:T1", RelationType.REQUIRES, "directive:Out"),
                edge("directive:Out", RelationType.REQUIRES, "directive:Behind"),
                edge("tactic:Refused", RelationType.SUGGESTS, "tactic:S1"),
                edge("tactic:T1", RelationType.SUGGESTS, "directive:Behind"));

        Context context = walk(closure, 1, inForce("mission_type:M", "tactic:T1", "directive:Behind", "tactic:S1"));

        assertThat(listed(context))
                .as("Out is refused, so Behind is not reached through it; Behind is reached only as a suggestion")
                .containsExactly("directive:Behind", "tactic:T1");
        assertThat(entry(context, "directive:Behind").step()).isEqualTo(Step.SUGGESTS);
    }

    @Test
    void theActionNodeIsTheStartWhateverThePredicateSaysOfIt() {
        Closure closure = closure(List.of("tactic:T1"), edge(START, RelationType.SCOPE, "tactic:T1"));

        Context context = walk(closure, 0, inForce("mission_type:M", "tactic:T1"));

        assertThat(listed(context)).containsExactly("tactic:T1");
    }

    @Test
    void aMissionTypeNotInForceAnswersNoEntry() {
        Closure closure = closure(List.of("tactic:T1"), edge(START, RelationType.SCOPE, "tactic:T1"));

        Context context = walk(closure, 1, inForce("tactic:T1"));

        assertThat(context.missionTypeInForce()).isFalse();
        assertThat(context.entries()).isEmpty();
    }

    // ---- enhancers, both ways (FR-015, rule E12) -------------------------------------------------------------------

    @Test
    void eachEntryBringsTheEnhancersInForceThatEnhanceItAndTheOnesItEnhancesAndPullsNoneIn() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:T2", "skill:E1", "skill:Idle", "skill:Target"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge(START, RelationType.SCOPE, "tactic:T2"),
                edge("skill:E1", RelationType.ENHANCES, "tactic:T1"),
                edge("skill:Idle", RelationType.ENHANCES, "tactic:T1"),
                edge("tactic:T2", RelationType.ENHANCES, "skill:Target"),
                edge("tactic:T2", RelationType.ENHANCES, "tactic:T1"));

        Context context =
                walk(closure, 0, inForce("mission_type:M", "tactic:T1", "tactic:T2", "skill:E1", "skill:Target"));

        assertThat(listed(context)).as("an enhancer is never pulled in").containsExactly("tactic:T1", "tactic:T2");
        assertThat(entry(context, "tactic:T1").enhancedBy())
                .as("skill:Idle is not in force")
                .containsExactly(urn("skill:E1"), urn("tactic:T2"));
        assertThat(entry(context, "tactic:T2").enhances()).containsExactly(urn("skill:Target"), urn("tactic:T1"));
        assertThat(entry(context, "tactic:T1").enhances()).isEmpty();
    }

    // ---- unknown names, judged against the merged closure (FR-019) -------------------------------------------------

    @Test
    void anUnknownMissionTypeIsMissionTypeNotFound() {
        Closure closure = closure(List.of());

        assertThatThrownBy(() ->
                        new ActionContextWalk(closure.merged()).walk("Nope", "a", 0, ActionContextWalkTest::everything))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
    }

    @Test
    void anUnknownActionOfAKnownMissionTypeIsActionNotFound() {
        Closure closure = closure(List.of());

        assertThatThrownBy(() ->
                        new ActionContextWalk(closure.merged()).walk("M", "nope", 0, ActionContextWalkTest::everything))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
    }

    @Test
    void theNamesAreJudgedWhetherOrNotTheMissionTypeIsInForce() {
        Closure closure = closure(List.of());

        assertThatThrownBy(() -> new ActionContextWalk(closure.merged()).walk("M", "nope", 0, inForce()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
    }

    @Test
    void anActionDeclaredWhereNoPackDefinesItsMissionTypeIsActionNotFound() {
        // OD-9: the predicate is "a pack defines the mission type and declares the action", so the walk and the
        // name check cannot use "the mission type is held and the action is declared somewhere".
        Closure closure = new Closure()
                .pack("alpha", PackLevel.ORG, List.of("mission_type:M", "action:M/a"))
                .pack("beta", PackLevel.PROJECT, List.of("action:M/stray", "tactic:T"));

        assertThatThrownBy(() -> new ActionContextWalk(closure.merged())
                        .walk("M", "stray", 0, ActionContextWalkTest::everything))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
    }

    @Test
    void theWalkStartsAtAHeldStepWhoseWinnerIsAPackThatDoesNotDefineTheMissionType() {
        // OD-9, closure form: the project pack overrides action:M/a without defining M, and the walk starts from the
        // winner and follows its edges.
        Closure closure = new Closure()
                .pack("org", PackLevel.ORG, List.of("mission_type:M", "action:M/a", "tactic:Old"))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("action:M/a", "tactic:New"),
                        edge(START, RelationType.SCOPE, "tactic:New"));

        Context context = walk(closure, 0, ActionContextWalkTest::everything);

        assertThat(listed(context)).containsExactly("tactic:New");
    }

    @Test
    void theAnswerDoesNotDependOnTheOrderTheEdgesWereWritten() {
        List<Relationship> written = Arrays.asList(
                edge(START, RelationType.SCOPE, "tactic:B"),
                edge(START, RelationType.SCOPE, "tactic:A"),
                edge("tactic:A", RelationType.REQUIRES, "directive:X"),
                edge("tactic:B", RelationType.REQUIRES, "directive:X"),
                edge("tactic:B", RelationType.SUGGESTS, "tactic:S"),
                edge("tactic:A", RelationType.SUGGESTS, "tactic:S"),
                edge("tactic:S", RelationType.VOCABULARY, "glossary_pack:G"),
                edge("tactic:A", RelationType.VOCABULARY, "glossary_pack:G"));
        List<String> artifacts = List.of("tactic:A", "tactic:B", "directive:X", "tactic:S", "glossary_pack:G");

        Context forward =
                walk(closure(artifacts, written.toArray(Relationship[]::new)), 1, ActionContextWalkTest::everything);
        Context backward = walk(
                closure(artifacts.reversed(), written.reversed().toArray(Relationship[]::new)),
                1,
                ActionContextWalkTest::everything);

        assertThat(backward).isEqualTo(forward);
    }

    @Test
    void aNegativeDepthIsRefused() {
        Closure closure = closure(List.of());

        assertThatThrownBy(() -> walk(closure, -1, ActionContextWalkTest::everything))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- suggestions out of force (decision 01M7059T4K64TAVKXN3T1FKWYG) -------------------------------------------

    private static Closure suggestingOutOfForce() {
        return closure(
                List.of("tactic:T1", "tactic:S", "directive:R", "glossary_pack:G"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                new Relationship(
                        urn("tactic:T1"),
                        urn("tactic:S"),
                        RelationType.SUGGESTS,
                        Optional.of("the change is large"),
                        Optional.of("S checks it")),
                edge("tactic:S", RelationType.REQUIRES, "directive:R"),
                edge("tactic:S", RelationType.VOCABULARY, "glossary_pack:G"));
    }

    @Test
    void aSuggestsTargetOutOfForceIsReportedWithItsEdgeDataAndNotExpanded() {
        Predicate<Urn> withoutS = inForce("mission_type:M", "tactic:T1", "directive:R", "glossary_pack:G");

        Context context = walk(suggestingOutOfForce(), 1, withoutS);

        assertThat(context.suggestionCandidates())
                .extracting(
                        ActionContextWalk.SuggestionCandidate::urn,
                        ActionContextWalk.SuggestionCandidate::via,
                        ActionContextWalk.SuggestionCandidate::hops,
                        ActionContextWalk.SuggestionCandidate::when,
                        ActionContextWalk.SuggestionCandidate::reason)
                .containsExactly(tuple(
                        urn("tactic:S"),
                        urn("tactic:T1"),
                        2,
                        Optional.of("the change is large"),
                        Optional.of("S checks it")));
        assertThat(listed(context))
                .as("S is no entry, its requires target R and its vocabulary G are not reached through it")
                .containsExactly("tactic:T1");
    }

    @Test
    void theSameSuggestsTargetInForceIsAnEntryAndIsExpanded() {
        Predicate<Urn> withS = inForce("mission_type:M", "tactic:T1", "tactic:S", "directive:R", "glossary_pack:G");

        Context context = walk(suggestingOutOfForce(), 1, withS);

        assertThat(context.suggestionCandidates()).isEmpty();
        assertThat(listed(context)).containsExactly("directive:R", "glossary_pack:G", "tactic:S", "tactic:T1");
    }

    @Test
    void aTargetAListedArtifactAlsoReachesThroughARequiresIsNotReportedAsASuggestion() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:T2", "tactic:S"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge(START, RelationType.SCOPE, "tactic:T2"),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:S"),
                edge("tactic:T2", RelationType.REQUIRES, "tactic:S"));

        Context context = walk(closure, 1, inForce("mission_type:M", "tactic:T1", "tactic:T2"));

        assertThat(context.suggestionCandidates()).isEmpty();
    }

    @Test
    void depthZeroReportsNoSuggestion() {
        Predicate<Urn> withoutS = inForce("mission_type:M", "tactic:T1", "directive:R", "glossary_pack:G");

        assertThat(walk(suggestingOutOfForce(), 0, withoutS).suggestionCandidates())
                .isEmpty();
    }

    @Test
    void
            severalSuggestsEdgesToOneTargetTakeTheParentWithTheFewestHopsThenTheSmallestSourceAndTheCandidatesAreInUrnOrder() {
        Closure closure = closure(
                List.of("tactic:B", "tactic:A", "tactic:S2", "tactic:S1"),
                edge(START, RelationType.SCOPE, "tactic:B"),
                edge(START, RelationType.SCOPE, "tactic:A"),
                edge("tactic:B", RelationType.SUGGESTS, "tactic:S2"),
                edge("tactic:A", RelationType.SUGGESTS, "tactic:S2"),
                edge("tactic:B", RelationType.SUGGESTS, "tactic:S1"));

        Context context = walk(closure, 1, inForce("mission_type:M", "tactic:A", "tactic:B"));

        assertThat(context.suggestionCandidates())
                .extracting(ActionContextWalk.SuggestionCandidate::urn, ActionContextWalk.SuggestionCandidate::via)
                .containsExactly(tuple(urn("tactic:S1"), urn("tactic:B")), tuple(urn("tactic:S2"), urn("tactic:A")));
    }

    @Test
    void onlyASuggestsLinkMakesACandidateNotAnotherRelationAScopedArtifactReachesATargetBy() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:Out"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.ENHANCES, "tactic:Out"));

        Context context = walk(closure, 2, inForce("mission_type:M", "tactic:T1"));

        assertThat(context.suggestionCandidates())
                .as("an enhances link to an out-of-force target is no suggestion")
                .isEmpty();
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aCycleOfRequiresEndsTheWalkAndListsEachArtifactOnce() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:T2"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.REQUIRES, "tactic:T2"),
                edge("tactic:T2", RelationType.REQUIRES, "tactic:T1"));

        Context context = walk(closure, 1, ActionContextWalkTest::everything);

        assertThat(listed(context)).containsExactly("tactic:T1", "tactic:T2");
    }

    @Test
    void aSuggestedArtifactIsStillACandidateWhenAnotherLinkOfTheSameSourceReachesAnotherTargetOutOfForce() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:S", "tactic:O"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:S"),
                edge("tactic:T1", RelationType.OVERRIDES, "tactic:O"));

        Context context = walk(closure, 1, inForce("mission_type:M", "tactic:T1"));

        assertThat(context.suggestionCandidates())
                .extracting(ActionContextWalk.SuggestionCandidate::urn)
                .containsExactly(urn("tactic:S"));
    }

    @Test
    void anArtifactTheActionItselfScopesIsNeverACandidateWhateverAListedArtifactSuggests() {
        Closure closure = closure(
                List.of("tactic:T1", "tactic:Scoped"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge(START, RelationType.SCOPE, "tactic:Scoped"),
                edge("tactic:T1", RelationType.SUGGESTS, "tactic:Scoped"));

        Context context = walk(closure, 1, inForce("mission_type:M", "tactic:T1"));

        assertThat(context.suggestionCandidates()).isEmpty();
    }

    // ---- the enhancers of the start step, both ways ----------------------------------------------------------------

    private static Closure enhancingTheStep() {
        return closure(
                List.of("tactic:T1", "skill:E2", "skill:E1", "skill:Idle", "tactic:Target", "tactic:IdleTarget"),
                edge(START, RelationType.SCOPE, "tactic:T1"),
                edge("skill:E2", RelationType.ENHANCES, START),
                edge("skill:E1", RelationType.ENHANCES, START),
                edge("skill:Idle", RelationType.ENHANCES, START),
                edge(START, RelationType.ENHANCES, "tactic:Target"),
                edge(START, RelationType.ENHANCES, "tactic:IdleTarget"));
    }

    @Test
    void theContextCarriesTheEnhancersInForceOfTheStartStepAndTheArtifactsInForceItEnhancesInUrnOrder() {
        Context context = walk(
                enhancingTheStep(), 0, inForce("mission_type:M", "tactic:T1", "skill:E1", "skill:E2", "tactic:Target"));

        assertThat(context.actionEnhancedBy())
                .as("skill:Idle is not in force")
                .containsExactly(urn("skill:E1"), urn("skill:E2"));
        assertThat(context.actionEnhances())
                .as("tactic:IdleTarget is not in force")
                .containsExactly(urn("tactic:Target"));
        assertThat(listed(context))
                .as("none of them is pulled into the answer (rule E12)")
                .containsExactly("tactic:T1");
    }

    @Test
    void aStartStepNobodyEnhancesAndThatEnhancesNothingHasNoEnhancers() {
        Context context = walk(
                closure(List.of("tactic:T1"), edge(START, RelationType.SCOPE, "tactic:T1")),
                0,
                ActionContextWalkTest::everything);

        assertThat(context.actionEnhancedBy()).isEmpty();
        assertThat(context.actionEnhances()).isEmpty();
    }

    @Test
    void aMissionTypeNotInForceAnswersNoEnhancerOfTheStartStepEither() {
        Context context = walk(enhancingTheStep(), 0, inForce("skill:E1", "tactic:Target"));

        assertThat(context.missionTypeInForce()).isFalse();
        assertThat(context.actionEnhancedBy()).isEmpty();
        assertThat(context.actionEnhances()).isEmpty();
    }
}
