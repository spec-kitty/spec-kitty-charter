package ai.speckitty.charter.packs.fixtures;

import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.testfixtures.Await;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A storage room that can hold a registration job where it runs (#659): its run-time check ({@link #requireForRun}) is
 * the first step of a job's work, once the job is {@code running}, so a job held there is running and has done
 * nothing. Holding both workers there keeps every job started after them {@code pending}, which is how the jobs'
 * queue holds a job in production. The admission checks are never held.
 *
 * <p>A hold ends when the test releases it, and in any case after {@link Await#LIMIT}, so a test that forgets one
 * slows down but never hangs. A hold that ended that way is counted, and the test's teardown asks for the count with
 * {@link #requireNoHoldTimedOut()}: the job went on without being released, which no test means.
 */
public final class HeldRoom implements RegistrationJobs.StorageRoom {

    private final RegistrationJobs.StorageRoom room;
    private final Object lock = new Object();
    private final Deque<CountDownLatch> held = new ArrayDeque<>();
    private boolean holding;
    private int arrivals;
    private int timedOut;

    /**
     * A held room over another room's answers.
     *
     * @param room the room that answers every check once a job goes on
     */
    public HeldRoom(RegistrationJobs.StorageRoom room) {
        this.room = Objects.requireNonNull(room, "room");
    }

    /** Holds every job that reaches its run-time check from now on, until it is released. */
    public void hold() {
        synchronized (lock) {
            holding = true;
        }
    }

    /** Lets the jobs that reach the check from now on through; the jobs already held stay held. */
    public void stopHolding() {
        synchronized (lock) {
            holding = false;
        }
    }

    /**
     * Lets the {@code count} jobs held longest go on.
     *
     * @param count how many to let go
     */
    public void release(int count) {
        synchronized (lock) {
            for (int i = 0; i < count && !held.isEmpty(); i++) {
                held.removeFirst().countDown();
            }
        }
    }

    /** Lets every held job go on, and holds no other. */
    public void releaseAll() {
        synchronized (lock) {
            holding = false;
            held.forEach(CountDownLatch::countDown);
            held.clear();
        }
    }

    /**
     * How many jobs are held now.
     *
     * @return the count
     */
    public int held() {
        synchronized (lock) {
            return held.size();
        }
    }

    /**
     * How many jobs reached the run-time check: the jobs that ran.
     *
     * @return the count
     */
    public int arrivals() {
        synchronized (lock) {
            return arrivals;
        }
    }

    /**
     * Fails when a hold ended by running out its {@link Await#LIMIT} instead of by a release: a job that went on
     * unreleased may have run the test's remaining steps in a state the test never meant. For a test's teardown, after
     * {@link #releaseAll()}.
     *
     * @throws AssertionError when any hold ran out
     */
    public void requireNoHoldTimedOut() {
        int count;
        synchronized (lock) {
            count = timedOut;
        }
        if (count > 0) {
            throw new AssertionError(count + " registration job hold(s) ran out their " + Await.LIMIT.toSeconds()
                    + " s without a release: the job went on unreleased");
        }
    }

    /**
     * Waits until {@code count} jobs are held.
     *
     * @param count how many
     */
    public void awaitHeld(int count) {
        Await.until(count + " registration job(s) held running", () -> held() >= count);
    }

    @Override
    public void require() {
        room.require();
    }

    @Override
    public void require(long archiveBytes) {
        room.require(archiveBytes);
    }

    @Override
    public void require(long archiveBytes, long openUploadBytes) {
        room.require(archiveBytes, openUploadBytes);
    }

    @Override
    public void requireForRun(long archiveBytes) {
        CountDownLatch mine = null;
        synchronized (lock) {
            arrivals++;
            if (holding) {
                mine = new CountDownLatch(1);
                held.addLast(mine);
            }
        }
        if (mine != null) {
            try {
                if (!mine.await(Await.LIMIT.toSeconds(), TimeUnit.SECONDS)) {
                    synchronized (lock) {
                        timedOut++;
                    }
                }
            } catch (InterruptedException interrupted) {
                // The deadline or the shutdown ended the job: it goes on, as an ended job does, and finds itself ended.
                Thread.currentThread().interrupt();
            } finally {
                synchronized (lock) {
                    held.remove(mine);
                }
            }
        }
        room.requireForRun(archiveBytes);
    }
}
