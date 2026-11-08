package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import java.nio.file.Path;

/**
 * The port through which the registration job fetches a pack from a source into staging (FR-004, IC-05). One adapter
 * serves each source kind and lives in {@code packs.infra.fetch}; the application ring names only this interface. A
 * fetcher runs in the job, outside every lock, writes only inside the directory it is given, and turns every byte it
 * reads into a staged tree through the bounded extractor.
 */
public interface PackSourceFetcher {

    /**
     * The kind this fetcher serves.
     *
     * @return the source kind
     */
    PackSourceKind kind();

    /**
     * Fetches the source into the job's staging directory.
     *
     * @param request the source as the request named it
     * @param directory the job's staging directory, which the fetcher may write into
     * @return the staged tree and the record of where it came from
     * @throws CharterRefusal {@code pack_source_refused} with a fixed reason, or {@code validation_failed} for an
     *     archive the extractor refuses
     * @throws SourceUpstreamException when the upstream failed or did not answer in time
     */
    Staged fetch(PackSourceRequest request, Path directory) throws CharterRefusal;
}
