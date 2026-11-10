package ai.speckitty.charter.missions.infra.api.badfixture;

import ai.speckitty.charter.packs.infra.api.ArtifactController;

/** The missions inbound adapter naming the packs inbound adapter: the known-bad of the rule that no missions class outside {@code missions.infra.packs} names {@code packs}. */
public final class MissionApiNamesPacksApi {

    private MissionApiNamesPacksApi() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(ArtifactController held) {
        return held;
    }
}
