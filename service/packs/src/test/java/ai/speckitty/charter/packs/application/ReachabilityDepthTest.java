package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The bound on {@code suggests} hops of {@link Reachability#reach} (IC-21, FR-043; issue #408 ruling 1): the walk is
 * layered by {@code suggests} hops, a 0-1 breadth-first search. Layer k is the full closure of the layer-k frontier over
 * every other relation, the lineage pass-through and a mission type's steps; the {@code suggests} targets not yet
 * reached form layer k+1. Each place is expanded once, in its lowest layer, a pass-through or step place inherits the
 * hop count of the place that produced it, and the bound is checked before a place is entered, in the behind-stop walk
 * too. {@link ReachResult#hops()} says each reached place's minimum hops (the read side reuses it, ruling 3), and
 * {@link ReachResult#beyondBound()} the {@code suggests} targets one hop past the bound.
 */
class ReachabilityDepthTest {

    private static final Set<RelationType> CASCADE = EnumSet.of(
            RelationType.REQUIRES,
            RelationType.SUGGESTS,
            RelationType.REFINES,
            RelationType.SCOPE,
            RelationType.INSTANTIATES,
            RelationType.ENHANCES);

    private static Set<Urn> urns(String... texts) {
        return Arrays.stream(texts).map(EdgeIndexTest::urn).collect(Collectors.toSet());
    }

    private static Predicate<Urn> denied(String... texts) {
        Set<Urn> deny = urns(texts);
        return deny::contains;
    }

    private static ReachRequest request(
            String start, Predicate<Urn> stopAt, boolean lineage, OptionalInt suggestsBound) {
        return new ReachRequest(
                urns(start),
                CASCADE,
                stopAt,
                lineage ? Optional.of(ReachRequest.Lineage.specializesFrom()) : Optional.empty(),
                suggestsBound);
    }

    private static ReachResult reach(EdgeIndex index, String start, int depth) {
        return Reachability.reach(index, request(start, urn -> false, true, OptionalInt.of(depth)));
    }

    private static EdgeIndex index(List<String> held, Relationship... edges) {
        return new Closure().pack("alpha", held, edges).index();
    }

    private static Set<Urn> reachedBy(ReachResult result) {
        return result.reached().keySet();
    }

    /** T requires B and suggests A; A requires Ar and suggests C; C requires Cr. */
    private static EdgeIndex chain() {
        return index(
                List.of("directive:T", "directive:B", "directive:A", "directive:Ar", "directive:C", "directive:Cr"),
                edge("directive:T", RelationType.REQUIRES, "directive:B"),
                edge("directive:T", RelationType.SUGGESTS, "directive:A"),
                edge("directive:A", RelationType.REQUIRES, "directive:Ar"),
                edge("directive:A", RelationType.SUGGESTS, "directive:C"),
                edge("directive:C", RelationType.REQUIRES, "directive:Cr"));
    }

    @Test
    void aDepthOfZeroCrossesNoSuggestsEdgeAndNamesTheNextFrontier() {
        ReachResult result = reach(chain(), "directive:T", 0);

        assertThat(reachedBy(result)).containsExactlyInAnyOrder(urn("directive:T"), urn("directive:B"));
        assertThat(result.beyondBound()).containsExactly(urn("directive:A"));
        assertThat(result.hops()).containsEntry(urn("directive:T"), 0).containsEntry(urn("directive:B"), 0);
    }

    @Test
    void aDepthOfOneAddsTheFirstSuggestsTargetAndItsHardClosure() {
        ReachResult result = reach(chain(), "directive:T", 1);

        assertThat(reachedBy(result))
                .containsExactlyInAnyOrder(
                        urn("directive:T"), urn("directive:B"), urn("directive:A"), urn("directive:Ar"));
        assertThat(result.beyondBound()).containsExactly(urn("directive:C"));
        assertThat(result.hops()).containsEntry(urn("directive:A"), 1).containsEntry(urn("directive:Ar"), 1);
    }

    @Test
    void aBoundPastTheChainReachesItAllAndLeavesNothingBeyond() {
        ReachResult result = reach(chain(), "directive:T", 2);

        assertThat(reachedBy(result)).hasSize(6);
        assertThat(result.beyondBound()).isEmpty();
        assertThat(result.hops()).containsEntry(urn("directive:C"), 2).containsEntry(urn("directive:Cr"), 2);
    }

    @Test
    void noBoundIsTheSameWalkAsTheDepthPastTheChain() {
        ReachResult unlimited = Reachability.reach(
                chain(), new ReachRequest(urns("directive:T"), CASCADE, urn -> false, Optional.empty()));

        assertThat(reachedBy(unlimited)).isEqualTo(reachedBy(reach(chain(), "directive:T", 2)));
        assertThat(unlimited.beyondBound()).isEmpty();
        assertThat(unlimited.hops()).containsEntry(urn("directive:Cr"), 2);
    }

    /** T suggests A and requires B; B requires A; A suggests C. A sorts before B, and the suggests path is first. */
    private static EdgeIndex diamond() {
        return index(
                List.of("directive:T", "directive:A", "directive:B", "directive:C"),
                edge("directive:T", RelationType.SUGGESTS, "directive:A"),
                edge("directive:T", RelationType.REQUIRES, "directive:B"),
                edge("directive:B", RelationType.REQUIRES, "directive:A"),
                edge("directive:A", RelationType.SUGGESTS, "directive:C"));
    }

    @Test
    void anArtifactReachedOverRequiresIsExpandedAtItsLowestLayerWhateverTheVisitOrder() {
        ReachResult result = reach(diamond(), "directive:T", 1);

        assertThat(reachedBy(result))
                .containsExactlyInAnyOrder(
                        urn("directive:T"), urn("directive:A"), urn("directive:B"), urn("directive:C"));
        assertThat(result.hops())
                .containsEntry(urn("directive:A"), 0)
                .containsEntry(urn("directive:B"), 0)
                .containsEntry(urn("directive:C"), 1);
        assertThat(result.beyondBound()).isEmpty();
    }

    @Test
    void theDiamondAtDepthZeroSelectsTheHardTargetAndCutsOnlyWhatLiesPastIt() {
        ReachResult result = reach(diamond(), "directive:T", 0);

        assertThat(reachedBy(result))
                .containsExactlyInAnyOrder(urn("directive:T"), urn("directive:A"), urn("directive:B"));
        assertThat(result.beyondBound()).containsExactly(urn("directive:C"));
    }

    @Test
    void aTargetReachedOverRequiresAndSuggestsKeepsEveryWayItWasReachedByWithinTheBound() {
        ReachResult atOne = reach(diamond(), "directive:T", 1);
        ReachResult atZero = reach(diamond(), "directive:T", 0);

        assertThat(atOne.reached().get(urn("directive:A")))
                .containsExactlyInAnyOrder(
                        new ReachResult.Via(urn("directive:T"), RelationType.SUGGESTS),
                        new ReachResult.Via(urn("directive:B"), RelationType.REQUIRES));
        assertThat(atZero.reached().get(urn("directive:A")))
                .as("the suggests edge lies past the bound, so it leaves no Via")
                .containsExactly(new ReachResult.Via(urn("directive:B"), RelationType.REQUIRES));
    }

    @Test
    void enhancesAfterASuggestsHopIsWalkedAtNoCost() {
        EdgeIndex index = index(
                List.of("directive:T", "directive:A", "directive:E", "directive:F"),
                edge("directive:T", RelationType.SUGGESTS, "directive:A"),
                edge("directive:A", RelationType.ENHANCES, "directive:E"),
                edge("directive:E", RelationType.SUGGESTS, "directive:F"));

        ReachResult result = reach(index, "directive:T", 1);

        assertThat(reachedBy(result))
                .containsExactlyInAnyOrder(urn("directive:T"), urn("directive:A"), urn("directive:E"));
        assertThat(result.hops()).containsEntry(urn("directive:E"), 1);
        assertThat(result.beyondBound()).containsExactly(urn("directive:F"));
    }

    /** T suggests A; A specializes from P; P requires Q and suggests X; Q suggests R. */
    private static EdgeIndex lineage() {
        return index(
                List.of(
                        "agent_profile:T",
                        "agent_profile:A",
                        "agent_profile:P",
                        "directive:Q",
                        "directive:R",
                        "directive:X"),
                edge("agent_profile:T", RelationType.SUGGESTS, "agent_profile:A"),
                edge("agent_profile:A", RelationType.SPECIALIZES_FROM, "agent_profile:P"),
                edge("agent_profile:P", RelationType.REQUIRES, "directive:Q"),
                edge("agent_profile:P", RelationType.SUGGESTS, "directive:X"),
                edge("directive:Q", RelationType.SUGGESTS, "directive:R"));
    }

    @Test
    void aPassThroughPlaceInheritsTheHopCountOfTheDescendantItCameFrom() {
        ReachResult result = reach(lineage(), "agent_profile:T", 1);

        assertThat(reachedBy(result))
                .containsExactlyInAnyOrder(urn("agent_profile:T"), urn("agent_profile:A"), urn("directive:Q"));
        assertThat(result.hops()).containsEntry(urn("agent_profile:A"), 1).containsEntry(urn("directive:Q"), 1);
        assertThat(result.ancestorsPassed()).containsExactly(urn("agent_profile:P"));
    }

    @Test
    void aSuggestsEdgeBehindALineagePassThroughIsCountedFromTheDescendantsHops() {
        ReachResult atOne = reach(lineage(), "agent_profile:T", 1);
        ReachResult atTwo = reach(lineage(), "agent_profile:T", 2);

        assertThat(reachedBy(atOne)).doesNotContain(urn("directive:R"));
        assertThat(atOne.beyondBound()).containsExactly(urn("directive:R"));
        assertThat(reachedBy(atTwo)).contains(urn("directive:R"));
        assertThat(atTwo.hops()).containsEntry(urn("directive:R"), 2);
    }

    @Test
    void theLineageNeverCarriesSuggestsWhateverTheBound() {
        ReachResult result = Reachability.reach(
                lineage(),
                new ReachRequest(
                        urns("agent_profile:T"),
                        CASCADE,
                        urn -> false,
                        Optional.of(ReachRequest.Lineage.specializesFrom())));

        assertThat(reachedBy(result)).doesNotContain(urn("directive:X"));
    }

    /** M holds the steps plan and review; plan requires E and suggests F; T suggests M. */
    private static EdgeIndex missionType() {
        return index(
                List.of(
                        "directive:T",
                        "mission_type:m",
                        "action:m/plan",
                        "action:m/review",
                        "directive:E",
                        "directive:F"),
                edge("directive:T", RelationType.SUGGESTS, "mission_type:m"),
                edge("action:m/plan", RelationType.REQUIRES, "directive:E"),
                edge("action:m/plan", RelationType.SUGGESTS, "directive:F"));
    }

    @Test
    void aStepPlaceInheritsTheHopCountOfItsMissionTypeAndItsSuggestsEdgeIsCountedFromThere() {
        ReachResult result = reach(missionType(), "directive:T", 1);

        assertThat(reachedBy(result))
                .containsExactlyInAnyOrder(
                        urn("directive:T"),
                        urn("mission_type:m"),
                        urn("action:m/plan"),
                        urn("action:m/review"),
                        urn("directive:E"));
        assertThat(result.hops())
                .containsEntry(urn("mission_type:m"), 1)
                .containsEntry(urn("action:m/plan"), 1)
                .containsEntry(urn("directive:E"), 1);
        assertThat(result.beyondBound()).containsExactly(urn("directive:F"));
    }

    @Test
    void aMissionTypeBehindASuggestsEdgeIsNotEnteredPastTheBoundAndSoNeitherAreItsSteps() {
        ReachResult result = reach(missionType(), "directive:T", 0);

        assertThat(reachedBy(result)).containsExactly(urn("directive:T"));
        assertThat(result.beyondBound()).containsExactly(urn("mission_type:m"));
    }

    @Test
    void aDeniedArtifactPastTheBoundIsNeitherStoppedAtNorGivenAWay() {
        ReachResult atZero =
                Reachability.reach(chain(), request("directive:T", denied("directive:A"), true, OptionalInt.of(0)));
        ReachResult atOne =
                Reachability.reach(chain(), request("directive:T", denied("directive:A"), true, OptionalInt.of(1)));

        assertThat(atZero.stopped()).isEmpty();
        assertThat(atZero.beyondBound()).containsExactly(urn("directive:A"));
        assertThat(atOne.stopped()).containsOnlyKeys(urn("directive:A"));
        assertThat(atOne.beyondBound()).isEmpty();
    }

    /** T requires S and suggests W; S requires Y and suggests Z; W requires V. S is denied. */
    private static EdgeIndex behindStop() {
        return index(
                List.of("directive:T", "directive:S", "directive:Y", "directive:Z", "directive:W", "directive:V"),
                edge("directive:T", RelationType.REQUIRES, "directive:S"),
                edge("directive:T", RelationType.SUGGESTS, "directive:W"),
                edge("directive:S", RelationType.REQUIRES, "directive:Y"),
                edge("directive:S", RelationType.SUGGESTS, "directive:Z"),
                edge("directive:W", RelationType.REQUIRES, "directive:V"));
    }

    @Test
    void nothingPastTheBoundIsReportedAsBehindAStop() {
        ReachResult atZero = Reachability.reach(
                behindStop(), request("directive:T", denied("directive:S"), true, OptionalInt.of(0)));
        ReachResult atOne = Reachability.reach(
                behindStop(), request("directive:T", denied("directive:S"), true, OptionalInt.of(1)));

        assertThat(atZero.stopped()).containsOnlyKeys(urn("directive:S"));
        assertThat(atZero.behindStopped()).containsExactly(urn("directive:Y"));
        assertThat(atOne.behindStopped()).containsExactlyInAnyOrder(urn("directive:Y"), urn("directive:Z"));
        assertThat(reachedBy(atOne)).contains(urn("directive:W"), urn("directive:V"));
    }

    @Test
    void aPlaceFirstSeenOverSuggestsIsExpandedAgainstItsLowerLayerWhenAHardPathReachesItLater() {
        EdgeIndex index = index(
                List.of("directive:T", "directive:A", "directive:B", "directive:C", "directive:D"),
                edge("directive:T", RelationType.SUGGESTS, "directive:A"),
                edge("directive:T", RelationType.REQUIRES, "directive:B"),
                edge("directive:B", RelationType.REQUIRES, "directive:C"),
                edge("directive:C", RelationType.REQUIRES, "directive:A"),
                edge("directive:A", RelationType.SUGGESTS, "directive:D"));

        ReachResult result = reach(index, "directive:T", 1);

        assertThat(result.hops()).containsEntry(urn("directive:A"), 0).containsEntry(urn("directive:D"), 1);
    }

    @Test
    void aCycleThroughSuggestsTerminatesAtAnyBound() {
        EdgeIndex index = index(
                List.of("directive:A", "directive:B"),
                edge("directive:A", RelationType.SUGGESTS, "directive:B"),
                edge("directive:B", RelationType.SUGGESTS, "directive:A"));

        ReachResult result = reach(index, "directive:A", 5);

        assertThat(reachedBy(result)).containsExactlyInAnyOrder(urn("directive:A"), urn("directive:B"));
        assertThat(result.hops()).containsEntry(urn("directive:A"), 0).containsEntry(urn("directive:B"), 1);
    }

    @Test
    void aNegativeBoundIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> request("directive:T", urn -> false, true, OptionalInt.of(-1)))
                .withMessageContaining("-1");
    }
}
