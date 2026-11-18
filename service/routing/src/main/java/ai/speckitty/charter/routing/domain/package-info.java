/**
 * The routing domain (ADR-0080): the model-task routing catalog as a pack authors it, and the ranker that turns a
 * catalog, a task type and an optional model tier into advice. The advice never decides for the client: ties are all
 * answered, and a tier flags a model, never hides it. Plain Java only; the dependency rule is pinned by
 * {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.routing.domain;

import org.jspecify.annotations.NullMarked;
