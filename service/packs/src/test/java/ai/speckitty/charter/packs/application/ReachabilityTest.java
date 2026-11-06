package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.pack;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import ai.speckitty.charter.packs.application.ReachResult.Direction;
import ai.speckitty.charter.packs.application.ReachResult.Pulled;
import ai.speckitty.charter.packs.application.ReachResult.Via;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The one traversal of the cascade (WP11 T064, T065; ADR-0059 D2): {@link Reachability} runs on {@link GraphWalk} over
 * a hand-built {@link EdgeIndex} and reports what it reached, with every predecessor, what it stopped at, what lies
 * behind a stop and the lineage ancestors it passed through.
 */
class ReachabilityTest {

    private static final Set<RelationType> FIVE = EnumSet.of(
            RelationType.REQUIRES,
            RelationType.SUGGESTS,
            RelationType.REFINES,
            RelationType.SCOPE,
            RelationType.INSTANTIATES);

    private static Set<Urn> urns(String... texts) {
        return Arrays.stream(texts).map(EdgeIndexTest::urn).collect(Collectors.toSet());
    }

    private static Predicate<Urn> denied(String... texts) {
        Set<Urn> deny = urns(texts);
        return deny::contains;
    }

    private static ReachRequest request(Set<Urn> starts, Predicate<Urn> stopAt, boolean lineage) {
        return new ReachRequest(
                starts, FIVE, stopAt, lineage ? Optional.of(ReachRequest.Lineage.specializesFrom()) : Optional.empty());
    }

    private static ReachResult reach(EdgeIndex index, Set<Urn> starts, Predicate<Urn> stopAt) {
        return Reachability.reach(index, request(starts, stopAt, false));
    }

    private static Via via(String predecessor, RelationType relation) {
        return new Via(urn(predecessor), relation);
    }

