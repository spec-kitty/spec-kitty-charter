package ai.speckitty.charter.packs.infra.api.badfixture;

import ai.speckitty.charter.routing.domain.RoutingCatalog;

/** An inbound-adapter class that names the routing slice's domain: a known-bad of ENTRYPOINT_STAYS_IN_ITS_SLICE. */
public final class EntrypointReachesRouting {

    /** A routing-domain type held by the packs entrypoint. */
    @SuppressWarnings("unused")
    private RoutingCatalog catalog;
}
