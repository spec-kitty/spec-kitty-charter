package ai.speckitty.charter.profiles.application.badfixture;

import ai.speckitty.charter.packs.application.OfferingReadService;

/** A profiles use case that names the packs slice: the known-bad of the rule that only {@code profiles.infra.packs} does. */
public final class ProfileApplicationNamesPacks {

    private ProfileApplicationNamesPacks() {}

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
