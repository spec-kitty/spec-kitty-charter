/**
 * The {@code pack-check} CLI (ADR-0075 decision 9): a thin main over {@code pack-validation} that checks packs on a pack
 * author's machine and in CI, without the service's closure walk, and writes the contract's problem JSON. It uses no
 * Spring, HTTP, commit store or lock; {@code HexagonalArchitectureTest} pins that as
 * {@code PACK_VALIDATION_IS_FRAMEWORK_FREE}. The rule refuses the generated contract and the web transport, so the CLI
 * writes the problem JSON with its own JSON writer, not with {@code ProblemJson}; the contract test of decision 9 keeps
 * the two equal. Like every package the rule selects, it has no {@code application} or {@code infra} sub-package. Empty
 * until its main lands (#478).
 */
@NullMarked
package ai.speckitty.charter.packcheck;

import org.jspecify.annotations.NullMarked;
