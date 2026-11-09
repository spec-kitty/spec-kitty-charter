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

    @Test
    void aSuggestsEdgeKeepsItsWhenAndItsReasonAndOtherStepsCarryNone() {
        Relationship suggests = new Relationship(
                urn(P),
                Optional.empty(),
                urn("tactic:S"),
                Optional.empty(),
                RelationType.SUGGESTS,
                Optional.of("the task touches the schema"),
                Optional.of("migrations need it"));
        Closure closure =
                closure(List.of(P, "tactic:S", "directive:D"), suggests, edge(P, RelationType.REQUIRES, "directive:D"));

        Result result = walk(closure, 1, GovernanceWalkTest::everything);

        assertThat(entry(result, "tactic:S"))
                .extracting(Entry::when, Entry::reason)
                .containsExactly(Optional.of("the task touches the schema"), Optional.of("migrations need it"));
        assertThat(entry(result, "directive:D"))
                .extracting(Entry::when, Entry::reason)
                .containsExactly(Optional.empty(), Optional.empty());
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
    void aParentNoPackOfTheClosureHoldsIsListedAsNotHeldAndNothingIsListedBelowIt() {
        Closure closure = closure(
                List.of(P, "directive:Own"),
                edge(P, RelationType.SPECIALIZES_FROM, "agent_profile:Gone"),
                edge(P, RelationType.REQUIRES, "directive:Own"),
                edge("agent_profile:Gone", RelationType.REQUIRES, "directive:Below"));

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
