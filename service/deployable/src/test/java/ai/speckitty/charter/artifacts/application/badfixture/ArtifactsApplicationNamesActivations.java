package ai.speckitty.charter.artifacts.application.badfixture;

import ai.speckitty.charter.activations.application.ActivationService;

/** An artifacts use case that names the activations slice: the known-bad of the rule that only {@code artifacts.infra.activations} does. */
public final class ArtifactsApplicationNamesActivations {

    private ArtifactsApplicationNamesActivations() {}

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
