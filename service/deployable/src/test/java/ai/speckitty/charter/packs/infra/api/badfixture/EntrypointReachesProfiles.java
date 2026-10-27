package ai.speckitty.charter.packs.infra.api.badfixture;

import ai.speckitty.charter.profiles.domain.AgentProfile;

/** An inbound-adapter class that names another slice's domain: the known-bad of ENTRYPOINT_STAYS_IN_ITS_SLICE. */
public final class EntrypointReachesProfiles {

    /** A profiles-domain type held by the packs entrypoint. */
    @SuppressWarnings("unused")
    private AgentProfile profile;
}
