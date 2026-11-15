/**
 * Node kinds as types: one final leaf class per {@link ai.speckitty.charter.kernel.domain.NodeKind} and the sealed
 * groups of the endpoint table, so a relationship whose kinds its relation does not allow does not compile
 * (ADR-0064 decision 13).
 */
@NullMarked
package ai.speckitty.charter.kernel.domain.kinds;

import org.jspecify.annotations.NullMarked;
