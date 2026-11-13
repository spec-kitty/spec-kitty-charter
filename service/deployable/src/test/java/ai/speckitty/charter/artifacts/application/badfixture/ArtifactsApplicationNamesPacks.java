package ai.speckitty.charter.artifacts.application.badfixture;

import ai.speckitty.charter.packs.application.OfferingReadService;

/** An artifacts use case that names the packs slice: the known-bad of the rule that only {@code artifacts.infra.packs} does. */
public final class ArtifactsApplicationNamesPacks {

    private ArtifactsApplicationNamesPacks() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(OfferingReadService held) {
        return held;
    }
}
