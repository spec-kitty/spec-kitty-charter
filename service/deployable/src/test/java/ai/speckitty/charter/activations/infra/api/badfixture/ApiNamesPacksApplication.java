package ai.speckitty.charter.activations.infra.api.badfixture;

import ai.speckitty.charter.packs.application.OfferingReadService;

/**
 * An activations controller that names a packs use case: the known-bad of the rule that only the packs adapter names
 * {@code packs}.
 */
public final class ApiNamesPacksApplication {

    private ApiNamesPacksApplication() {}

    /**
     * A packs use case the activations REST adapter must not hold.
     *
     * @param offering the type named
     * @return the same value
     */
    public static Object hold(OfferingReadService offering) {
        return offering;
    }
}
