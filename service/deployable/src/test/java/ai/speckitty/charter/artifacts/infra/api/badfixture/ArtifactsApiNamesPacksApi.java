package ai.speckitty.charter.artifacts.infra.api.badfixture;

import ai.speckitty.charter.packs.infra.api.ArtifactController;

/** The artifacts inbound adapter naming the packs inbound adapter: the known-bad of the rule that no artifacts class outside {@code artifacts.infra.packs} names {@code packs}. */
public final class ArtifactsApiNamesPacksApi {

    private ArtifactsApiNamesPacksApi() {}

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
