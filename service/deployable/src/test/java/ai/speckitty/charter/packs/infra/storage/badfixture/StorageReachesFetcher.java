package ai.speckitty.charter.packs.infra.storage.badfixture;

import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;

/** A storage-adapter class that names an outbound fetch-adapter type: the known-bad of the fetch-adapter rule. */
public final class StorageReachesFetcher {

    /** A fetch-adapter type held by the storage adapter. */
    @SuppressWarnings("unused")
    private GuideAllowlist allowlist = GuideAllowlist.EMPTY;
}
