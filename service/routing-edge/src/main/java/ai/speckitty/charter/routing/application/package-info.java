/**
 * The routing slice's application ring (ADR-0080): the model recommendation's use case and the port it reads the
 * activation's inputs through. It names its own domain, the kernel and Spring only; the adapter over the activations
 * slice realizes the port.
 */
@NullMarked
package ai.speckitty.charter.routing.application;

import org.jspecify.annotations.NullMarked;
