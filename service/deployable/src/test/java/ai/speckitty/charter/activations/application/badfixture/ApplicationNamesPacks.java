package ai.speckitty.charter.activations.application.badfixture;

import ai.speckitty.charter.packs.application.OfferingReadService;

/**
 * An activations use case that names the packs slice's application ring directly, not through its {@code AvailablePacks}
 * port: the known-bad of the rule that only the packs adapter names {@code packs}.
 */
public final class ApplicationNamesPacks {

    private ApplicationNamesPacks() {}

    /**
     * A packs use case the activations application ring must not hold.
     *
     * @param offering the type named
     * @return the same value
     */
    public static Object hold(OfferingReadService offering) {
        return offering;
    }
}
