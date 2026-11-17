/**
 * The pack-validation module (ADR-0075 decision 3, as amended 2026-11-13 and by #554's S2, S3 and S6): the validation
 * edge. Validate mode is {@link ai.speckitty.charter.packvalidation.PackCheck}: it reads a pack once through the packs
 * domain's {@code PackReading} port, whose readout carries the storage edge's verdict, adds the placement rules
 * ({@code placement}), and maps the findings ({@link ai.speckitty.charter.packvalidation.PackVerdicts}). The module
 * also holds the whole-pack round trip ({@code roundtrip}, #578). It names only the packs domain and the kernel, so the
 * packs domain model stays in {@code packs} (#553); a composition root plugs in the file-system reader. Nothing it
 * reaches uses Spring, HTTP, a commit store or a lock; {@code HexagonalArchitectureTest} pins that as
 * {@code PACK_VALIDATION_IS_FRAMEWORK_FREE}. The module has no onion rings, and no {@code application} or
 * {@code infra} sub-package.
 */
@NullMarked
package ai.speckitty.charter.packvalidation;

import org.jspecify.annotations.NullMarked;
