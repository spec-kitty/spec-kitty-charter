/**
 * The git pack source (FR-004, IC-05, NFR-011, C-008): the one place the service starts a host process, the system
 * {@code git} binary, to fetch one ref of a public repository over https. The threat model of running it (argument
 * injection, transport abuse, rebinding, hostile content, configuration and credentials, resource exhaustion, cleanup) is
 * plan.md's "The system git binary", and each control of it is one class here: {@code GitSourceRules} validates the URL
 * and the ref before any process starts, {@code GitCommand} runs git with a fixed argument list and an environment built
 * from empty under a deadline, {@code GitVersionCheck} disables the source below 2.50.1, and {@code GitSourceFetcher}
 * puts them in order and reads the objects without ever checking out a work tree.
 * {@code NetworkCodeBoundaryTest} keeps {@code ProcessBuilder} and {@code Process} out of every other package.
 */
@NullMarked
package ai.speckitty.charter.packs.infra.fetch.git;

import org.jspecify.annotations.NullMarked;
