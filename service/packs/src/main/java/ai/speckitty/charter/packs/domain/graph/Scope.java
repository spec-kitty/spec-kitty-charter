package ai.speckitty.charter.packs.domain.graph;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * What a caller narrows a walk by: where it starts and which elements it selects. It is only a selection filter: the
 * walk visits the composition closure of the roots, and the selection decides which elements the visitor is handed. It
 * never names the edges a walk follows; the domain decides those. The predicate is pure: it reads values already
 * loaded.
 *
 * @param roots the configured packs the walk starts at, in order
 * @param selection which elements the visitor is handed
 */
public record Scope(List<PackIdentifier> roots, Predicate<? super PackGraphNode> selection) {

    /**
     * Checks and copies the fields.
     *
     * @param roots the roots; at least one
     * @param selection the selection
     */
    public Scope {
        roots = List.copyOf(roots);
        Objects.requireNonNull(selection, "selection");
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("a scope has at least one root");
        }
    }

    /**
     * A scope that selects every element of the roots' closure.
     *
     * @param roots the roots
     * @return the scope
     */
    public static Scope of(List<PackIdentifier> roots) {
        return new Scope(roots, element -> true);
    }

    /**
     * A scope that selects every element of a composed closure, every pack of it a root, in the closure's order.
     *
     * @param closure the composed closure; at least one pack
     * @return the scope
     */
    public static Scope allOf(List<PackUnderResolution> closure) {
        return of(closure.stream().map(member -> member.configured().id()).toList());
    }
}
