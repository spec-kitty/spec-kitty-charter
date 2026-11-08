package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.util.Objects;
import java.util.function.Function;

/** The facts of a pack, kept between reads (#416, IC-07). */
public final class CompositionFactsCache {

    /** The most packs the cache keeps unless it is built with another bound. */
    public static final int DEFAULT_CAPACITY = 256;

    /**
     * A cache.
     *
     * @param revisions the committed revision of a pack
     * @param generation the write generation the pack writes bracket their swap with
     * @param lock the registry write lock
     * @param capacity the most packs kept
     */
    public CompositionFactsCache(
            PackRevisions revisions, PackWriteGeneration generation, RegistryWriteLock lock, int capacity) {
        Objects.requireNonNull(revisions, "revisions");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(lock, "lock");
    }

    /**
     * The facts of a pack.
     *
     * @param pack the configured identifier of the pack
     * @param load reads the facts of the pack from its store
     * @return the facts
     */
    public PackCompositionFacts facts(PackIdentifier pack, Function<PackIdentifier, PackCompositionFacts> load) {
        return load.apply(pack);
    }

    /** How many packs the cache keeps now. */
    int size() {
        return 0;
    }
}
