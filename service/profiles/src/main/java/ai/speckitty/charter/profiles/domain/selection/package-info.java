/**
 * Profile selection: which in-force agent profile fills a role (ADR-0069 decision 4, as ADR-0078 rules it). A pure
 * domain service over values the caller supplies: the candidates in force under an activation, the pin in force for
 * the role and the fallback in force. Conflicts between pins or fallbacks are judged by the caller before selection.
 * Plain Java only; the dependency rule is pinned by {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.profiles.domain.selection;

import org.jspecify.annotations.NullMarked;
