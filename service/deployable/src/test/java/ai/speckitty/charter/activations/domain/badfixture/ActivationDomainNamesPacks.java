package ai.speckitty.charter.activations.domain.badfixture;

import ai.speckitty.charter.packs.domain.Pack;

/** An activations-domain class that names the packs slice: the known-bad of the slice-isolation rules. */
public final class ActivationDomainNamesPacks {

    private ActivationDomainNamesPacks() {}

    /**
     * A packs type the activations domain must not name.
     *
     * @param pack the type named
     * @return the same value
     */
    public static Object hold(Pack pack) {
        return pack;
    }
}
