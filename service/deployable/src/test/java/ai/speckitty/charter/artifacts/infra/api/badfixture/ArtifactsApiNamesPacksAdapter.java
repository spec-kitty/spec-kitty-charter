package ai.speckitty.charter.artifacts.infra.api.badfixture;

import ai.speckitty.charter.artifacts.infra.packs.badfixture.ArtifactsPacksStandIn;

/** The artifacts inbound adapter naming the edge's own packs adapter: the known-bad of the rule that the inbound adapter names no other adapter of its edge. */
public final class ArtifactsApiNamesPacksAdapter {

    private ArtifactsApiNamesPacksAdapter() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(ArtifactsPacksStandIn held) {
        return held;
    }
}
