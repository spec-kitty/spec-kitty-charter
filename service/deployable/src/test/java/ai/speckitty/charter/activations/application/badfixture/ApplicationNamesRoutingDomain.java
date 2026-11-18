package ai.speckitty.charter.activations.application.badfixture;

import ai.speckitty.charter.routing.domain.RoutingCatalog;

/** An activations class that names the routing domain: the known-bad of the rule that only the catalog's store and its read adapter do. */
public final class ApplicationNamesRoutingDomain {

    private ApplicationNamesRoutingDomain() {}

    /**
     * A type the class must not name.
     *
     * @param held the type named
     * @return the same value
     */
    public static Object hold(RoutingCatalog held) {
        return held;
    }
}
