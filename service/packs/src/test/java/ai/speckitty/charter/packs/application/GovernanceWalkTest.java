package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import ai.speckitty.charter.packs.application.GovernanceWalk.Entry;
import ai.speckitty.charter.packs.application.GovernanceWalk.Result;
import ai.speckitty.charter.packs.application.GovernanceWalk.Step;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The profile walk: the action walk's rules from {@code agent_profile:P}. {@code LINEAGE} first, then {@code requires},
 * {@code suggests}, the requires of the suggested artifacts and the vocabulary, each seeded by the profile and the
 * ancestors in force. Every case builds its own fixture.
 */
class GovernanceWalkTest {

    private static final String P = "agent_profile:P";
    private static final String Q = "agent_profile:Q";
    private static final String R = "agent_profile:R";

    private static boolean everything(Urn urn) {
        return true;
    }

    private static Predicate<Urn> inForce(String... urns) {
        Set<Urn> set = Set.copyOf(Stream.of(urns).map(EdgeIndexTest::urn).toList());
        return set::contains;
    }

    private static Closure closure(List<String> artifacts, Relationship... edges) {
        return new Closure().pack("alpha", artifacts, edges);
    }

    private static Result walk(Closure closure, int depth, Predicate<Urn> inForce) {
        return GovernanceWalk.over(closure.merged()).walk(urn(P), depth, inForce);
    }

    private static Entry entry(Result result, String urn) {
        Optional<Entry> found = result.entries().stream()
                .filter(candidate -> candidate.urn().equals(urn(urn)))
                .findFirst();
        assertThat(found).as(urn + " is listed").isPresent();
        return found.orElseThrow();
    }

    private static List<String> listed(Result result) {
        return result.entries().stream().map(entry -> entry.urn().toString()).toList();
    }

    @Test
    void theLineageOfTwoLevelsListsTheAncestorsAndTheirRequiresAndSuggestsWithTheAncestorAsParent() {
        Closure closure = closure(
                List.of(P, Q, R, "directive:Own", "directive:FromQ", "tactic:FromR"),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(Q, RelationType.SPECIALIZES_FROM, R),
                edge(P, RelationType.REQUIRES, "directive:Own"),
                edge(Q, RelationType.REQUIRES, "directive:FromQ"),
                edge(R, RelationType.SUGGESTS, "tactic:FromR"));

        Result result = walk(closure, 1, GovernanceWalkTest::everything);

        assertThat(result.profile()).isEqualTo(urn(P));
        assertThat(result.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn(Q), Step.LINEAGE, urn(P), 1),
                        tuple(urn(R), Step.LINEAGE, urn(Q), 2),
                        tuple(urn("directive:FromQ"), Step.REQUIRES, urn(Q), 2),
                        tuple(urn("directive:Own"), Step.REQUIRES, urn(P), 1),
                        tuple(urn("tactic:FromR"), Step.SUGGESTS, urn(R), 3));
        assertThat(entry(result, Q).relation()).isEqualTo(RelationType.SPECIALIZES_FROM);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void aSpecializesFromCycleTerminatesAndListsEveryNodeOnceWithoutTheStart() {
        Closure closure = closure(
                List.of(P, Q, R),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(Q, RelationType.SPECIALIZES_FROM, R),
                edge(R, RelationType.SPECIALIZES_FROM, P),
                edge(R, RelationType.SPECIALIZES_FROM, Q));

        Result result = walk(closure, 3, GovernanceWalkTest::everything);

        assertThat(result.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(tuple(urn(Q), Step.LINEAGE, urn(P), 1), tuple(urn(R), Step.LINEAGE, urn(Q), 2));
    }

    @Test
    void aTargetReachedByRequiresAndBySuggestsIsListedOnceByTheMinimumStep() {
        Closure closure = closure(
                List.of(P, "tactic:X", "tactic:Y"),
                edge(P, RelationType.SUGGESTS, "tactic:X"),
                edge(P, RelationType.REQUIRES, "tactic:X"),
                edge(P, RelationType.SUGGESTS, "tactic:Y"),
                edge("tactic:Y", RelationType.REQUIRES, "tactic:X"));

        Result result = walk(closure, 2, GovernanceWalkTest::everything);

        assertThat(result.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("tactic:X"), Step.REQUIRES, urn(P), 1),
                        tuple(urn("tactic:Y"), Step.SUGGESTS, urn(P), 1));
    }

