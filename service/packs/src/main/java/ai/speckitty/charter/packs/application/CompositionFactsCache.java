package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The composition facts of each pack, kept between reads so that an effective read recomposes only what changed
 * (IC-07, #416, decision {@code DM-01M6XT2FKK7YPV1GCESFW1ANZ7}). It is a cache over a loader the caller passes, so it
 * holds no storage type and knows no store.
 *
 * <p>An entry is keyed by the pack's configured name and valid for one write generation
 * ({@link PackWriteGeneration#ofPack}) and one file digest ({@link PackRevisions#digestOf}) together. The generation
 * catches a swap, even one that restores the same bytes; the digest, which names every file as well as its bytes,
 * catches a change no write bracketed (an out-of-band edit, a rename included). Every pack write brackets its swap with the pack's configured name (#418 R5), so that is the name
 * the generation is read with here.
 *
 * <p>What it never does:
 *
 * <ul>
 *   <li>serve an entry, or fill one, from a read whose generation was odd (a swap was running) or moved during the read,
 *       or whose digest changed during it;
 *   <li>serve or fill from a holder of the {@link RegistryWriteLock}: the registry's write paths read the packs they are
 *       about to replace, a staged view that is not the committed state;
 *   <li>keep a failure: a pack that cannot be read stays a refusal with the loader's code and detail, on every read. A
 *       digest that cannot be read leaves the answer to the loader, so its refusal is the one reported;
 *   <li>grow past its bound: the least recently used pack leaves when the bound is reached.
 * </ul>
 */
@Service
public final class CompositionFactsCache {

    /** The most packs the cache keeps unless it is built with another bound. */
    public static final int DEFAULT_CAPACITY = 256;

    private final PackRevisions revisions;
    private final PackWriteGeneration generation;
    private final RegistryWriteLock lock;
    private final Map<String, Kept> entries;
    private final AtomicLong loads = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();

    /** What one read of a pack found, and the state of the pack it found it in. */
    private record Kept(long generation, String digest, PackCompositionFacts facts) {}

    /** A map that keeps its most recently used entries, at most {@code capacity} of them. */
    private static final class Lru extends LinkedHashMap<String, Kept> {

        private static final long serialVersionUID = 1L;

        private final int capacity;

        Lru(int capacity) {
            super(16, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Kept> eldest) {
            return size() > capacity;
        }
    }

    /**
     * The cache of this JVM's pack domain: the shared write generation and registry lock, and the default bound.
     *
     * @param revisions the committed revision of a pack and the digest of its files
     */
    @Autowired
    public CompositionFactsCache(PackRevisions revisions) {
        this(revisions, PackWriteGeneration.shared(), RegistryWriteLock.shared(), DEFAULT_CAPACITY);
    }

    /**
     * A cache over a write generation and a lock of its own; a test builds one, the deployment uses the other
     * constructor.
     *
     * @param revisions the committed revision of a pack and the digest of its files
     * @param generation the write generation the pack writes bracket their swap with
     * @param lock the registry write lock whose holders neither read from nor fill the cache
     * @param capacity the most packs kept, at least 1
     * @throws IllegalArgumentException when {@code capacity} is below 1
     */
    public CompositionFactsCache(
            PackRevisions revisions, PackWriteGeneration generation, RegistryWriteLock lock, int capacity) {
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.lock = Objects.requireNonNull(lock, "lock");
        if (capacity < 1) {
            throw new IllegalArgumentException("the cache keeps at least one pack, not " + capacity);
        }
        this.entries = new Lru(capacity);
    }

    /**
     * The facts of a pack: the entry kept for its generation and file digest, else what {@code load} reads now.
     *
     * @param pack the configured identifier of the pack, the name its writes bracket the generation with
     * @param load reads the facts of the pack from its store; its refusal is the answer, unchanged
     * @return the facts of the pack's committed state
     */
    public PackCompositionFacts facts(PackIdentifier pack, Function<PackIdentifier, PackCompositionFacts> load) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(load, "load");
        long before = generation.ofPack(pack.value());
        if (lock.isHeldByCurrentThread() || PackWriteGeneration.isSwapping(before)) {
            return counted(pack, load);
        }
        Optional<String> digest = digestOf(pack);
        if (digest.isEmpty()) {
            return counted(pack, load);
        }
        Optional<PackCompositionFacts> kept = kept(pack.value(), before, digest.get());
        if (kept.isPresent()) {
            hits.incrementAndGet();
            return kept.get();
        }
        PackCompositionFacts loaded = counted(pack, load);
        if (generation.ofPack(pack.value()) == before
                && digestOf(pack).filter(digest.get()::equals).isPresent()) {
            remember(pack.value(), new Kept(before, digest.get(), loaded));
        }
        return loaded;
    }

    /**
     * How many facts this cache read from a store: a test hook of the NFR-003 gate, which counts the work a read does.
     *
     * @return the loads so far
     */
    public long loads() {
        return loads.get();
    }

    /**
     * How many facts this cache answered from an entry: a test hook of the NFR-003 gate.
     *
     * @return the hits so far
     */
    public long hits() {
        return hits.get();
    }

    /** Forgets every entry: the cold start of the NFR-003 gate. */
    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    /** How many packs the cache keeps now. */
    int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    /** One read from the store, counted. */
    private PackCompositionFacts counted(PackIdentifier pack, Function<PackIdentifier, PackCompositionFacts> load) {
        loads.incrementAndGet();
        return load.apply(pack);
    }

    /** The digest of the pack's files; empty when it cannot be read, so that the load's own refusal is what the caller sees. */
    private Optional<String> digestOf(PackIdentifier pack) {
        try {
            return Optional.of(revisions.digestOf(pack));
        } catch (CharterRefusal unreadable) {
            return Optional.empty();
        }
    }

    private Optional<PackCompositionFacts> kept(String name, long generationNow, String digest) {
        synchronized (entries) {
            return Optional.ofNullable(entries.get(name))
                    .filter(entry -> entry.generation() == generationNow
                            && entry.digest().equals(digest))
                    .map(Kept::facts);
        }
    }

    private void remember(String name, Kept entry) {
        synchronized (entries) {
            entries.put(name, entry);
        }
    }
}
