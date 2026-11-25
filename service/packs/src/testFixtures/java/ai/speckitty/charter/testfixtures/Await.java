package ai.speckitty.charter.testfixtures;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * The one wait of the service tests: until a state holds, never for a span of time (#659). A test that waits for a
 * span passes vacuously when a loaded runner is slow to reach the state, and fails spuriously when it is slower still;
 * a test that waits for the state is as fast as the state and fails only when the state never comes. The limit is
 * generous, so it is reached only when the behaviour is broken.
 */
public final class Await {

    /** How long a state may take before the wait fails: far beyond any state a test waits for. */
    public static final Duration LIMIT = Duration.ofSeconds(30);

    // Fine enough that a test waiting for a thousand short jobs one after another spends little in the pauses (#461).
    private static final long PAUSE_NANOS = Duration.ofNanos(100_000).toNanos();

    private Await() {}

    /**
     * Waits until {@code state} holds.
     *
     * @param what the state, as the failure names it
     * @param state the state
     * @throws AssertionError when the state does not hold within {@link #LIMIT}
     */
    public static void until(String what, BooleanSupplier state) {
        long deadline = System.nanoTime() + LIMIT.toNanos();
        while (!state.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) {
                throw new AssertionError("waited " + LIMIT.toSeconds() + " s for " + what + "; it never came");
            }
            LockSupport.parkNanos(PAUSE_NANOS);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for " + what);
            }
        }
    }
}
