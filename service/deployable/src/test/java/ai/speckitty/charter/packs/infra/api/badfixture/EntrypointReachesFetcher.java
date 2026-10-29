package ai.speckitty.charter.packs.infra.api.badfixture;

import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;

/** An inbound-adapter class that names an outbound fetch-adapter type: the known-bad of the fetch-adapter rules. */
public final class EntrypointReachesFetcher {

    /** A fetch-adapter type held by the entrypoint. */
    @SuppressWarnings("unused")
    private GuideAllowlist allowlist = GuideAllowlist.EMPTY;
}
