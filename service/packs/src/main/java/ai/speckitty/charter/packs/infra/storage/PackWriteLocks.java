package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The per-pack write locks of this JVM: one lock per pack entry of the pack domain, keyed by the entry's normalized
 * absolute path ({@code packs/<name>}, a link's own path, not its target's). {@link CommitStorePackWriteStore#exclusively}
 * holds it across a registration's load, precondition and commit, and {@link StorageRootLocations} takes it to repair
 * a commit a crash interrupted (#281). It is JVM-wide, so every store and listing over one directory shares it however
 * it was built. The map holds one lock per pack entry ever seen, so it stays small. Writers in other processes are not
 * serialized by it (ADR-0026).
 */
final class PackWriteLocks {

    private static final ConcurrentMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private PackWriteLocks() {}

    /**
     * The write lock of a pack entry.
     *
     * @param entry the pack's entry under the pack domain, as it is listed
     * @return the one lock of that entry in this JVM
     */
    static ReentrantLock of(Path entry) {
        return LOCKS.computeIfAbsent(entry.toAbsolutePath().normalize(), key -> new ReentrantLock());
    }

    /**
     * Whether the current thread holds any pack's write lock. Such a thread must only try another pack's lock, never
     * wait for it, or two threads could each wait on the other's; trying a lock it already holds succeeds.
     *
     * @return true when the thread holds a pack's write lock
     */
    static boolean heldByCurrentThread() {
        return LOCKS.values().stream().anyMatch(ReentrantLock::isHeldByCurrentThread);
    }
}
