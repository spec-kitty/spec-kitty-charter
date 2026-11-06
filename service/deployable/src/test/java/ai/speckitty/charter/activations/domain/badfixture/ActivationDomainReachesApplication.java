package ai.speckitty.charter.activations.domain.badfixture;

import ai.speckitty.charter.activations.application.AvailablePacks;

/** An activations-domain class that names its slice's application ring: the known-bad of the domain purity rules. */
public final class ActivationDomainReachesApplication {

    private ActivationDomainReachesApplication() {}

    /**
     * The application port a domain class must not hold.
     *
     * @param packs the type named
     * @return the same value
     */
    public static Object hold(AvailablePacks packs) {
        return packs;
    }
}
