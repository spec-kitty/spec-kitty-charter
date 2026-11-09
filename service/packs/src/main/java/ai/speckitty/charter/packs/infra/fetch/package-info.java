/**
 * The guarded guide fetcher (NFR-004, research D8a): the service's only outbound HTTP adapter. The inner rings name
 * only the {@code GuideFetcher} port; nothing outside {@code infra} depends on {@code java.net} beyond {@code URI}
 * ({@code NetworkCodeBoundaryTest}).
 */
@NullMarked
package ai.speckitty.charter.packs.infra.fetch;

import org.jspecify.annotations.NullMarked;
