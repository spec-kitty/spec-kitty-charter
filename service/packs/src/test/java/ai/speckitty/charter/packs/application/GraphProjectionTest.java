package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import ai.speckitty.charter.packs.application.GraphProjection.Edge;
import ai.speckitty.charter.packs.domain.LayerOverride;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The graph projection over the merged index (FR-018, rule E15): the nodes in force, each held step whose mission type
 * is in force, the merged edges whose endpoints are both nodes, and the layer overrides of those nodes, every list in an
 * order of its own.
 */
class GraphProjectionTest {

    private static Predicate<Urn> inForce(String... urns) {
        Set<Urn> set = Set.copyOf(Stream.of(urns).map(EdgeIndexTest::urn).toList());
        return set::contains;
    }

    private static GraphProjection project(Closure closure, Predicate<Urn> inForce) {
        return GraphProjection.of(closure.merged(), closure.mergedClosure(), inForce);
    }

    private static Edge link(String source, RelationType relation, String target) {
        return new Edge(urn(source), relation, urn(target));
    }

    @Test
    void theNodesAreTheArtifactsInForceInUrnOrderAndNoOther() {
        Closure closure = new Closure().pack("alpha", List.of("tactic:T", "directive:D", "tactic:A", "tactic:Z"));

        GraphProjection graph = project(closure, inForce("tactic:T", "directive:D", "tactic:A"));

        assertThat(graph.nodes()).containsExactly(urn("directive:D"), urn("tactic:A"), urn("tactic:T"));
    }

    @Test
    void anEdgeIsDroppedWithEitherEndpoint() {
        Closure closure = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:T", "tactic:U"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"),
                        edge("directive:D", RelationType.SUGGESTS, "tactic:U"),
                        edge("tactic:U", RelationType.REQUIRES, "directive:D"));

        GraphProjection graph = project(closure, inForce("directive:D", "tactic:T"));

        assertThat(graph.edges()).containsExactly(link("directive:D", RelationType.REQUIRES, "tactic:T"));
    }

    @Test
    void anEdgeKeepsBothEndpointsAmongTheNodesAfterTheLayerMerge() {
        Closure closure = new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("directive:D", "tactic:T"),
                        edge("tactic:T", RelationType.REQUIRES, "directive:D"))
                .pack("project", PackLevel.PROJECT, List.of("directive:D"));

        GraphProjection graph = project(closure, inForce("directive:D", "tactic:T"));

        assertThat(graph.edges()).containsExactly(link("tactic:T", RelationType.REQUIRES, "directive:D"));
    }

    @Test
    void aHeldStepOfAMissionTypeInForceIsANodeAndItsEdgesAreKept() {
        Closure closure = new Closure()
                .pack(
                        "alpha",
                        List.of("mission_type:M", "action:M/a", "action:M/b", "tactic:T"),
                        edge("action:M/a", RelationType.SCOPE, "tactic:T"));

        GraphProjection graph = project(closure, inForce("mission_type:M", "tactic:T"));

        assertThat(graph.nodes())
                .containsExactly(urn("action:M/a"), urn("action:M/b"), urn("mission_type:M"), urn("tactic:T"));
        assertThat(graph.edges()).containsExactly(link("action:M/a", RelationType.SCOPE, "tactic:T"));
    }

    @Test
    void aHeldStepOfAMissionTypeNotInForceIsNoNode() {
        Closure closure = new Closure()
                .pack(
                        "alpha",
                        List.of("mission_type:M", "action:M/a", "tactic:T"),
                        edge("action:M/a", RelationType.SCOPE, "tactic:T"));

        GraphProjection graph = project(closure, inForce("tactic:T"));

        assertThat(graph.nodes()).containsExactly(urn("tactic:T"));
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void anActionDeclaredWhereNoPackHoldsItsStepIsNoNode() {
        Closure closure = new Closure()
                .pack("alpha", List.of("mission_type:M", "action:M/a"))
                .pack("beta", PackLevel.PROJECT, List.of("action:M/stray"));

        GraphProjection graph = project(closure, inForce("mission_type:M"));

        assertThat(graph.nodes()).containsExactly(urn("action:M/a"), urn("mission_type:M"));
    }

    @Test
    void aStepAnotherLevelOverridesIsANodeAndItsLayerOverrideIsListed() {
        Closure closure = new Closure()
                .pack("org", PackLevel.ORG, List.of("mission_type:M", "action:M/a"))
                .pack("project", PackLevel.PROJECT, List.of("action:M/a"));

        GraphProjection graph = project(closure, inForce("mission_type:M"));

        assertThat(graph.nodes()).contains(urn("action:M/a"));
        assertThat(graph.layerOverrides()).extracting(LayerOverride::urn).containsExactly(urn("action:M/a"));
    }

    @Test
    void theLayerOverridesAreThoseOfTheNodesInArtifactThenLosingLevelThenLosingPackOrder() {
        Closure closure = new Closure()
                .pack("beta", PackLevel.BUILT_IN, List.of("tactic:A", "directive:D", "tactic:Out"))
                .pack("org", PackLevel.ORG, List.of("directive:D", "tactic:A", "tactic:Out"))
                .pack("project", PackLevel.PROJECT, List.of("directive:D", "tactic:A", "tactic:Out"));

        GraphProjection graph = project(closure, inForce("directive:D", "tactic:A"));

        assertThat(graph.layerOverrides())
                .extracting(
                        LayerOverride::urn,
                        replaced -> replaced.loser().level(),
                        replaced -> replaced.loser().packId())
                .containsExactly(
                        tuple(urn("directive:D"), PackLevel.BUILT_IN, new PackIdentifier("beta")),
                        tuple(urn("directive:D"), PackLevel.ORG, new PackIdentifier("org")),
                        tuple(urn("tactic:A"), PackLevel.BUILT_IN, new PackIdentifier("beta")),
                        tuple(urn("tactic:A"), PackLevel.ORG, new PackIdentifier("org")));
    }

    @Test
    void theEdgesAreSortedBySourceThenRelationThenTargetAndEachOnceWhateverTheOrderTheyWereWritten() {
        List<Relationship> written = List.of(
                edge("tactic:T", RelationType.SUGGESTS, "directive:D"),
                edge("directive:D", RelationType.SUGGESTS, "tactic:T"),
                edge("directive:D", RelationType.REQUIRES, "tactic:U"),
                edge("directive:D", RelationType.REQUIRES, "tactic:T"),
                edge("directive:D", RelationType.REQUIRES, "tactic:T"));
        List<String> held = List.of("directive:D", "tactic:T", "tactic:U");
        Predicate<Urn> all = inForce("directive:D", "tactic:T", "tactic:U");

        GraphProjection forward = project(new Closure().pack("alpha", held, written.toArray(Relationship[]::new)), all);
        GraphProjection backward = project(
                new Closure().pack("alpha", held.reversed(), written.reversed().toArray(Relationship[]::new)), all);

        assertThat(forward.edges())
                .containsExactly(
                        link("directive:D", RelationType.REQUIRES, "tactic:T"),
                        link("directive:D", RelationType.REQUIRES, "tactic:U"),
                        link("directive:D", RelationType.SUGGESTS, "tactic:T"),
                        link("tactic:T", RelationType.SUGGESTS, "directive:D"));
        assertThat(backward).isEqualTo(forward);
    }
}
