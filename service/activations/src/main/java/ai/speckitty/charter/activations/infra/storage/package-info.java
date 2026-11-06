/**
 * The storage adapters of the activation documents (FR-019): {@link
 * ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository} and {@link
 * ai.speckitty.charter.activations.infra.storage.FileActivationRepository}, both implementing the domain's {@code
 * ActivationRepository}. Only the composition root names this package from outside the slice, and picks the adapter.
 */
@NullMarked
package ai.speckitty.charter.activations.infra.storage;

import org.jspecify.annotations.NullMarked;
