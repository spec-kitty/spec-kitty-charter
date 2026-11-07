package ai.speckitty.charter.packs.infra.archive.badfixture;

import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;

/** An archive-adapter class that names a fetch-adapter type: the known-bad of the archive-adapter rule. */
public final class ArchiveReachesFetcher {

    /** A fetch-adapter type returned by the archive adapter. */
    public GuideAllowlist allowlist() {
        return GuideAllowlist.EMPTY;
    }
}
