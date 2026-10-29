package ai.speckitty.charter.packs.infra.fetch;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The refusals of the resolver guard. {@code HttpClient} turns a failed connect-time lookup into a bare
 * {@code ConnectException} and drops the guard's {@link NonGlobalAddressException}, and the JDK answers a refused
 * {@code localhost} lookup with the loopback address anyway; this record is how the fetcher still types a rebinding
 * caught at connect as {@code NON_GLOBAL_ADDRESS}.
 *
 * <p>The typing is per fetch, never a shared window: each fetch runs as an {@link Attempt}, made the current thread's
 * attempt on every thread that works for that fetch (the fetching thread around each send, and every task of the
 * fetch's own {@code HttpClient} through {@link Attempt#executor}). The guard records a refusal against the attempt
 * of the thread its lookup runs on, so a refusal in one fetch never relabels a failure of another fetch to the same
 * host. A lookup on a thread that works for no attempt (none known in the JDK's client today) is recorded only in the
 * per-host log; the fetch it served is then typed {@code UNREACHABLE}, still a refusal.
 *
 * <p>The per-host log ({@link #refusedSince}) is kept for observing the guard the JDK installed; the fetcher never
 * types a fetch by it. Only allowlisted hosts are ever recorded, so it is bounded by the allowlist.
 */
final class GuardRefusals {

    private static final ThreadLocal<@Nullable Attempt> CURRENT = new ThreadLocal<>();

    private final Map<String, Long> lastRefusal = new ConcurrentHashMap<>();

    /**
     * Records a refusal of the guard: against the current thread's attempt, if any, and in the per-host log.
     *
     * @param host the refused host
     */
    void record(String host) {
        String normalised = GuideAllowlist.normalise(host);
        lastRefusal.put(normalised, System.nanoTime());
        Attempt attempt = CURRENT.get();
        if (attempt != null) {
            attempt.refused.add(normalised);
        }
    }

    /**
     * Whether the guard refused the host at or after the given instant, in any fetch: for observation only.
     *
     * @param host the host
     * @param sinceNanos a {@link System#nanoTime()} reading
     */
    boolean refusedSince(String host, long sinceNanos) {
        Long at = lastRefusal.get(GuideAllowlist.normalise(host));
        return at != null && at - sinceNanos >= 0;
    }

    /** One fetch, and the hosts the guard refused while a thread worked for it. */
    static final class Attempt {

        private final Set<String> refused = ConcurrentHashMap.newKeySet();

        /**
         * Whether the guard refused the host in this fetch.
         *
         * @param host the host
         * @return {@code true} when a lookup made for this fetch was refused
         */
        boolean refused(String host) {
            return refused.contains(GuideAllowlist.normalise(host));
        }

        /**
         * The task, run as this attempt's on whatever thread runs it: the attempt is the thread's current one for the
         * task's length, and the thread's previous one after.
         *
         * @param task the task
         * @return the bound task
         */
        Runnable bound(Runnable task) {
            Objects.requireNonNull(task, "task");
            return () -> {
                Attempt previous = CURRENT.get();
                CURRENT.set(this);
                try {
                    task.run();
                } finally {
                    restore(previous);
                }
            };
        }

        /**
         * Runs work on the current thread as this attempt's.
         *
         * @param work the work
         * @param <T> its result
         * @return what it returned
         */
        <T> T call(Supplier<T> work) {
            Attempt previous = CURRENT.get();
            CURRENT.set(this);
            try {
                return work.get();
            } finally {
                restore(previous);
            }
        }

        /**
         * An executor that runs every task as this attempt's: the executor of the fetch's own client.
         *
         * @param delegate the executor that runs the tasks
         * @return the binding executor
         */
        Executor executor(Executor delegate) {
            return task -> delegate.execute(bound(task));
        }

        private static void restore(@Nullable Attempt previous) {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
