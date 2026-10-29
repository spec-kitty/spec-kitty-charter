package ai.speckitty.charter.packs.application;

import java.net.URI;

/**
 * The port through which a toolguide's guide is fetched from a URI (NFR-004, research D8/D8a). The adapter lives in
 * {@code packs.infra.fetch}; the application ring names only this interface. {@link URI} is the one {@code java.net}
 * type the inner rings may name ({@code NetworkCodeBoundaryTest}): it is a value, it opens nothing.
 */
@FunctionalInterface
public interface GuideFetcher {

    /**
     * Fetches the guide the URI names.
     *
     * @param uri the guide's location; only an HTTPS URI whose host is on the configured allowlist is fetched
     * @return the guide's text, decoded as UTF-8
     * @throws GuideFetchRefusal when the URI or anything the exchange meets is refused; nothing was fetched
     */
    String fetch(URI uri) throws GuideFetchRefusal;
}
