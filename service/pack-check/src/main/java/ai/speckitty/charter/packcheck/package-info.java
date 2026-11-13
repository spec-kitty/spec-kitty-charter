/**
 * The {@code pack-check} CLI (ADR-0075 decision 9): a thin main over {@code pack-validation} that checks packs on a pack
 * author's machine and in CI, without the service's closure walk, and writes the contract's problem JSON. It uses no
 * Spring, HTTP, commit store or lock; {@code HexagonalArchitectureTest} pins that as
 * {@code PACK_VALIDATION_IS_FRAMEWORK_FREE}. Empty until its main lands (#478).
 */
@NullMarked
package ai.speckitty.charter.packcheck;

import org.jspecify.annotations.NullMarked;
