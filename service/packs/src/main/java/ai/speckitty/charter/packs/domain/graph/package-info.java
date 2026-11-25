/**
 * The pack graph as the packs domain walks it (ADR-0082 decision 2, #638): its nodes ({@link PackGraphNode}: a pack, an
 * artifact, an edge), the kinds of edge ({@link EdgeKind}) and the {@link Scope} a caller narrows a walk by. The driver
 * is {@link ai.speckitty.charter.packs.domain.PackGraph}; a scope only selects, and never names the edges a walk
 * follows. Plain Java only; the dependency rule is pinned by {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.packs.domain.graph;

import org.jspecify.annotations.NullMarked;
