package ai.speckitty.charter.packs.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The write generation of the pack domain, a seqlock (research R-lock, NFR-006): it is <em>odd</em> while a directory
 * swap runs and <em>even</em> when none does, and it only ever grows. A pack write brackets its swap with
 * {@link #bracket}; {@link ConsistentRead} records the generation before it reads and refuses the read when it has
 * moved since, and a read that started while it was odd unless its digests agree (FR-023). That closes the A &rarr; B
 * &rarr; A hole in which the content digests of a read agree although a swap ran in between.
 *
 * <p>There is one generation for the registry and one for each pack (a pack's is what the facts cache of the effective
 * reads keys on). Swaps are normally serialized by {@link RegistryWriteLock}; the counters nevertheless stay correct
 * when two swaps overlap: a generation is odd while at least one swap runs.
 *
 * <p>Each pack also counts the moves of its live directory ({@link #moved}, {@link #movesOf}): a swap that moves the
 * directory aside counts one, whether its second rename then succeeds or fails and the aside copy is moved back. A
 * rolled-back swap leaves the very same directory in place, so a lock-free reader that compares only the directory's
 * identity cannot tell it from no swap at all; the count can ({@code PackDirectories}, squad D-1). The generation's
 * parity cannot serve there: a reader may take it after the swap began and compare it again before the swap ends.
 *
 * <p>The generation lives in this JVM only, as the locks do (ADR-0061): a second replica, or another process writing
 * the pack domain, is not seen by it. {@link #shared()} is the one the deployment uses.
 */
public final class PackWriteGeneration {

    private static final PackWriteGeneration SHARED = new PackWriteGeneration();

    private final Counter registry = new Counter();
    private final Map<String, Counter> packs = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> moves = new ConcurrentHashMap<>();

    /** A generation at zero, with no swap running; a test builds its own, the deployment uses {@link #shared()}. */
    public PackWriteGeneration() {}

    /**
     * The generation of this JVM's pack domain, shared by the write store and the verified reads.
     *
     * @return the one shared instance
     */
    public static PackWriteGeneration shared() {
        return SHARED;
    }

    /**
     * Whether a swap was running at the moment the generation was read.
     *
     * @param generation a value of {@link #registry()} or {@link #ofPack(String)}
     * @return true when odd
     */
    public static boolean isSwapping(long generation) {
        return (generation & 1L) == 1L;
    }

    /**
     * The registry's generation.
     *
     * @return odd while any pack's swap runs
     */
    public long registry() {
        return registry.value();
    }

    /**
     * One pack's generation.
     *
     * @param pack the pack's configured name: every write brackets its swap with it (#418 R5), so a reader keys on it too
     * @return odd while that pack's swap runs; zero for a pack never swapped
     */
    public long ofPack(String pack) {
        Counter counter = packs.get(Objects.requireNonNull(pack, "pack"));
        return counter == null ? 0L : counter.value();
    }

    /**
     * Marks a swap as running: the registry's and the pack's generation are odd until {@link #end}.
     *
     * @param pack the pack about to be swapped
     */
    public void begin(String pack) {
        Objects.requireNonNull(pack, "pack");
        registry.begin();
        packs.computeIfAbsent(pack, key -> new Counter()).begin();
    }

    /**
     * Marks a swap as finished. Call it from a {@code finally}, so a failed swap does not leave the generation odd.
     *
     * @param pack the pack {@link #begin} was called for
     */
    public void end(String pack) {
        Objects.requireNonNull(pack, "pack");
        packs.computeIfAbsent(pack, key -> new Counter()).end();
        registry.end();
    }

    /**
     * Records that a swap moved the pack's live directory out of its place. Call it after the first rename and before
     * anything moves the directory back, so that a reader that resolved the directory before that rename and resolves
     * it again after a rollback sees the count move between the two.
     *
     * @param pack the pack's configured name, as {@link #begin} takes it
     */
    public void moved(String pack) {
        moves.computeIfAbsent(Objects.requireNonNull(pack, "pack"), key -> new AtomicLong())
                .incrementAndGet();
    }

    /**
     * How many times a swap has moved the pack's live directory out of its place.
     *
     * @param pack the pack's configured name
     * @return the count; zero for a pack never swapped
     */
    public long movesOf(String pack) {
        AtomicLong count = moves.get(Objects.requireNonNull(pack, "pack"));
        return count == null ? 0L : count.get();
    }

    /**
     * Runs {@code swap} with the generation odd, and even again after it, whatever it does.
     *
     * @param pack the pack being swapped
     * @param swap the directory swap (or the whole commit that ends in it)
     * @param <T> what the swap answers
     * @return what {@code swap} answers
     */
    public <T> T bracket(String pack, Supplier<T> swap) {
        begin(pack);
        try {
            return swap.get();
        } finally {
            end(pack);
        }
    }

    /**
     * Waits until no swap runs.
     *
     * @param max the longest wait
     * @return true when the registry's generation is even on return
     */
    public boolean awaitIdle(Duration max) {
        return registry.awaitIdle(max);
    }

    /** A generation: {@code 2 * finished + (running > 0 ? 1 : 0)}, guarded by its own monitor. */
    private static final class Counter {
        private long finished;
        private int running;

        synchronized void begin() {
            running++;
        }

        synchronized void end() {
            running--;
            if (running == 0) {
                finished++;
                notifyAll();
            }
        }

        synchronized long value() {
            return 2 * finished + (running > 0 ? 1 : 0);
        }

        synchronized boolean awaitIdle(Duration max) {
            long deadline = System.nanoTime() + max.toNanos();
            while (running > 0) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, left);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return running == 0;
                }
            }
            return true;
        }
    }
}
