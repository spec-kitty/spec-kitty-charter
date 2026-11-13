package ai.speckitty.charter.web.badfixture;

import ai.speckitty.charter.artifacts.application.badfixture.ArtifactsUseCaseStandIn;

/** A shared web transport class that names the artifacts edge: the known-bad of the rule that it names no slice. */
public final class WebNamesTheArtifactsEdge {

    private WebNamesTheArtifactsEdge() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(ArtifactsUseCaseStandIn held) {
        return held;
    }
}
