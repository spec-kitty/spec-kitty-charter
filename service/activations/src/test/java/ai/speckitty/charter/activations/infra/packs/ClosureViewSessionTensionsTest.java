package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.TensionSnapshot;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.GraphProjection;
import ai.speckitty.charter.packs.application.TensionsInForce;
import ai.speckitty.charter.packs.application.TensionsInForce.TensionInForce;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The session's copy of the packs slice's tensions (ADR-0045 decisions 9 and 10): the tensions in force and the graph's
 * tension edges become values with their words and reconcilers, in the order the packs slice gave them; a structural
 * edge carries none.
 */
class ClosureViewSessionTensionsTest {

    private static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    private static NodeRef node(String text) {
        return NodeRef.of(urn(text)).orElseThrow();
    }

    private static TensionInForce tension(String source, String target, String... reconcilers) {
        TreeSet<Urn> by = new TreeSet<>();
        for (String reconciler : reconcilers) {
            by.add(urn(reconciler));
        }
        return new TensionInForce(urn(source), urn(target), Optional.of("when"), Optional.of("why"), by);
    }

    @Test
    void theTensionsInForceBecomeSnapshotsInTheOrderGivenWithTheirWordsAndReconcilers() {
        TensionsInForce tensions = new TensionsInForce(List.of(
                tension("directive:C", "directive:D"), tension("directive:A", "directive:B", "tactic:S", "tactic:R")));

        List<TensionSnapshot> snapshots = ClosureViewSession.tensionsOf(tensions);

        assertThat(snapshots)
                .containsExactly(
                        new TensionSnapshot(
                                node("directive:A"),
                                node("directive:B"),
                                Optional.of("when"),
                                Optional.of("why"),
                                List.of(node("tactic:R"), node("tactic:S"))),
                        new TensionSnapshot(
                                node("directive:C"),
                                node("directive:D"),
                                Optional.of("when"),
                                Optional.of("why"),
                                List.of()));
        assertThat(ClosureViewSession.tensionsOf(TensionsInForce.NONE)).isEmpty();
    }

    @Test
    void aTensionEdgeOfTheProjectionKeepsItsWordsAndAStructuralEdgeHasNone() {
        GraphProjection projection = new GraphProjection(
                List.of(urn("directive:A"), urn("directive:B"), urn("tactic:R")),
                List.of(
                        GraphProjection.Edge.of(tension("directive:A", "directive:B", "tactic:R")),
                        new GraphProjection.Edge(urn("directive:B"), RelationType.REQUIRES, urn("directive:A"))),
                List.of());

        GraphSnapshot graph = ClosureViewSession.graphOf(projection);

        assertThat(graph.edges())
                .containsExactly(
                        new GraphSnapshot.Edge(
                                node("directive:A"),
                                RelationType.TENSION,
                                node("directive:B"),
                                Optional.of("when"),
                                Optional.of("why"),
                                List.of(node("tactic:R"))),
                        new GraphSnapshot.Edge(node("directive:B"), RelationType.REQUIRES, node("directive:A")));
    }
}
