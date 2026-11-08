/**
 * The guarded fetchers (NFR-004, research D8a, R9): the service's only outbound adapters, the guide fetcher and the pack
 * sources (the https archive and the local path), over one guarded HTTPS exchange. The inner rings name only the
 * {@code GuideFetcher} and {@code PackSourceFetcher} ports; nothing outside {@code infra} depends on {@code java.net}
 * beyond {@code URI} ({@code NetworkCodeBoundaryTest}).
 */
@NullMarked
package ai.speckitty.charter.packs.infra.fetch;

import org.jspecify.annotations.NullMarked;
