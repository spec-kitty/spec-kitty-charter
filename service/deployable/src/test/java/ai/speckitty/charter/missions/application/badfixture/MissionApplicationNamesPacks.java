package ai.speckitty.charter.missions.application.badfixture;

import ai.speckitty.charter.packs.application.OfferingReadService;

/** A missions use case that names the packs slice: the known-bad of the rule that only {@code missions.infra.packs} does. */
public final class MissionApplicationNamesPacks {

    private MissionApplicationNamesPacks() {}

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
