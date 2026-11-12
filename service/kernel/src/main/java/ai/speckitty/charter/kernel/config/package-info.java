/**
 * The framework-free vocabulary of the service's configuration that every slice's wiring shares: the refusal to start on
 * a setting ({@link ai.speckitty.charter.kernel.config.StartupRefusal}), which the deployable reports without a stack trace
 * (#454). Nothing here imports Spring; the dependency rule is pinned by {@code HexagonalArchitectureTest}.
 */
@NullMarked
package ai.speckitty.charter.kernel.config;

import org.jspecify.annotations.NullMarked;
