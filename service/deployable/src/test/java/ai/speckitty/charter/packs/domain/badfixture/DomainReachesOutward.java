package ai.speckitty.charter.packs.domain.badfixture;

import ai.speckitty.charter.packs.application.ServiceInfo;
import ai.speckitty.charter.packs.infra.storage.PackLocation;

/** A domain class that names an application type and a storage-adapter type: the known-bad of the onion rules. */
public final class DomainReachesOutward {

    /** An application type held by the domain. */
    @SuppressWarnings("unused")
    private ServiceInfo listing;

    /** A storage type held by the domain. */
    @SuppressWarnings("unused")
    private PackLocation location;
}
