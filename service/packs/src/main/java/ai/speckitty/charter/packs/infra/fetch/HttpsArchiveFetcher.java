package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import java.nio.file.Path;

/** The https archive source (skeleton: signatures only, built next). */
public final class HttpsArchiveFetcher implements PackSourceFetcher {

    /** The default cap on an archive, 32 MiB (NFR-004). */
    public static final long DEFAULT_MAX_BYTES = 32L * 1024 * 1024;

    /**
     * A fetcher over the guarded exchange and the extractor.
     *
     * @param exchange the guarded exchange
     * @param reader the extractor
     * @param maxBytes the largest archive accepted
     */
    public HttpsArchiveFetcher(HttpsExchange exchange, PackArchiveReader reader, long maxBytes) {}

    @Override
    public PackSourceKind kind() {
        return PackSourceKind.HTTPS_ARCHIVE;
    }

    @Override
    public Staged fetch(PackSourceRequest request, Path directory) throws CharterRefusal {
        throw new UnsupportedOperationException("the https archive fetcher is not built yet");
    }
}
