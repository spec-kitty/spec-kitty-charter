/**
 * The mission-type domain (ADR-0044): a mission type with its steps in order, the steps invoked on demand and the
 * artifacts it expects, as a pack authors them in one file; the guidance it scopes is graph edges (ADR-0064). A step
 * is an entity of its type, not an artifact of its own. The domain holds no file paths: where a type and its step
 * documents are stored is the pack store's concern (ADR-0037). Plain Java only; the dependency rule is pinned by
 * {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.missions.domain;

import org.jspecify.annotations.NullMarked;
