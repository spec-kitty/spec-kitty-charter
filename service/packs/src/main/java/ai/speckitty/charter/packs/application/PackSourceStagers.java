package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.packs.application.RegistrationJobs.Stager;
import java.util.Collection;
import java.util.Optional;

/** The stagers of the source kinds (skeleton: signatures only, built next). */
public final class PackSourceStagers {

    /**
     * Stagers over the fetchers of the kinds that are built.
     *
     * @param fetchers the fetchers, one per kind
     * @param loader reads a staged tree
     */
    public PackSourceStagers(Collection<PackSourceFetcher> fetchers, WholePackWriteStore.Loader loader) {}

    /**
     * The fetcher of a kind.
     *
     * @param kind the kind
     * @return the fetcher; empty when the kind is not built
     */
    public Optional<PackSourceFetcher> fetcherFor(PackSourceKind kind) {
        return Optional.empty();
    }

    /**
     * The stager of a request.
     *
     * @param request the source
     * @param target the target pack id
     * @return the stager
     */
    public Stager stagerFor(PackSourceRequest request, String target) {
        return directory -> {
            throw new UnsupportedOperationException("not built yet");
        };
    }
}
