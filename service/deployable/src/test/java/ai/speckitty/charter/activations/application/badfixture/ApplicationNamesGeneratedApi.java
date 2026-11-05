package ai.speckitty.charter.activations.application.badfixture;

import ai.speckitty.charter.api.model.ActivationConfig;

/** An activations use case that names a generated transport type: the known-bad of the transport rule. */
public final class ApplicationNamesGeneratedApi {

    private ApplicationNamesGeneratedApi() {}

    /**
     * A generated DTO held outside an inbound adapter.
     *
     * @param document the type named
     * @return the same value
     */
    public static Object hold(ActivationConfig document) {
        return document;
    }
}