    @Test
    void aStoppedTargetIsReportedWithItsPredecessorAndNotDescended() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:T", "tactic:X", "tactic:Y"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:X"),
                        edge("tactic:X", RelationType.REQUIRES, "tactic:Y"))
                .index();

        ReachResult result = reach(index, urns("directive:D"), denied("tactic:X"));

        assertThat(result.reached()).containsOnlyKeys(urn("directive:D"), urn("tactic:T"));
        assertThat(result.reached().get(urn("directive:D")))
                .as("a start has no Via")
                .isEmpty();
        assertThat(result.reached().get(urn("tactic:T"))).containsExactly(via("directive:D", RelationType.REQUIRES));
        assertThat(result.stopped()).containsOnlyKeys(urn("tactic:X"));
        assertThat(result.stopped().get(urn("tactic:X"))).containsExactly(via("directive:D", RelationType.REQUIRES));
        assertThat(result.behindStopped()).containsExactly(urn("tactic:Y"));
    }

    @Test
    void aCycleTerminatesAndEachNodeIsReachedOnce() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("tactic:A", "tactic:B"),
                        edge("tactic:A", RelationType.REQUIRES, "tactic:B"),
                        edge("tactic:B", RelationType.REQUIRES, "tactic:A"))
                .index();

        ReachResult result = reach(index, urns("tactic:A"), urn -> false);

        assertThat(result.reached()).containsOnlyKeys(urn("tactic:A"), urn("tactic:B"));
        assertThat(result.reached().get(urn("tactic:A"))).containsExactly(via("tactic:B", RelationType.REQUIRES));
        assertThat(result.stopped()).isEmpty();
        assertThat(result.behindStopped()).isEmpty();
    }

    @Test
    void aRelationOutsideTheRequestIsNotCrossed() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "styleguide:S", "tactic:E"),
                        edge("directive:D", RelationType.SUGGESTS, "styleguide:S"),
                        edge("tactic:E", RelationType.ENHANCES, "directive:D"))
                .index();

        ReachResult result = Reachability.reach(
                index,
                new ReachRequest(
                        urns("directive:D"), EnumSet.of(RelationType.REQUIRES), urn -> false, Optional.empty()));

        assertThat(result.reached()).containsOnlyKeys(urn("directive:D"));
    }

    @Test
    void aNodeReachedAnotherWayIsNotBehindTheStop() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:X", "tactic:Y", "tactic:Z"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:X"),
                        edge("tactic:X", RelationType.REQUIRES, "tactic:Y"),
                        edge("tactic:X", RelationType.REQUIRES, "tactic:Z"),
                        edge("directive:D", RelationType.SCOPE, "tactic:Y"))
                .index();

        ReachResult result = reach(index, urns("directive:D"), denied("tactic:X"));

        assertThat(result.reached()).containsOnlyKeys(urn("directive:D"), urn("tactic:Y"));
        assertThat(result.behindStopped()).containsExactly(urn("tactic:Z"));
    }

    /** P specializes_from Q; Q requires R and suggests S; with {@code directRequirer}, D also requires Q. */
    private static EdgeIndex lineageIndex(boolean directRequirer) {
        List<String> held = List.of("agent_profile:P", "agent_profile:Q", "tactic:R", "styleguide:S", "directive:D");
        List<Relationship> edges = new ArrayList<>(List.of(
                edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "agent_profile:Q"),
                edge("agent_profile:Q", RelationType.REQUIRES, "tactic:R"),
                edge("agent_profile:Q", RelationType.SUGGESTS, "styleguide:S")));
        if (directRequirer) {
            edges.add(edge("directive:D", RelationType.REQUIRES, "agent_profile:Q"));
        }
        return new Closure()
                .pack("alpha", held, edges.toArray(Relationship[]::new))
                .index();
    }

    @Test
    void lineagePassesRequiresAndScopeThroughAnAncestorWithoutPuttingItInForce() {
        ReachResult result =
                Reachability.reach(lineageIndex(false), request(urns("agent_profile:P"), urn -> false, true));

        assertThat(result.reached()).containsOnlyKeys(urn("agent_profile:P"), urn("tactic:R"));
        assertThat(result.reached().get(urn("tactic:R")))
                .containsExactly(via("agent_profile:Q", RelationType.REQUIRES));
        assertThat(result.ancestorsPassed()).containsExactly(urn("agent_profile:Q"));
        assertThat(result.reached()).doesNotContainKeys(urn("agent_profile:Q"), urn("styleguide:S"));
    }

    @Test
    void withoutLineageTheAncestorChainIsNotWalked() {
        ReachResult result =
                Reachability.reach(lineageIndex(false), request(urns("agent_profile:P"), urn -> false, false));

        assertThat(result.reached()).containsOnlyKeys(urn("agent_profile:P"));
        assertThat(result.ancestorsPassed()).isEmpty();
    }

    @Test
    void aDeniedAncestorStopsTheLineageAndWhatItWouldPassIsBehindIt() {
        ReachResult result = Reachability.reach(
                lineageIndex(false), request(urns("agent_profile:P"), denied("agent_profile:Q"), true));

        assertThat(result.reached()).containsOnlyKeys(urn("agent_profile:P"));
        assertThat(result.stopped()).containsOnlyKeys(urn("agent_profile:Q"));
        assertThat(result.stopped().get(urn("agent_profile:Q")))
                .containsExactly(via("agent_profile:P", RelationType.SPECIALIZES_FROM));
        assertThat(result.behindStopped()).containsExactly(urn("tactic:R"));
        assertThat(result.ancestorsPassed()).isEmpty();
    }

    @Test
    void lineageWalksTheWholeAncestorChain() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("agent_profile:P", "agent_profile:Q", "agent_profile:Q2", "tactic:R2"),
                        edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "agent_profile:Q"),
                        edge("agent_profile:Q", RelationType.SPECIALIZES_FROM, "agent_profile:Q2"),
                        edge("agent_profile:Q2", RelationType.SCOPE, "tactic:R2"))
                .index();

        ReachResult result = Reachability.reach(index, request(urns("agent_profile:P"), urn -> false, true));

        assertThat(result.reached()).containsOnlyKeys(urn("agent_profile:P"), urn("tactic:R2"));
        assertThat(result.reached().get(urn("tactic:R2"))).containsExactly(via("agent_profile:Q2", RelationType.SCOPE));
        assertThat(result.ancestorsPassed()).containsExactlyInAnyOrder(urn("agent_profile:Q"), urn("agent_profile:Q2"));
    }

    @Test
    void aStoppedNodeWithTwoPredecessorsRecordsBoth() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "directive:E", "tactic:X"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:X"),
                        edge("directive:E", RelationType.SUGGESTS, "tactic:X"))
                .index();

        ReachResult result = reach(index, urns("directive:D", "directive:E"), denied("tactic:X"));

        assertThat(result.stopped().get(urn("tactic:X")))
                .containsExactlyInAnyOrder(
                        via("directive:D", RelationType.REQUIRES), via("directive:E", RelationType.SUGGESTS));
    }

    @Test
    void aReachedNodeWithTwoPredecessorsRecordsBoth() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:A", "tactic:T"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:A"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"),
                        edge("tactic:A", RelationType.REFINES, "tactic:T"))
                .index();

        ReachResult result = reach(index, urns("directive:D"), urn -> false);

        assertThat(result.reached().get(urn("tactic:T")))
                .containsExactlyInAnyOrder(
                        via("directive:D", RelationType.REQUIRES), via("tactic:A", RelationType.REFINES));
    }

    @Test
    void aPackQualifiedTargetIsReachedInThePackThatHoldsIt() {
        EdgeIndex index = new Closure()
                .pack("beta", List.of("tactic:T", "tactic:U"), edge("tactic:T", RelationType.INSTANTIATES, "tactic:U"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("directive:D"),
                        edge("directive:D", RelationType.REQUIRES, "beta::tactic:T"))
                .index();

        ReachResult result = reach(index, urns("directive:D"), urn -> false);

        assertThat(result.reached()).containsOnlyKeys(urn("directive:D"), urn("tactic:T"), urn("tactic:U"));
        assertThat(result.reached().get(urn("tactic:U"))).containsExactly(via("tactic:T", RelationType.INSTANTIATES));
    }

    /** J4: Q is both P's ancestor and D's direct requires target; only the in-force Q crosses its suggests. */
    @Test
    void anAncestorThatIsAlsoADirectTargetIsInForceAndPassedThrough() {
        ReachResult result = Reachability.reach(
                lineageIndex(true), request(urns("agent_profile:P", "directive:D"), urn -> false, true));

        assertThat(result.reached().get(urn("agent_profile:Q")))
                .containsExactly(via("directive:D", RelationType.REQUIRES));
        assertThat(result.reached()).containsKeys(urn("tactic:R"), urn("styleguide:S"));
        assertThat(result.reached().get(urn("tactic:R")))
                .as("reached both in force from Q and passed through as P's ancestor")
                .containsExactly(via("agent_profile:Q", RelationType.REQUIRES));
        assertThat(result.ancestorsPassed()).containsExactly(urn("agent_profile:Q"));

        ReachResult withoutD =
                Reachability.reach(lineageIndex(true), request(urns("agent_profile:P"), urn -> false, true));
        assertThat(withoutD.reached()).containsOnlyKeys(urn("agent_profile:P"), urn("tactic:R"));
    }

    /** J4: two packs each hold directive:D; walking one does not mark the other visited. */
    @Test
    void visitsAreKeyedByPackAndUrnNotByUrnAlone() {
        EdgeIndex index = new Closure()
                .pack(
                        "beta",
                        List.of("directive:D", "tactic:T2"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T2"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("directive:D", "tactic:T1", "procedure:X"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T1"),
                        edge("procedure:X", RelationType.REQUIRES, "directive:D"),
                        edge("procedure:X", RelationType.REQUIRES, "beta::directive:D"))
                .index();

        ReachResult fromX = reach(index, urns("procedure:X"), urn -> false);
        assertThat(fromX.reached())
                .containsOnlyKeys(urn("procedure:X"), urn("directive:D"), urn("tactic:T1"), urn("tactic:T2"));

        ReachResult fromD = reach(index, urns("directive:D"), urn -> false);
        assertThat(fromD.reached())
                .as("a start held by two packs is walked in each")
                .containsOnlyKeys(urn("directive:D"), urn("tactic:T1"), urn("tactic:T2"));
    }

    // ---- enhances (ADR-0059 D2.2, R1) ------------------------------------------------------------------------------

    @Test
    void thePullRunsBothWays() {
        EdgeIndex index = new Closure()
                .pack("alpha", List.of("tactic:E", "tactic:T"), edge("tactic:E", RelationType.ENHANCES, "tactic:T"))
                .index();

        assertThat(Reachability.enhancesPull(index, urns("tactic:T")))
                .containsOnlyKeys(urn("tactic:E"))
                .containsEntry(urn("tactic:E"), List.of(new Pulled(urn("tactic:T"), Direction.ENHANCER_OF)));
        assertThat(Reachability.enhancesPull(index, urns("tactic:E")))
                .containsOnlyKeys(urn("tactic:T"))
                .containsEntry(urn("tactic:T"), List.of(new Pulled(urn("tactic:E"), Direction.TARGET_OF)));
    }

    @Test
    void thePullIsTransitiveOverAChainAndTerminatesOnACycle() {
        EdgeIndex chain = new Closure()
                .pack(
                        "alpha",
                        List.of("tactic:E1", "tactic:E2", "tactic:T"),
                        edge("tactic:E1", RelationType.ENHANCES, "tactic:E2"),
                        edge("tactic:E2", RelationType.ENHANCES, "tactic:T"))
                .index();
        assertThat(Reachability.enhancesPull(chain, urns("tactic:T")))
                .containsOnlyKeys(urn("tactic:E1"), urn("tactic:E2"));

        EdgeIndex cycle = new Closure()
                .pack(
                        "alpha",
                        List.of("tactic:A", "tactic:B"),
                        edge("tactic:A", RelationType.ENHANCES, "tactic:B"),
                        edge("tactic:B", RelationType.ENHANCES, "tactic:A"))
                .index();
        assertThat(Reachability.enhancesPull(cycle, urns("tactic:A"))).containsOnlyKeys(urn("tactic:B"));
    }

    @Test
    void thePullNeverCrossesACascadeRelation() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("tactic:E", "tactic:T", "tactic:Z"),
                        edge("tactic:E", RelationType.ENHANCES, "tactic:T"),
                        edge("tactic:E", RelationType.REQUIRES, "tactic:Z"))
                .index();

        assertThat(Reachability.enhancesPull(index, urns("tactic:T"))).containsOnlyKeys(urn("tactic:E"));
    }

    @Test
    void enhancersOfIsTheOneHopReverseLookupWithTheEnhancersPack() {
        EdgeIndex index = new Closure()
                .pack("beta", List.of("tactic:T"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("tactic:E1", "tactic:E2"),
                        edge("tactic:E1", RelationType.ENHANCES, "tactic:E2"),
                        edge("tactic:E2", RelationType.ENHANCES, "beta::tactic:T"))
                .index();

        assertThat(Reachability.enhancersOf(index, urns("tactic:T")))
                .containsOnlyKeys(urn("tactic:T"))
                .containsEntry(urn("tactic:T"), List.of(new EdgeIndex.Place(pack("alpha"), urn("tactic:E2"))));
    }
}
