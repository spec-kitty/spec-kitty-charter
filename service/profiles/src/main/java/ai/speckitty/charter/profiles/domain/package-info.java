/**
 * The agent-profile domain: an agent's identity, specialization, collaboration and governance references, as a pack
 * authors them. Lineage is a {@code specializes_from} edge of the pack's graph, not a field here, and selecting or
 * resolving a profile is not this slice's job yet (ADR-0032). Plain Java only; the dependency rule is pinned by
 * {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.profiles.domain;

import org.jspecify.annotations.NullMarked;
