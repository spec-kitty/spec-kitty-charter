package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.transport.RetryLaterException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * The one write lock of the pack domain (research R-lock, NFR-009). Every pack write takes it <em>first</em>, with a
 * timed {@code tryLock}, and only then the pack's own lock, so the order is always registry, then pack. It is fair:
 * writers are served in arrival order, so sixteen concurrent {@code PUT}s and a replace all complete. It is
 * reentrant, so a registration that runs its commit inside {@code exclusively} may take it again.
 *
 * <p>A caller that cannot take it within the timeout (5 s by default) gets {@link RegistryBusyException}; the
 * registration job ends {@code refused} with {@code 503} and the caller retries. Readers take no lock.
 *
 * <p><strong>One replica only.</strong> The lock is in-JVM (ADR-0061): a second replica, or a second process writing the
 * pack domain, is not serialized by it. Revisit with ADR-0061's cross-process lock item. The activation writes take it too
 * (ADR-0065 D12, through the activations slice's {@code RegistryWriteCoordination}), so a document is never judged
 * against a pack set a registration is replacing; they take no per-pack lock, and every writer takes this lock first, so
 * the two cannot deadlock.
 */
public final class RegistryWriteLock {

    /** How long a writer waits for the lock before it is refused. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private static final RegistryWriteLock SHARED = new RegistryWriteLock(DEFAULT_TIMEOUT);

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Duration timeout;

    /**
     * A lock that waits {@code timeout}; a test injects a short one.
     *
     * @param timeout the longest a caller waits for the lock
     */
    public RegistryWriteLock(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * The registry lock of this JVM, shared by every write store.
     *
     * @return the one shared instance, with the {@linkplain #DEFAULT_TIMEOUT default timeout}
     */
    public static RegistryWriteLock shared() {
        return SHARED;
    }

    /**
     * Runs {@code work} holding the lock.
     *
     * @param work the write, which takes the pack's own lock inside
     * @param <T> what the work answers
     * @return what {@code work} answers
     * @throws RegistryBusyException when the lock was not free within the timeout, or the wait was interrupted; the work
     *     did not run
     */
    public <T> T exclusively(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        acquire();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The seconds a refused caller is told to wait.
     *
     * @return the timeout rounded up to a whole second, at least 1
     */
    public int retryAfterSeconds() {
        return (int) Math.max(1, (timeout.toMillis() + 999) / 1000);
    }

    /**
     * Whether the current thread holds the lock.
     *
     * @return true inside {@link #exclusively}
     */
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * Whether any thread holds the lock.
     *
     * @return true while a write runs
     */
    public boolean isLocked() {
        return lock.isLocked();
    }

    private void acquire() {
        try {
            if (!lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new RegistryBusyException(
                        "The pack registry is busy with another write; try again shortly.", retryAfterSeconds());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            RegistryBusyException busy =
                    new RegistryBusyException("Interrupted while waiting for the pack registry.", retryAfterSeconds());
            busy.initCause(interrupted);
            throw busy;
        }
    }

    /**
     * The registry lock was not free in time (NFR-009): a {@link RetryLaterException}, so the transport answers it
     * {@code 503} with {@code Retry-After} whichever write met it, and the registration job ends {@code refused} with
     * the same answer.
     */
    public static final class RegistryBusyException extends RetryLaterException {

        private static final long serialVersionUID = 1L;

        /**
         * A refusal to wait longer.
         *
         * @param message the detail sentence
         */
        public RegistryBusyException(String message) {
            this(message, 1);
        }

        /**
         * A refusal to wait longer, with the seconds a client waits.
         *
         * @param message the detail sentence
         * @param retryAfterSeconds the {@code Retry-After} value
         */
        public RegistryBusyException(String message, int retryAfterSeconds) {
            super(message, retryAfterSeconds);
        }
    }
}
