package ai.speckitty.charter.artifacts.infra.packs.badfixture;

import ai.speckitty.charter.activations.application.ActivationService;

/** The artifacts packs adapter naming the activations slice: the known-bad of the rule that only {@code artifacts.infra.activations} does. */
public final class ArtifactsPacksAdapterNamesActivations {

    private ArtifactsPacksAdapterNamesActivations() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(ActivationService held) {
        return held;
    }
}
