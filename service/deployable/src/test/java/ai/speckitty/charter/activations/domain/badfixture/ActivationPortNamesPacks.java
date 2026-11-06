package ai.speckitty.charter.activations.domain.badfixture;

import ai.speckitty.charter.packs.domain.Pack;

/**
 * A port-shaped interface of the activations domain whose signature names the packs slice's domain: the known-bad of
 * the per-slice port-signature rule (the packs-pinned rule allows packs domain types, so only the per-slice one bites).
 */
public interface ActivationPortNamesPacks {

    /**
     * Names a packs domain type in a port signature.
     *
     * @param id the pack's id
     * @return a packs type, which an activations port must never name
     */
    Pack pack(String id);
}
