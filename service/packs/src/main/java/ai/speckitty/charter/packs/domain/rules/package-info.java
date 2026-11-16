/**
 * The pack rules' plain functions: binding a graph document ({@link GraphBinding}), the pack-relative path faults
 * ({@link Paths}), the descriptor's well-formedness ({@link DescriptorChecks}), the detail vocabulary of a file that
 * could not be read ({@link Wording}) and the order of the findings ({@link Precedence}). Each is pure and
 * storage-agnostic: it names no {@code Path}, no YAML and no storage type. Plain Java only; the dependency rule is
 * pinned by {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.packs.domain.rules;

import org.jspecify.annotations.NullMarked;
