/**
 * The pack rules' plain functions: binding a graph document ({@link GraphBinding}), the pack-relative path faults and
 * where a finding comes from ({@link Paths}), the descriptor's well-formedness ({@link DescriptorChecks}), how a finding
 * is stated ({@link Findings}) and the order of the findings ({@link Precedence}). Each is pure and storage-agnostic: it
 * names no {@code Path}, no YAML and no storage type. The detail vocabulary of a file that could not be read is the
 * one reader's, in the storage edge.
 *
 * <p>The content rules (#554, FR-001) live here too: kernel {@code Rule}s over the facts of the one read
 * ({@code PackContents}), one holder per pack file kind ({@link StepRules}, {@link ArtifactRules}, {@link AssetRules},
 * {@link MissionTypeRules}), each a set of constants built from the kernel combinators over pure functions. A rule
 * reads no file, holds no flag and knows no surface: the compositions choose which rules a surface runs. Plain Java
 * only; the dependency rule is pinned by {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.packs.domain.rules;

import org.jspecify.annotations.NullMarked;
