package ai.speckitty.charter.missions.application.badfixture;

import ai.speckitty.charter.activations.application.ActivationService;

/** A missions use case that names the activations slice: the known-bad of the rule that only {@code missions.infra.activations} does. */
public final class MissionApplicationNamesActivations {

    private MissionApplicationNamesActivations() {}

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
