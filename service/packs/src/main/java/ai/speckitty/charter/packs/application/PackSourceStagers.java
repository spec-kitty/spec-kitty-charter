package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.RegistrationJobs.Stager;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What stages a source for the registration job (FR-004, IC-05): the fetcher of the request's kind, which writes the tree
 * into the job's staging directory, and then the one check that is the source's own, that the name the fetched
 * descriptor declares is the target {@code packId} ({@code name_mismatch}). The tree reaches the job unchanged, and the
 * flow behind it (IC-04) judges it as it judges an upload. A kind no fetcher serves is unavailable.
 */
public final class PackSourceStagers {

    private final Map<PackSourceKind, PackSourceFetcher> fetchers = new EnumMap<>(PackSourceKind.class);
    private final WholePackWriteStore.Loader loader;

    /**
     * Stagers over the fetchers of the kinds that are built.
     *
     * @param fetchers the fetchers, one per kind
     * @param loader reads a staged tree, for the name it declares
     * @throws IllegalArgumentException when two fetchers serve one kind
     */
    public PackSourceStagers(Collection<PackSourceFetcher> fetchers, WholePackWriteStore.Loader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
        for (PackSourceFetcher fetcher : fetchers) {
            if (this.fetchers.putIfAbsent(fetcher.kind(), fetcher) != null) {
                throw new IllegalArgumentException(
                        "two fetchers serve the source kind " + fetcher.kind().wire());
            }
        }
    }

    /**
     * The fetcher of a kind.
     *
     * @param kind the kind
     * @return the fetcher; empty when the kind is not built
     */
    public Optional<PackSourceFetcher> fetcherFor(PackSourceKind kind) {
        return Optional.ofNullable(fetchers.get(kind));
    }

    /**
     * The stager of a request: it runs in the job, outside every lock.
     *
     * @param request the source as the request named it
     * @param target the target pack id the fetched pack's name must equal
     * @return the stager
     * @throws CharterRefusal {@code pack_source_unavailable} when no fetcher serves the kind
     */
    public Stager stagerFor(PackSourceRequest request, String target) {
        PackSourceFetcher fetcher = fetcherFor(request.kind())
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_SOURCE_UNAVAILABLE,
                        "The " + request.kind().wire() + " source is not available in this service."));
        return directory -> {
            Staged staged = fetcher.fetch(request, directory);
            requireNamed(staged, target);
            return staged;
        };
    }

    private void requireNamed(Staged staged, String target) {
        String name = loader.loadStaged(staged.pack()).pack().descriptor().name();
        if (!name.equals(target)) {
            throw SourceRefusal.NAME_MISMATCH.refusal();
        }
    }
}
