package ai.speckitty.charter.testfixtures;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * The one wait of the service tests: until a state holds, never for a span of time (#659). A test that waits for a
 * span passes vacuously when a loaded runner is slow to reach the state, and fails spuriously when it is slower still;
 * a test that waits for the state is as fast as the state and fails only when the state never comes. The limit is
 * generous, so it is reached only when the behaviour is broken.
 *
 * <p>One narrow exception: a test of code whose deadline is read from the real clock, with no clock to inject (the
 * {@code GitCommand} deadline), waits until that deadline has passed. It waits for the state "the deadline is behind
 * us", it asserts nothing about how long anything took, and it says so where it waits.
 *
 * <p>A test that waits here and carries a JUnit {@code @Timeout} keeps that timeout above {@link #LIMIT}, so the wait's
 * message, which names the state, is the one that fails the test and not the timeout's.
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
        if (!within(LIMIT, state)) {
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError("interrupted while waiting for " + what);
            }
            throw new AssertionError("waited " + LIMIT.toSeconds() + " s for " + what + "; it never came");
        }
    }

    /**
     * Waits until {@code state} holds, or {@code limit} passed, or the thread is interrupted: for a stress test that
     * holds a window open until a state comes and goes on without it, which its own count then reports.
     *
     * @param limit how long to wait at most
     * @param state the state
     * @return whether the state holds
     */
    public static boolean within(Duration limit, BooleanSupplier state) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!state.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0 || Thread.currentThread().isInterrupted()) {
                return false;
            }
            LockSupport.parkNanos(PAUSE_NANOS);
        }
        return true;
    }
}
