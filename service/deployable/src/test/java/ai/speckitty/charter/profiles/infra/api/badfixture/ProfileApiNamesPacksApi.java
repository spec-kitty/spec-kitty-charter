package ai.speckitty.charter.profiles.infra.api.badfixture;

import ai.speckitty.charter.packs.infra.api.ArtifactController;

/** The profiles inbound adapter naming the packs inbound adapter: the known-bad of the rule that no profiles class outside {@code profiles.infra.packs} names {@code packs}. */
public final class ProfileApiNamesPacksApi {

    private ProfileApiNamesPacksApi() {}

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
