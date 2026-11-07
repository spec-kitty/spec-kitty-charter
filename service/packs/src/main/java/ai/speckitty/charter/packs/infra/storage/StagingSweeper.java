package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.RegistryWriteLock;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * The sweep of what a registration leaves when it does not end (NFR-009): staging directories older than 15 minutes that
 * no running job owns, and the leftovers of a commit a crash interrupted in the pack domain: {@code .pack-commit-*},
 * {@code .pack-write-*} and {@code .history/*.tmp-*}. It runs at startup and every five minutes.
 */
public final class StagingSweeper {

    /** A staging directory older than this, with no job that owns it, is removed. */
    public static final Duration MAX_AGE = Duration.ofMinutes(15);

    /** How often the sweep runs. */
    public static final Duration INTERVAL = Duration.ofMinutes(5);

    /**
     * A sweeper.
     *
     * @param stagingRoot the directory the jobs stage in
     * @param packRoot the pack domain, {@code <config-root>/packs}
     * @param owned the staging directories of the jobs that have not ended
     * @param clock the clock the age is read against
     * @param registryLock the lock a commit holds, which the pack-domain sweep takes so it never meets a commit in flight
     */
    public StagingSweeper(
            Path stagingRoot, Path packRoot, Supplier<Set<Path>> owned, Clock clock, RegistryWriteLock registryLock) {
        Objects.requireNonNull(stagingRoot, "stagingRoot");
        Objects.requireNonNull(packRoot, "packRoot");
        Objects.requireNonNull(owned, "owned");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(registryLock, "registryLock");
    }

    /**
     * One sweep.
     *
     * @return how many entries it removed
     */
    public int sweep() {
        return 0;
    }

    /**
     * Sweeps now, and again every {@link #INTERVAL}.
     *
     * @param scheduler the scheduler that runs the repeats
     */
    public void start(ScheduledExecutorService scheduler) {}
}
