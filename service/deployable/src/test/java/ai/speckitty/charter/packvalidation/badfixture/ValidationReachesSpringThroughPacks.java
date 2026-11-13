package ai.speckitty.charter.packvalidation.badfixture;

import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import org.jspecify.annotations.Nullable;

/**
 * A pack-validation class that reaches Spring only through a packs Spring bean: the transitive half must refuse it, even
 * where a composition root may name the storage adapter the bean shares its package with.
 */
public final class ValidationReachesSpringThroughPacks {

    private ValidationReachesSpringThroughPacks() {}

    /** Names the store the transitive half must refuse. */
    public static @Nullable WholePackStore store() {
        return null;
    }
}
