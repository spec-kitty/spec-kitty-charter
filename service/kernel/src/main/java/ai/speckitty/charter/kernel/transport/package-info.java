/**
 * The framework-free transport vocabulary every slice's inbound adapter shares: the reserved-operation marker, the
 * HTTP precondition failure, the entity-tag comparisons and the request-body limit. Nothing here imports Spring or
 * Jackson; the dependency rule is pinned by {@code HexagonalArchitectureTest} ({@code KERNEL_IS_PURE}).
 */
@NullMarked
package ai.speckitty.charter.kernel.transport;

import org.jspecify.annotations.NullMarked;
