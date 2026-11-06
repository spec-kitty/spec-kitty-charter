/**
 * The service-wide HTTP transport, shared by every slice's inbound adapter: the closed problem bodies
 * ({@link ai.speckitty.charter.web.Problems}, {@link ai.speckitty.charter.web.ProblemHandler}), the mapping of a charter
 * refusal to its problem body ({@link ai.speckitty.charter.web.RefusalHandler}), the JSON body guard
 * ({@link ai.speckitty.charter.web.JsonBodyGuard}) and how the problem bodies serialize
 * ({@link ai.speckitty.charter.web.ProblemJson}). It is an adapter ring: it names no slice, and no domain or application
 * ring names it.
 */
@NullMarked
package ai.speckitty.charter.web;

import org.jspecify.annotations.NullMarked;
