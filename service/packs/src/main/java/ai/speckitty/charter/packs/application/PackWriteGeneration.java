package ai.speckitty.charter.packs.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The write generation of the pack domain, a seqlock (research R-lock, NFR-006): it is <em>odd</em> while a directory
 * swap runs and <em>even</em> when none does, and it only ever grows. A pack write brackets its swap with
 * {@link #bracket}; {@link ConsistentRead} records the generation before it reads and refuses the read when it was odd
 * or has moved since. That closes the A &rarr; B &rarr; A hole in which the content digests of a read agree although a
 * swap ran in between, and it turns a read that hit the instant the live directory is missing into a stale one.
 *
 * <p>There is one generation for the registry and one for each pack (a pack's is what the facts cache of the effective
 * reads keys on). Swaps are normally serialized by {@link RegistryWriteLock}; the counters nevertheless stay correct
 * when two swaps overlap: a generation is odd while at least one swap runs.
 *
 * <p>The generation lives in this JVM only, as the locks do (ADR-0061): a second replica, or another process writing
 * the pack domain, is not seen by it. {@link #shared()} is the one the deployment uses.
 */
public final class PackWriteGeneration {

    private static final PackWriteGeneration SHARED = new PackWriteGeneration();

    private final Counter registry = new Counter();
    private final Map<String, Counter> packs = new ConcurrentHashMap<>();

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
