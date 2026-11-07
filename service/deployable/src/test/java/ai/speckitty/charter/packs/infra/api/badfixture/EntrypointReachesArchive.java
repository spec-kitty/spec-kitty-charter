package ai.speckitty.charter.packs.infra.api.badfixture;

import ai.speckitty.charter.packs.infra.archive.ArchiveLimits;

/** An inbound-adapter class that names an archive-adapter type: the known-bad of the archive-adapter rules. */
public final class EntrypointReachesArchive {

    /** An archive-adapter type returned by the entrypoint. */
    public ArchiveLimits limits() {
        return ArchiveLimits.DEFAULTS;
    }
}
