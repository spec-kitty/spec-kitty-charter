/**
 * The pack-validation module (ADR-0075 decision 3): every pack check as a ruleset of the kernel's validation
 * vocabulary, with the codecs, the classification and the load they need, and no Spring, HTTP, commit store or lock.
 * The packs slice and the {@code pack-check} CLI run it. It depends on the kernel, the profiles and missions domains and
 * the parsing libraries only; {@code HexagonalArchitectureTest} pins that as {@code PACK_VALIDATION_IS_FRAMEWORK_FREE}.
 * The module has no onion rings, and no {@code application} or {@code infra} sub-package, which the rule refuses. Empty
 * until the load moves in (#553).
 */
@NullMarked
package ai.speckitty.charter.packvalidation;

import org.jspecify.annotations.NullMarked;
