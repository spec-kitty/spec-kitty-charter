/**
 * The {@code pack-doctor} CLI (ADR-0075 decision 9): a thin main over {@code pack-validation} that checks packs on a pack
 * author's machine and in CI, without the service's closure walk, and writes the contract's problem JSON. It uses no
 * Spring, HTTP, commit store or lock; {@code HexagonalArchitectureTest} pins that as
 * {@code PACK_VALIDATION_IS_FRAMEWORK_FREE}. The rule refuses the generated contract and the web transport, so the CLI
 * writes the problem JSON with its own JSON writer, not with {@code ProblemJson}; the contract test of decision 9 keeps
 * the two equal. Like every package the rule selects, it has no {@code application} or {@code infra} sub-package.
 *
 * <p>{@link ai.speckitty.charter.packdoctor.PackDoctorMain} is the composition root (#478): it alone names the packs
 * slice's file-system reader and its production round trip. The rest parses the command line, renders the verdict of
 * {@code pack-validation}'s pack-set check as problems and writes the report; it holds no check of its own (FR-009).
 */
@NullMarked
package ai.speckitty.charter.packdoctor;

import org.jspecify.annotations.NullMarked;
