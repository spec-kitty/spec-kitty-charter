package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.application.RegistryWriteLock.RegistryBusyException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sweep of what a registration leaves when it does not end (NFR-009): staging directories (named with a registration
 * id; nothing else in the staging root is touched) older than 15 minutes that no running job owns, and the leftovers of a commit a crash interrupted in the pack domain: {@code .pack-commit-*},
 * {@code .pack-write-*} and {@code .history/*.tmp-*}. It runs at startup and every five minutes.
 *
 * <p>A commit copies its tree into a {@code .pack-commit-*} or {@code .pack-write-*} folder and stages its history in a
 * {@code .tmp-*} file while it holds the registry lock, so the pack-domain sweep takes that lock: what it finds then is
 * a leftover whatever its age, and a sweep that cannot take the lock in time skips this round and runs again. An aside
 * copy ({@code *.pack-old-*}) is the listing's to repair or hide, not this sweep's.
 */
public final class StagingSweeper {

    /** A staging directory older than this, with no job that owns it, is removed. */
    public static final Duration MAX_AGE = Duration.ofMinutes(15);

    /** How often the sweep runs. */
    public static final Duration INTERVAL = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(StagingSweeper.class);
    private static final List<String> COMMIT_PREFIXES = List.of(".pack-commit-", ".pack-write-");
    private static final String HISTORY = ".history";
    private static final String HISTORY_TEMP = ".tmp-";

    private final Path stagingRoot;
    private final Path packRoot;
    private final Supplier<Set<Path>> owned;
    private final Clock clock;
    private final RegistryWriteLock registryLock;

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
        this.stagingRoot = Objects.requireNonNull(stagingRoot, "stagingRoot")
                .toAbsolutePath()
                .normalize();
        this.packRoot =
                Objects.requireNonNull(packRoot, "packRoot").toAbsolutePath().normalize();
        this.owned = Objects.requireNonNull(owned, "owned");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.registryLock = Objects.requireNonNull(registryLock, "registryLock");
    }

    /**
     * One sweep.
     *
     * @return how many entries it removed
     */
    public int sweep() {
        return sweepStaging() + sweepLeftovers();
    }

    /**
     * Sweeps now, and again every {@link #INTERVAL}.
     *
     * @param scheduler the scheduler that runs the repeats
     * @return the repeating run, to cancel at shutdown
     */
    public @Nullable ScheduledFuture<?> start(ScheduledExecutorService scheduler) {
        sweepQuietly();
        return scheduler.scheduleWithFixedDelay(
                this::sweepQuietly, INTERVAL.toMillis(), INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void sweepQuietly() {
        try {
            sweep();
        } catch (RuntimeException failure) {
            LOG.warn("The registration sweep failed; it runs again in {} minutes.", INTERVAL.toMinutes(), failure);
        }
    }

    private int sweepStaging() {
        Instant oldest = clock.instant().minus(MAX_AGE);
        Set<Path> running = owned.get().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .collect(Collectors.toSet());
        int removed = 0;
        for (Path entry : children(stagingRoot)) {
            if (isJobDirectory(entry)
                    && !running.contains(entry)
                    && modifiedBefore(entry, oldest)
                    && deleteTree(entry)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * A job's staging directory is a directory named with a registration id. Whatever else lies in the staging root, a
     * file, a link or a directory with another name, is not the registration's: a staging directory that was
     * misconfigured onto a folder with other content must cost nothing but the job directories in it.
     */
    private static boolean isJobDirectory(Path entry) {
        return RegistrationJobs.JOB_ID
                        .matcher(String.valueOf(entry.getFileName()))
                        .matches()
                && Files.isDirectory(entry, java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    private int sweepLeftovers() {
        if (!Files.isDirectory(packRoot)) {
            return 0;
        }
        try {
            return registryLock.exclusively(this::removeLeftovers);
        } catch (RegistryBusyException busy) {
            LOG.info(
                    "The pack registry is busy; the crash-leftover sweep runs again in {} minutes.",
                    INTERVAL.toMinutes());
            return 0;
        }
    }

    /** Called with the registry lock held, so no commit is in flight. */
    private int removeLeftovers() {
        int removed = 0;
        for (Path entry : children(packRoot)) {
            String name = String.valueOf(entry.getFileName());
            if (COMMIT_PREFIXES.stream().anyMatch(name::startsWith) && deleteTree(entry)) {
                removed++;
            } else if (name.equals(HISTORY) && Files.isDirectory(entry)) {
                removed += removeHistoryTemporaries(entry);
            }
        }
        return removed;
    }

    private int removeHistoryTemporaries(Path history) {
        int removed = 0;
        for (Path file : children(history)) {
            if (String.valueOf(file.getFileName()).contains(HISTORY_TEMP) && deleteTree(file)) {
                removed++;
            }
        }
        return removed;
    }

    private static List<Path> children(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> listed = Files.list(directory)) {
            return new ArrayList<>(listed.toList());
        } catch (IOException failure) {
            LOG.warn("Cannot list {} for the registration sweep.", directory.getFileName(), failure);
            return List.of();
        }
    }

    private static boolean modifiedBefore(Path entry, Instant oldest) {
        try {
            return Files.getLastModifiedTime(entry, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    .toInstant()
                    .isBefore(oldest);
        } catch (IOException unreadable) {
            return false;
        }
    }

    /** Deletes the entry whole, links as links: a link is removed and never followed. */
    private static boolean deleteTree(Path entry) {
        try {
            Files.walkFileTree(entry, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, @Nullable IOException failure)
                        throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException failure) {
            LOG.warn("Cannot remove {}; the sweep tries again.", entry.getFileName(), failure);
            return false;
        }
    }
}
