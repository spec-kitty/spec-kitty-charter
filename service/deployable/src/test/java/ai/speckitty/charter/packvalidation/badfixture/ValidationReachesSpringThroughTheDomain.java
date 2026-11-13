package ai.speckitty.charter.packvalidation.badfixture;

import ai.speckitty.charter.packs.domain.badfixture.DomainUsesSpring;
import org.jspecify.annotations.Nullable;

/**
 * A pack-validation class that names only a packs domain class, which the direct half allows, but one that is a Spring
 * bean: the transitive half must refuse it, two hops away.
 */
public final class ValidationReachesSpringThroughTheDomain {

    private ValidationReachesSpringThroughTheDomain() {}

    /** Names the domain class that reaches Spring. */
    public static @Nullable DomainUsesSpring bean() {
        return null;
    }
}
