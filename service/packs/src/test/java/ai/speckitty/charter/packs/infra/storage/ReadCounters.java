package ai.speckitty.charter.packs.infra.storage;

/**
 * The storage edge's read counters, for a test outside the storage package that counts what a call reads (#554,
 * FR-016): the walks of a pack tree, the runs of the store half and the store's whole reads.
 *
 * @param walks {@code PackFileSet.walks()}
 * @param runs {@code StoreRead.runs()}
 * @param reads {@code FilesystemPackStore.reads()}
 */
public record ReadCounters(long walks, long runs, long reads) {

    /**
     * The counters now.
     *
     * @return the counters
     */
    public static ReadCounters now() {
        return new ReadCounters(PackFileSet.walks(), StoreRead.runs(), FilesystemPackStore.reads());
    }

    /**
     * What was counted since {@code before}.
     *
     * @param before the counters taken first
     * @return the differences
     */
    public ReadCounters since(ReadCounters before) {
        return new ReadCounters(walks - before.walks, runs - before.runs, reads - before.reads);
    }
}
