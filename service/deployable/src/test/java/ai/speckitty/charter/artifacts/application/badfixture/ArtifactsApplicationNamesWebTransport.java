package ai.speckitty.charter.artifacts.application.badfixture;

import ai.speckitty.charter.web.Problems;

/** An artifacts use case that names the shared web transport: the known-bad of the rule that the application ring is an edge. */
public final class ArtifactsApplicationNamesWebTransport {

    private ArtifactsApplicationNamesWebTransport() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(Problems held) {
        return held;
    }
}