    @Test
    void theRequiresOfASuggestedArtifactAreListedByTheirOwnStepAndTheSuggestionsOfARequiredOneAreNot() {
        Closure closure = closure(
                List.of(P, "tactic:S", "directive:RS", "directive:Req", "tactic:OfReq"),
                edge(P, RelationType.SUGGESTS, "tactic:S"),
                edge("tactic:S", RelationType.REQUIRES, "directive:RS"),
                edge(P, RelationType.REQUIRES, "directive:Req"),
                edge("directive:Req", RelationType.SUGGESTS, "tactic:OfReq"));

        Result result = walk(closure, 1, GovernanceWalkTest::everything);

        assertThat(result.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("directive:RS"), Step.REQUIRES_OF_SUGGESTED, urn("tactic:S"), 2),
                        tuple(urn("directive:Req"), Step.REQUIRES, urn(P), 1),
                        tuple(urn("tactic:S"), Step.SUGGESTS, urn(P), 1));
        assertThat(entry(result, "directive:RS").relation()).isEqualTo(RelationType.REQUIRES);
    }

    @Test
    void theProfilesOwnVocabularyEdgeIsListedAndSoIsTheVocabularyOfWhatTheWalkListed() {
        Closure closure = closure(
                List.of(P, "directive:D", "glossary_pack:G1", "glossary_pack:G2"),
                edge(P, RelationType.VOCABULARY, "glossary_pack:G1"),
                edge(P, RelationType.REQUIRES, "directive:D"),
                edge("directive:D", RelationType.VOCABULARY, "glossary_pack:G2"));

        Result result = walk(closure, 0, GovernanceWalkTest::everything);

        assertThat(result.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("directive:D"), Step.REQUIRES, urn(P), 1),
                        tuple(urn("glossary_pack:G1"), Step.VOCABULARY, urn(P), 1),
                        tuple(urn("glossary_pack:G2"), Step.VOCABULARY, urn("directive:D"), 2));
    }

    private static Relationship worded(String from, RelationType type, String to, String when, String reason) {
        return new Relationship(
                urn(from),
                Optional.empty(),
                urn(to),
                Optional.empty(),
                type,
                Optional.ofNullable(when),
                Optional.ofNullable(reason));
    }

    @Test
    void everyEntryCarriesTheWordsOfTheEdgeThatReachedItWhateverItsRelation() {
        Closure closure = closure(
                List.of(P, Q, "tactic:S", "tactic:SR", "directive:D", "directive:V", "glossary_pack:G"),
                worded(P, RelationType.SPECIALIZES_FROM, Q, null, "the profile builds on Q"),
                worded(P, RelationType.SUGGESTS, "tactic:S", "the task touches the schema", "migrations need it"),
                worded("tactic:S", RelationType.REQUIRES, "tactic:SR", "always", null),
                worded(P, RelationType.REQUIRES, "directive:D", "always", "the charter says so"),
                worded(P, RelationType.VOCABULARY, "glossary_pack:G", null, "its words are used"),
                edge(P, RelationType.SUGGESTS, "directive:V"));

        Result result = walk(closure, 2, GovernanceWalkTest::everything);

        assertThat(entry(result, "agent_profile:Q"))
                .extracting(Entry::when, Entry::reason)
                .containsExactly(Optional.empty(), Optional.of("the profile builds on Q"));
        assertThat(entry(result, "tactic:S"))
                .extracting(Entry::when, Entry::reason)
                .containsExactly(Optional.of("the task touches the schema"), Optional.of("migrations need it"));
        assertThat(entry(result, "tactic:SR"))
                .extracting(Entry::step, Entry::when, Entry::reason)
                .containsExactly(Step.REQUIRES_OF_SUGGESTED, Optional.of("always"), Optional.empty());
        assertThat(entry(result, "directive:D"))
                .extracting(Entry::step, Entry::when, Entry::reason)
                .containsExactly(Step.REQUIRES, Optional.of("always"), Optional.of("the charter says so"));
        assertThat(entry(result, "glossary_pack:G"))
                .extracting(Entry::when, Entry::reason)
                .containsExactly(Optional.empty(), Optional.of("its words are used"));
        assertThat(entry(result, "directive:V"))
                .extracting(Entry::step, Entry::when, Entry::reason)
                .containsExactly(Step.SUGGESTS, Optional.empty(), Optional.empty());
    }

    @Test
    void aTargetReachedByRequiresAndSuggestsTakesTheWordsOfTheEdgeOfTheStepThatListedIt() {
        Closure closure = closure(
                List.of(P, "tactic:X"),
                worded(P, RelationType.SUGGESTS, "tactic:X", "suggested when", "suggested because"),
                worded(P, RelationType.REQUIRES, "tactic:X", "required when", "required because"));

        Result result = walk(closure, 1, GovernanceWalkTest::everything);

        assertThat(entry(result, "tactic:X"))
                .extracting(Entry::step, Entry::when, Entry::reason)
                .containsExactly(Step.REQUIRES, Optional.of("required when"), Optional.of("required because"));
    }

    @Test
    void aTargetTwoEdgesOfOneStepReachTakesTheWordsOfTheEdgeOfTheParentTheWalkKeeps() {
        Closure closure = closure(
                List.of(P, "directive:A", "directive:B", "tactic:X"),
                edge(P, RelationType.REQUIRES, "directive:A"),
                edge(P, RelationType.REQUIRES, "directive:B"),
                worded("directive:B", RelationType.REQUIRES, "tactic:X", null, "B needs it"),
                worded("directive:A", RelationType.REQUIRES, "tactic:X", null, "A needs it"));

        Result result = walk(closure, 0, GovernanceWalkTest::everything);

        assertThat(entry(result, "tactic:X"))
                .extracting(Entry::via, Entry::reason)
                .containsExactly(urn("directive:A"), Optional.of("A needs it"));
    }

    @Test
    void anAncestorInForceSeedsRequiresAndSuggestsAndAnAncestorOutOfForceIsNotListedAndSeedsNothing() {
        Closure closure = closure(
                List.of(P, Q, R, "directive:Own", "directive:FromQ", "tactic:FromQ", "directive:FromR"),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(Q, RelationType.SPECIALIZES_FROM, R),
                edge(P, RelationType.REQUIRES, "directive:Own"),
                edge(Q, RelationType.REQUIRES, "directive:FromQ"),
                edge(Q, RelationType.SUGGESTS, "tactic:FromQ"),
                edge(R, RelationType.REQUIRES, "directive:FromR"));

        Result inForceAll = walk(closure, 1, GovernanceWalkTest::everything);
        Result withoutQ =
                walk(closure, 1, inForce("directive:Own", "directive:FromQ", "tactic:FromQ", R, "directive:FromR"));

        assertThat(listed(inForceAll))
                .containsExactlyInAnyOrder(Q, R, "directive:Own", "directive:FromQ", "tactic:FromQ", "directive:FromR");
        assertThat(listed(withoutQ))
                .as(
                        "Q is not in force: it is not listed, nothing it requires or suggests is, and R behind it is not reached")
                .containsExactly("directive:Own");
    }

    @Test
    void aStartTheAppliedPredicateRefusesIsStillWalked() {
        Closure closure = closure(
                List.of(P, Q, "directive:Own", "directive:FromQ"),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(P, RelationType.REQUIRES, "directive:Own"),
                edge(Q, RelationType.REQUIRES, "directive:FromQ"));

        Result result = walk(closure, 0, inForce(Q, "directive:Own", "directive:FromQ"));

        assertThat(listed(result)).containsExactly(Q, "directive:FromQ", "directive:Own");
        assertThat(result.entries()).extracting(Entry::urn).doesNotContain(urn(P));
    }

    @Test
    void aSuggestedTargetTheClosureHoldsAndThePredicateRefusesIsACandidateNeitherListedNorExpanded() {
        Closure closure = closure(
                List.of(P, "tactic:Out", "directive:BehindOut", "tactic:In"),
                edge(P, RelationType.SUGGESTS, "tactic:Out"),
                edge(P, RelationType.SUGGESTS, "tactic:In"),
                edge("tactic:Out", RelationType.REQUIRES, "directive:BehindOut"));

        Result result = walk(closure, 2, inForce("tactic:In", "directive:BehindOut"));

        assertThat(listed(result)).containsExactly("tactic:In");
        assertThat(result.suggestionCandidates())
                .extracting(
                        ActionContextWalk.SuggestionCandidate::urn,
                        ActionContextWalk.SuggestionCandidate::via,
                        ActionContextWalk.SuggestionCandidate::hops)
                .containsExactly(tuple(urn("tactic:Out"), urn(P), 1));
    }

    @Test
    void theNearestParentWinsWhereSeveralEntriesReachOneTargetAndTheTieGoesToTheSmallestUrn() {
        Closure closure = closure(
                List.of(P, Q, R, "directive:D"),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(P, RelationType.SPECIALIZES_FROM, R),
                edge(Q, RelationType.REQUIRES, "directive:D"),
                edge(R, RelationType.REQUIRES, "directive:D"));

        Result result = walk(closure, 0, GovernanceWalkTest::everything);

        assertThat(entry(result, "directive:D"))
                .extracting(Entry::via, Entry::hops)
                .containsExactly(urn(Q), 2);
    }

    // ---- written links: a target no pack holds is listed, held = false, never expanded
    // --------------------------------

    @Test
    void aParentNoPackOfTheClosureHoldsIsListedAsNotHeld() {
        Closure closure = closure(
                List.of(P, "directive:Own"),
                edge(P, RelationType.SPECIALIZES_FROM, "agent_profile:Gone"),
                edge(P, RelationType.REQUIRES, "directive:Own"));

        Result written = GovernanceWalk.overWritten(closure.merged()).walk(urn(P), 1, GovernanceWalkTest::everything);

        assertThat(written.entries())
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::held)
                .containsExactly(
                        tuple(urn("agent_profile:Gone"), Step.LINEAGE, urn(P), false),
                        tuple(urn("directive:Own"), Step.REQUIRES, urn(P), true));
        assertThat(listed(walk(closure, 1, GovernanceWalkTest::everything)))
                .as("the held-links walk, the activation-scoped one, never lists a target nobody holds")
                .containsExactly("directive:Own");
    }

    @Test
    void aParentOnlyADependencyHoldsIsListedNotHeldAndNoLaterStepListsAnythingBelowIt() {
        Closure closure = new Closure()
                .pack(
                        "builtin",
                        List.of(),
                        List.of("agent_profile:Base", "directive:D", "tactic:T", "directive:DofT", "glossary_pack:G"),
                        edge("agent_profile:Base", RelationType.REQUIRES, "directive:D"),
                        edge("agent_profile:Base", RelationType.SUGGESTS, "tactic:T"),
                        edge("tactic:T", RelationType.REQUIRES, "directive:DofT"),
                        edge("agent_profile:Base", RelationType.VOCABULARY, "glossary_pack:G"))
                .pack(
                        "qa",
                        List.of("builtin"),
                        List.of(P),
                        edge(P, RelationType.SPECIALIZES_FROM, "agent_profile:Base"));

        Result written = GovernanceWalk.overWritten(closure.merged()).walk(urn(P), 2, GovernanceWalkTest::everything);

        assertThat(written.entries())
                .as("the bare parent means qa's own Base, which qa does not hold: builtin's Base edges are not P's")
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::held)
                .containsExactly(tuple(urn("agent_profile:Base"), Step.LINEAGE, urn(P), false));
        assertThat(listed(walk(closure, 2, GovernanceWalkTest::everything)))
                .as("the activation walk over the same closure lists neither Base nor anything below it")
                .isEmpty();
    }

    @Test
    void everyEntryOfAHeldTargetIsHeld() {
        Closure closure = closure(
                List.of(P, Q, "directive:D"),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(Q, RelationType.REQUIRES, "directive:D"));

        assertThat(GovernanceWalk.overWritten(closure.merged())
                        .walk(urn(P), 0, GovernanceWalkTest::everything)
                        .entries())
                .extracting(Entry::held)
                .containsOnly(true);
        assertThat(walk(closure, 0, GovernanceWalkTest::everything).entries())
                .extracting(Entry::held)
                .containsOnly(true);
    }

    @Test
    void aBareTargetOnlyAnotherPackHoldsIsListedNotHeldInThePackScopedWalkAndNotAtAllInTheActivationWalk() {
        Closure closure = new Closure()
                .pack(
                        "builtin",
                        List.of(),
                        List.of("directive:D"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"))
                .pack("qa", List.of("builtin"), List.of(P), edge(P, RelationType.REQUIRES, "directive:D"));

        Result written = GovernanceWalk.overWritten(closure.merged()).walk(urn(P), 1, GovernanceWalkTest::everything);

        assertThat(written.entries())
                .as("the bare target means qa's own D, which qa does not hold: listed, not held, never expanded")
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::held)
                .containsExactly(tuple(urn("directive:D"), Step.REQUIRES, urn(P), false));
        assertThat(listed(walk(closure, 1, GovernanceWalkTest::everything)))
                .as("the activation walk resolves the same edge the same way and drops it")
                .isEmpty();
    }

    @Test
    void aQualifiedTargetItsNamedPackHoldsIsHeldAndItsOwnEdgesAreWalked() {
        Closure closure = new Closure()
                .pack(
                        "builtin",
                        List.of(),
                        List.of("directive:D", "tactic:T"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"))
                .pack("qa", List.of("builtin"), List.of(P), edge(P, RelationType.REQUIRES, "builtin::directive:D"));

        Result written = GovernanceWalk.overWritten(closure.merged()).walk(urn(P), 1, GovernanceWalkTest::everything);

        assertThat(written.entries())
                .extracting(Entry::urn, Entry::held)
                .containsExactly(tuple(urn("directive:D"), true), tuple(urn("tactic:T"), true));
        assertThat(listed(walk(closure, 1, GovernanceWalkTest::everything))).containsExactly("directive:D", "tactic:T");
    }

    @Test
    void anUnheldNodeIsNeverExpandedByAnyStepEvenWhereAnotherPackHoldsTheSameUrnWithEdgesOfItsOwn() {
        Closure closure = new Closure()
                .pack(
                        "builtin",
                        List.of(),
                        List.of("directive:D", "tactic:Below", "tactic:Suggested", "glossary_pack:G"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:Below"),
                        edge("directive:D", RelationType.SUGGESTS, "tactic:Suggested"),
                        edge("directive:D", RelationType.VOCABULARY, "glossary_pack:G"))
                .pack("qa", List.of("builtin"), List.of(P), edge(P, RelationType.REQUIRES, "directive:D"));

        assertThat(GovernanceWalk.overWritten(closure.merged())
                        .walk(urn(P), 2, GovernanceWalkTest::everything)
                        .entries())
                .as("builtin's D requires, suggests and has vocabulary, but the node reached through the dangling edge"
                        + " seeds no step")
                .extracting(Entry::urn)
                .containsExactly(urn("directive:D"));
    }

    @Test
    void anUnheldNodeIsNotWalkedThroughWhenALaterStepReachesItThroughAHeldEdge() {
        Closure closure = new Closure()
                .pack(
                        "builtin",
                        List.of(),
                        List.of("directive:D", "tactic:Below"),
                        edge("directive:D", RelationType.SUGGESTS, "tactic:Below"))
                .pack(
                        "qa",
                        List.of("builtin"),
                        List.of(P),
                        edge(P, RelationType.REQUIRES, "directive:D"),
                        edge(P, RelationType.SUGGESTS, "builtin::directive:D"));

        assertThat(GovernanceWalk.overWritten(closure.merged())
                        .walk(urn(P), 2, GovernanceWalkTest::everything)
                        .entries())
                .as("D is listed held=false by the requires step; an entry with held false has nothing below it")
                .extracting(Entry::urn, Entry::held)
                .containsExactly(tuple(urn("directive:D"), false));
    }

    @Test
    void aHeldEdgeOfOneParentBeatsADanglingEdgeOfTheSameParentWhateverTheirWords() {
        Closure closure = new Closure()
                .pack(
                        "builtin",
                        List.of(),
                        List.of("directive:D", "tactic:Below"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:Below"))
                .pack(
                        "qa",
                        List.of("builtin"),
                        List.of(P),
                        edge(P, RelationType.REQUIRES, "directive:D"),
                        new Relationship(
                                urn(P),
                                Optional.empty(),
                                urn("directive:D"),
                                Optional.of("builtin"),
                                RelationType.REQUIRES,
                                Optional.of("x"),
                                Optional.empty()));

        assertThat(GovernanceWalk.overWritten(closure.merged())
                        .walk(urn(P), 0, GovernanceWalkTest::everything)
                        .entries())
                .as("the dangling edge has no words and would sort first on them; the held edge is kept")
                .extracting(Entry::urn, Entry::held, Entry::when)
                .containsExactly(
                        tuple(urn("directive:D"), true, Optional.of("x")),
                        tuple(urn("tactic:Below"), true, Optional.empty()));
    }

    // ---- hops: the fewest, whatever depth a seed of the step sits at --------------------------------------------

    @Test
    void aTargetADeepAncestorReachesInOneHopTakesTheShorterPathThroughTheProfilesOwnRequires() {
        Closure closure = closure(
                List.of(P, Q, R, "directive:X", "directive:Y"),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(Q, RelationType.SPECIALIZES_FROM, R),
                edge(P, RelationType.REQUIRES, "directive:X"),
                edge("directive:X", RelationType.REQUIRES, "directive:Y"),
                edge(R, RelationType.REQUIRES, "directive:Y"));

        Result result = walk(closure, 0, GovernanceWalkTest::everything);

        assertThat(entry(result, "directive:Y"))
                .as("R sits at hops 2 and would give Y hops 3; X gives it hops 2")
                .extracting(Entry::via, Entry::hops)
                .containsExactly(urn("directive:X"), 2);
    }

    @Test
    void theSuggestsDepthCountsFromTheNearestSeedWhileHopsTakeTheFewestFromTheProfile() {
        Closure closure = closure(
                List.of(P, Q, R, "tactic:X", "tactic:Y", "tactic:Z"),
                edge(P, RelationType.SPECIALIZES_FROM, Q),
                edge(Q, RelationType.SPECIALIZES_FROM, R),
                edge(P, RelationType.SUGGESTS, "tactic:X"),
                edge("tactic:X", RelationType.SUGGESTS, "tactic:Y"),
                edge(R, RelationType.SUGGESTS, "tactic:Y"),
                edge("tactic:Y", RelationType.SUGGESTS, "tactic:Z"));

        Result result = walk(closure, 2, GovernanceWalkTest::everything);

        assertThat(result.entries())
                .as("Z is two suggests hops from R, so depth 2 lists it; Y takes its fewest hops through X")
                .extracting(Entry::urn, Entry::via, Entry::hops)
                .contains(
                        tuple(urn("tactic:X"), urn(P), 1),
                        tuple(urn("tactic:Y"), urn("tactic:X"), 2),
                        tuple(urn("tactic:Z"), urn("tactic:Y"), 3));
    }

    @Test
    void anEntryAnEarlierStepListedIsWalkedThroughWhenALaterStepReachesIt() {
        Closure closure = closure(
                List.of(P, "directive:D", "tactic:T"),
                edge(P, RelationType.REQUIRES, "directive:D"),
                edge(P, RelationType.SUGGESTS, "directive:D"),
                edge("directive:D", RelationType.SUGGESTS, "tactic:T"));

        Result result = walk(closure, 2, GovernanceWalkTest::everything);

        assertThat(result.entries())
                .as("D is listed by requires; the suggests step walks through it to T")
                .extracting(Entry::urn, Entry::step, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("directive:D"), Step.REQUIRES, urn(P), 1),
                        tuple(urn("tactic:T"), Step.SUGGESTS, urn("directive:D"), 2));
    }

    @Test
    void aRefusedSuggestionTakesTheParentWithTheFewestHopsBeforeTheSmallestUrn() {
        String ancestor = "agent_profile:A";
        Closure closure = closure(
                List.of(P, ancestor, "tactic:Out"),
                edge(P, RelationType.SPECIALIZES_FROM, ancestor),
                edge(ancestor, RelationType.SUGGESTS, "tactic:Out"),
                edge(P, RelationType.SUGGESTS, "tactic:Out"));

        Result result = walk(closure, 1, inForce(ancestor));

        assertThat(result.suggestionCandidates())
                .as("A is the smaller URN but sits at hops 1; P is at 0")
                .extracting(
                        ActionContextWalk.SuggestionCandidate::urn,
                        ActionContextWalk.SuggestionCandidate::via,
                        ActionContextWalk.SuggestionCandidate::hops)
                .containsExactly(tuple(urn("tactic:Out"), urn(P), 1));
    }

    @Test
    void twoHeldEdgesOfOneParentToOneTargetAreOrderedByWhenThenByReason() {
        Closure byWhen = closure(
                List.of(P, "directive:X"),
                worded(P, RelationType.REQUIRES, "directive:X", "b", "a"),
                worded(P, RelationType.REQUIRES, "directive:X", "a", "z"));
        Closure byReason = closure(
                List.of(P, "directive:X"),
                worded(P, RelationType.REQUIRES, "directive:X", "a", "z"),
                worded(P, RelationType.REQUIRES, "directive:X", "a", "y"));

        assertThat(entry(walk(byWhen, 0, GovernanceWalkTest::everything), "directive:X"))
                .extracting(Entry::when, Entry::reason)
                .containsExactly(Optional.of("a"), Optional.of("z"));
        assertThat(entry(walk(byReason, 0, GovernanceWalkTest::everything), "directive:X"))
                .extracting(Entry::when, Entry::reason)
                .containsExactly(Optional.of("a"), Optional.of("y"));
    }

    @Test
    void theStepsHaveTheirOwnTokensAndRelationsAndLineageIsSpecializesFrom() {
        assertThat(Step.values())
                .extracting(Step::name, Step::relation)
                .containsExactly(
                        tuple("LINEAGE", RelationType.SPECIALIZES_FROM),
                        tuple("REQUIRES", RelationType.REQUIRES),
                        tuple("SUGGESTS", RelationType.SUGGESTS),
                        tuple("REQUIRES_OF_SUGGESTED", RelationType.REQUIRES),
                        tuple("VOCABULARY", RelationType.VOCABULARY));
    }
}
