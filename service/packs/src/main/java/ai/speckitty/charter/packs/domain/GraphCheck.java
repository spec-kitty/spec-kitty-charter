package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.packs.domain.graph.PackGraphNode;
import ai.speckitty.charter.packs.domain.graph.Scope;
import java.util.List;
import java.util.Objects;

/**
 * A check the rule factory hands out (ADR-0082 decision 5): the walk mode the domain chose for it and its phases. The
 * caller passes the graph and the scope; it never picks the edges the walk follows.
 *
 * @param walk the walk mode: which elements the driver emits beyond the packs
 * @param phases the phases the walker runs
 */
public record GraphCheck(PackGraph.Walk walk, List<ValidationWalker.Phase<PackGraphNode>> phases) {

    /**
     * Checks and copies the fields.
     *
     * @param walk the walk mode
     * @param phases the phases; at least one
     */
    public GraphCheck {
        Objects.requireNonNull(walk, "walk");
        phases = List.copyOf(phases);
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("a check has at least one phase");
        }
    }

    /**
     * Runs the check over a scope of a graph: one walk of the graph per phase.
     *
     * @param graph the pack graph
     * @param scope the roots and the selection
     * @return the ledger
     */
    public Ledger run(PackGraph graph, Scope scope) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(scope, "scope");
        return ValidationWalker.of(phases).run(visitor -> graph.walk(scope, walk, visitor));
    }
}
