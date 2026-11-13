package ai.speckitty.charter.activations.application.badfixture;

import ai.speckitty.charter.artifacts.infra.api.badfixture.ArtifactsApiStandIn;

/** An activations class that names an artifacts adapter: the known-bad of the rule that no packs or activations class names the artifacts edge. */
public final class ApplicationNamesArtifactsInfra {

    private ApplicationNamesArtifactsInfra() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(ArtifactsApiStandIn held) {
        return held;
    }
}
