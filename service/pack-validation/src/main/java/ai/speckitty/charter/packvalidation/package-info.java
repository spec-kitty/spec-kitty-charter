/**
 * The pack-validation module (ADR-0075 decision 3, as amended 2026-11-13): every pack check, run over the pack's
 * contents that the packs slice's own Spring-free load builds ({@link ai.speckitty.charter.packvalidation.PackLoad}).
 * It depends on the packs slice and names only its domain, the {@code PackInspector} port included, so the packs domain
 * model stays in {@code packs} (#553); a composition root plugs in the file-system adapter. Nothing it reaches uses
 * Spring, HTTP, a commit store or a lock; {@code HexagonalArchitectureTest} pins that as
 * {@code PACK_VALIDATION_IS_FRAMEWORK_FREE}. The module has no onion rings, and no {@code application} or {@code infra}
 * sub-package.
 */
@NullMarked
package ai.speckitty.charter.packvalidation;

import org.jspecify.annotations.NullMarked;
