package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.packs.application.RegistrationJobs;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The latch seam of the registration scenarios (T077): it holds a job before it runs, so its status is {@code pending}, or
 * while it runs, so it is {@code running}, until a step releases it. Production passes every job through.
 */
final class HoldableGate implements RegistrationJobs.JobGate {

    private static final long LIMIT_SECONDS = 30;

    private volatile CountDownLatch before = new CountDownLatch(0);
    private volatile CountDownLatch running = new CountDownLatch(0);
    private final AtomicInteger holdingWhileRunning = new AtomicInteger();

    void holdBeforeRun() {
        before = new CountDownLatch(1);
    }

    void holdWhileRunning() {
        running = new CountDownLatch(1);
    }

    /** Lets every held job go, whatever it is held at. */
    void releaseAll() {
        before.countDown();
        running.countDown();
    }

    /** Lets the jobs held before they run go on, and keeps holding what reaches the running state. */
    void releaseBeforeRun() {
        before.countDown();
    }

    /** Waits until a job is held while it runs. */
    void awaitHeldWhileRunning() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMIT_SECONDS);
        while (holdingWhileRunning.get() == 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("no job reached the running state");
            }
            sleep();
        }
    }

    @Override
    public void beforeRun(String id) {
        await(before);
    }

    @Override
    public void whileRunning(String id) {
        holdingWhileRunning.incrementAndGet();
        try {
            await(running);
        } finally {
            holdingWhileRunning.decrementAndGet();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
