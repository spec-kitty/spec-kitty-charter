/**
 * A lean, framework-free rule vocabulary (ADR-0075 decision 1). A {@link ai.speckitty.charter.kernel.validation.Rule}
 * returns every {@link ai.speckitty.charter.kernel.validation.Reason} it finds, each with a code, a severity and a
 * location, and composes with {@code on}, {@code forEach} and {@code when}. A
 * {@link ai.speckitty.charter.kernel.validation.Constraint} is the rule of one predicate; a
 * {@link ai.speckitty.charter.kernel.validation.ModularRuleset} is a rule of rules that either collects every reason
 * ({@code check}) or fails fast on the first violation ({@code guard}). Depends on {@code java} and JSpecify only;
 * the rule is pinned by {@code HexagonalArchitectureTest}. Derived from {@code modular-validators} (Apache-2.0); see
 * {@code kernel-validation-ATTRIBUTION.md}.
 */
@NullMarked
package ai.speckitty.charter.kernel.validation;

import org.jspecify.annotations.NullMarked;
