package ai.speckitty.charter.packs.infra.fetch.badfixture;

import ai.speckitty.charter.packs.infra.archive.ArchiveLimits;

/** A fetch-adapter class that names an archive-adapter type: the known-bad of the archive-adapter rule. */
public final class FetcherReachesArchive {

    /** An archive-adapter type returned by the fetch adapter. */
    public ArchiveLimits limits() {
        return ArchiveLimits.DEFAULTS;
    }
}
