package ai.speckitty.charter.routing.application.badfixture;

import ai.speckitty.charter.activations.application.EffectiveReadService;

/** A routing use case that names the activations slice: the known-bad of the rule that only {@code routing.infra.activations} does. */
public final class RoutingApplicationNamesActivations {

    private RoutingApplicationNamesActivations() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(EffectiveReadService held) {
        return held;
    }
}
