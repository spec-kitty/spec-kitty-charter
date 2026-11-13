package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.kernel.transport.RetryLaterException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The registration jobs of FR-024 (IC-14): a bounded executor that runs the registration flow after the request was
 * answered {@code 202}, and the in-memory status the client polls.
 */
public final class RegistrationJobs implements AutoCloseable {

    /**
     * The grammar of a registration id, which is also the name of the job's staging directory: a ULID. What does not
     * match is not a job's, and the sweep of the staging root leaves it alone.
     */
    public static final Pattern JOB_ID = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    /** Registrations that run at once. */
    public static final int RUNNING_LIMIT = 2;

    /** Registrations that wait for a worker; the next is refused with {@code Retry-After}. */
    public static final int WAITING_LIMIT = 8;

    /**
     * Uploads open at once, counted apart from the queue: as many as the queue has slots, so that the bodies arriving
     * are never more than the jobs that can be waiting for them (#418, F1). The next is refused with {@code Retry-After}.
     */
    public static final int UPLOAD_LIMIT = RUNNING_LIMIT + WAITING_LIMIT;

    /** The most statuses kept, ended or not (one replica, ADR-0061). */
    public static final int MAX_RETAINED = 1_000;

    /** How long a status is kept after its job ended. */
    public static final Duration RETENTION = Duration.ofHours(1);

    /** The source deadline (60 s) plus 30 s: a registration not finished by then ends {@code refused} (NFR-008). */
    public static final Duration DEADLINE = Duration.ofSeconds(90);

    /** The point a job has reached; it moves only forward. */
    public enum State {
        /** Accepted, waiting for a worker. */
        PENDING,
        /** A worker runs it. */
        RUNNING,
        /** Ended with a stored pack, or an unchanged one. */
        SUCCEEDED,
        /** Ended with the refusal the synchronous API would have answered. */
        REFUSED
    }

    /** How a job ended. */
    public sealed interface Ending permits Succeeded, Refused {}

    /**
     * A job that stored the pack, or found it unchanged.
     *
     * @param outcome what was done
     * @param source what the stager recorded of where the tree came from; empty for an archive upload
     */
    public record Succeeded(RegistrationOutcome outcome, Map<String, String> source) implements Ending {

        /**
         * Checks the fields and copies the map.
         *
         * @param outcome what was done
         * @param source the source record
         */
        public Succeeded {
            Objects.requireNonNull(outcome, "outcome");
            source = Map.copyOf(source);
        }
    }

    /** Why a refused job was refused: the kinds of answer that are not a code of their own. */
    public enum Kind {
        /** A refusal with a code of the refusal table. */
        CODED,
        /** The {@code If-Match} no longer names the stored revision ({@code 412}, a plain problem). */
        STALE,
        /** The registry lock was not free in time, or the service is stopping ({@code 503}, a plain problem). */
        BUSY,
        /** The job failed in a way no refusal names: the answer is the service's own internal error, and it is logged. */
        FAILED,
        /** The source's upstream failed ({@code 502}, a plain problem). */
        UPSTREAM_FAILED,
        /** The source's upstream did not answer in time ({@code 504}, a plain problem). */
        UPSTREAM_TIMEOUT
    }

    /**
     * A job that was refused.
     *
     * @param kind which kind of answer it is
     * @param refusal the coded refusal; present exactly for {@link Kind#CODED}
     * @param detail the detail sentence of the answer
     */
    public record Refused(Kind kind, @Nullable CharterRefusal refusal, String detail) implements Ending {

        /**
         * Checks the fields.
         *
         * @param kind which kind of answer it is
         * @param refusal the coded refusal
         * @param detail the detail sentence
         */
        public Refused {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * What a job's status shows at one moment.
     *
     * @param id the registration id, a ULID
     * @param packId the target pack
     * @param state where the job is
     * @param acceptedAt when the registration was accepted
     * @param finishedAt when it ended; null while it is pending or running
     * @param ending how it ended; null while it is pending or running
     */
    public record Status(
            String id,
            String packId,
            State state,
            Instant acceptedAt,
            @Nullable Instant finishedAt,
            @Nullable Ending ending) {

        /**
         * Checks the fields.
         *
         * @param id the registration id
         * @param packId the target pack
         * @param state where the job is
         * @param acceptedAt when it was accepted
         * @param finishedAt when it ended
         * @param ending how it ended
         */
        public Status {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(acceptedAt, "acceptedAt");
        }
    }

    /**
     * A tree the job's stager produced.
     *
     * @param pack the staged pack
     * @param source what the stager records of where it came from (the commit of a git ref, for one); empty for an upload
     */
    public record Staged(StagedPack pack, Map<String, String> source) {

        /**
         * Checks the fields and copies the map.
         *
         * @param pack the staged pack
         * @param source the source record
         */
        public Staged {
            Objects.requireNonNull(pack, "pack");
            source = Map.copyOf(source);
        }
    }

    /** Fetches or extracts the tree to register, into the job's staging directory. It runs outside every lock. */
    @FunctionalInterface
    public interface Stager {

        /**
         * Produces the staged tree.
         *
         * @param directory the job's staging directory, which it may write into
         * @return the staged tree
         * @throws Exception a {@link CharterRefusal} for a refusal of the flow; anything else is a failure of the service
         */
        Staged stage(Path directory) throws Exception;
    }

    /** The seam a test holds a job at; production passes through. */
    public interface JobGate {

        /** A gate that holds nothing. */
        JobGate OPEN = new JobGate() {};

        /**
         * Called by the worker before the job is {@link State#RUNNING}.
         *
         * @param id the registration id
         */
        default void beforeRun(String id) {}

        /**
         * Called by the worker once the job is {@link State#RUNNING}, before its work starts.
         *
         * @param id the registration id
         */
        default void whileRunning(String id) {}
    }

    /**
     * Where a job's attempt came from, for its audit entry.
     *
     * @param worktree the worktree identity
     * @param credential what the request carried
     * @param operation the operation
     * @param sourceKind the source kind
     * @param locator the locator with its query cut
     */
    public record Origin(
            String worktree, AuditLog.Credential credential, String operation, String sourceKind, String locator) {

        /**
         * Checks the fields.
         *
         * @param worktree the worktree identity
         * @param credential what the request carried
         * @param operation the operation
         * @param sourceKind the source kind
         * @param locator the locator with its query cut
         */
        public Origin {
            Objects.requireNonNull(worktree, "worktree");
            Objects.requireNonNull(credential, "credential");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(sourceKind, "sourceKind");
            Objects.requireNonNull(locator, "locator");
        }
    }

    /**
     * A body that is arriving: a directory in staging that holds the spool, and no slot of the queue (#418, A4). A
     * reservation made with it adopts the directory as the job's; one that is never made leaves the directory to
     * {@link #discard()}.
     */
    public interface Upload {

        /**
         * The registration id the job will have, the name of the directory.
         *
         * @return the ULID
         */
        String id();

        /**
         * The directory the body is spooled in.
         *
         * @return the directory, owner-only
         */
        Path directory();

        /** Removes the directory unless a reservation adopted it, and ends the upload's ownership of it. Idempotent. */
        void discard();
    }

    /** A slot in the queue, taken once the body is spooled. */
    public interface Reservation {

        /**
         * The registration id.
         *
         * @return the ULID the status URI ends in
         */
        String id();

        /**
         * The job's staging directory, created and owned by the job.
         *
         * @return the directory
         */
        Path directory();

        /**
         * Hands the job to the executor.
         *
         * @param stager what produces the tree
         * @return {@code true} when the job was handed over; {@code false} when it had ended already, because the
         *     deadline or the shutdown reached it while it was reserved: its status then holds that end (for the deadline,
         *     {@code registration_timed_out}), nothing runs, and the caller answers with that status
         * @throws IllegalStateException when the job was started before
         */
        boolean start(Stager stager);

        /** Gives the slot back, when the request failed before the job could start; deletes the directory. */
        void abandon();

        /**
         * The status now.
         *
         * @return the pending status the {@code 202} answers
         */
        Status status();

        /**
         * The stored revision when the slot was taken.
         *
         * @return the revision token; empty when no pack was stored under the name
         */
        String revisionBefore();
    }

    /**
     * What the jobs need of the outside world.
     *
     * @param clock the clock of the statuses and the deadline
     * @param service the registration flow
     * @param revisions the stored revisions
     * @param audit the audit trail
     * @param gate the test seam
     * @param room the free-space check
     * @param staging the staging area the jobs stage in
     */
    public record Dependencies(
            java.time.Clock clock,
            PackRegistrationService service,
            PackRevisions revisions,
            AuditLog audit,
            JobGate gate,
            StorageRoom room,
            StagingArea staging) {

        /**
         * Checks the fields.
         *
         * @param clock the clock of the statuses and the deadline
         * @param service the registration flow
         * @param revisions the stored revisions
         * @param audit the audit trail
         * @param gate the test seam
         * @param room the free-space check
         * @param staging the staging area the jobs stage in
         */
        public Dependencies {
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(revisions, "revisions");
            Objects.requireNonNull(audit, "audit");
            Objects.requireNonNull(gate, "gate");
            Objects.requireNonNull(room, "room");
            Objects.requireNonNull(staging, "staging");
        }
    }

    /** The check that the volumes a registration writes to have room, before it writes anything. */
    @FunctionalInterface
    public interface StorageRoom {

        /** A volume that always has room. */
        StorageRoom UNBOUNDED = () -> {};

        /** The archive size that is not known: a chunked body, a source. It is judged as a full-size archive. */
        long UNKNOWN_BYTES = -1;

        /** The archive bound of a registration, 32 MiB (NFR-004): what an upload of unknown length is taken to write. */
        long ARCHIVE_BOUND_BYTES = 32L * 1024 * 1024;

        /**
         * Checks the room for an archive of the largest size a registration takes.
         *
         * @throws CharterRefusal {@code PACK_STORAGE_FULL} when a volume has less than the per-job peak free
         */
        void require();

        /**
         * Checks the room for an archive of this size: at admission, the declared length of the upload.
         *
         * @param archiveBytes the archive's size, or {@link #UNKNOWN_BYTES}
         * @throws CharterRefusal {@code PACK_STORAGE_FULL} when a volume has less than the per-job peak free
         */
        default void require(long archiveBytes) {
            require();
        }

        /**
         * Checks the room for an archive of this size when other uploads are open: the bytes they are declared to write
         * are not free for this one.
         *
         * @param archiveBytes the archive's size, or {@link #UNKNOWN_BYTES}
         * @param openUploadBytes the declared bytes of the uploads already open ({@link #ARCHIVE_BOUND_BYTES} for each
         *     of unknown length)
         * @throws CharterRefusal {@code PACK_STORAGE_FULL} when a volume has less than the peak plus these bytes free
         */
        default void require(long archiveBytes, long openUploadBytes) {
            require(archiveBytes);
        }

        /**
         * Checks the room for what a job still has to write when it runs, which is less than its peak when the archive is
         * already spooled: the tree it expands to and the copy the commit makes, not the archive again.
         *
         * @param archiveBytes the size of the archive already spooled in staging, or {@link #UNKNOWN_BYTES} when the job
         *     has still to fetch it (a source), which is judged as a full-size archive written at run time
         * @throws CharterRefusal {@code PACK_STORAGE_FULL} when a volume has less than that free
         */
        default void requireForRun(long archiveBytes) {
            require(archiveBytes);
        }
    }

    private static final System.Logger LOG = System.getLogger(RegistrationJobs.class.getName());

    private static final String INTERNAL_DETAIL = "The charter service failed unexpectedly; the failure is logged.";
    private static final String STOPPING_DETAIL = "The service is stopping; send the registration again.";

    private final Dependencies dependencies;
    private final java.time.Clock clock;
    private final ThreadPoolExecutor executor;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    /** The uploads open, with the bytes each is declared to write; guarded by itself. */
    private final Map<Path, Long> uploads = new LinkedHashMap<>();

    private final SecureRandom random = new SecureRandom();
    private volatile boolean closing;
    private @Nullable ScheduledExecutorService watchdog;
    private @Nullable ScheduledFuture<?> watchdogRuns;

    /**
     * Jobs over their dependencies, with no watchdog: {@link #expireOverdue()} is called by whoever holds the clock. The
     * composition root starts the watchdog ({@link #startWatchdog(Duration)}); a test moves its own clock.
     *
     * @param dependencies the clock, the flow, the stored revisions, the audit trail and the seams
     */
    public RegistrationJobs(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.clock = dependencies.clock();
        AtomicInteger workers = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                RUNNING_LIMIT, RUNNING_LIMIT, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "charter-registration-" + workers.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * The staging area the jobs work in, for the stagers that make a directory inside a job's.
     *
     * @return the staging area
     */
    public StagingArea staging() {
        return dependencies.staging();
    }

    /**
     * Starts the thread that ends the jobs that passed the deadline, every {@code period} of real time.
     *
     * @param period how often it looks
     * @return these jobs
     */
    public RegistrationJobs startWatchdog(Duration period) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "charter-registration-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        watchdogRuns = scheduler.scheduleWithFixedDelay(
                this::expireOverdueQuietly, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        watchdog = scheduler;
        return this;
    }

    private void expireOverdueQuietly() {
        try {
            expireOverdue();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The registration watchdog failed; it runs again.", failure);
        }
    }

    /**
     * Takes a slot for a registration of {@code packId}: a full queue is refused, and the preconditions are judged
     * against the stored revision, before the caller reads the body.
     *
     * @param packId the target pack
     * @param preconditions the conditional headers
     * @param origin where the attempt came from
     * @return the reservation, whose staging directory exists
     * @throws RetryLaterException when two jobs run and eight wait already, or the service is stopping
     * @throws PreconditionException {@code STALE} when the preconditions do not hold against the stored pack
     */
    public Reservation reserve(String packId, Preconditions preconditions, Origin origin) {
        return take(packId, preconditions, origin, null, StorageRoom.UNKNOWN_BYTES);
    }

    /**
     * Takes a slot for the registration of an upload whose body is spooled: the same refusals as
     * {@link #reserve(String, Preconditions, Origin)}, judged now that the body is here, and the job adopts the upload's
     * directory. The deadline of the job counts from this moment, not from the start of the transfer.
     *
     * @param packId the target pack
     * @param preconditions the conditional headers
     * @param origin where the attempt came from
     * @param upload the spooled body
     * @param archiveBytes the size of the spooled archive, which the job's free-space check is made for
     * @return the reservation, whose directory is the upload's
     * @throws RetryLaterException when two jobs run and eight wait already, or the service is stopping
     * @throws PreconditionException {@code STALE} when the preconditions do not hold against the stored pack
     * @throws IllegalStateException when a reservation adopted the upload already
     */
    public Reservation reserve(
            String packId, Preconditions preconditions, Origin origin, Upload upload, long archiveBytes) {
        return take(packId, preconditions, origin, Objects.requireNonNull(upload, "upload"), archiveBytes);
    }

    private Reservation take(
            String packId, Preconditions preconditions, Origin origin, @Nullable Upload upload, long archiveBytes) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(preconditions, "preconditions");
        Objects.requireNonNull(origin, "origin");
        requireOpen();
        OpenUpload claimed = upload instanceof OpenUpload open ? open : null;
        if (claimed != null && !claimed.claim()) {
            throw new IllegalStateException("the upload " + claimed.id() + " was adopted by a reservation already");
        }
        try {
            return takeSlot(packId, preconditions, origin, upload, archiveBytes, claimed);
        } catch (RuntimeException | Error refused) {
            if (claimed != null) {
                claimed.unclaim();
            }
            throw refused;
        }
    }

    private Reservation takeSlot(
            String packId,
            Preconditions preconditions,
            Origin origin,
            @Nullable Upload upload,
            long archiveBytes,
            @Nullable OpenUpload claimed) {
        if (inFlight.incrementAndGet() > RUNNING_LIMIT + WAITING_LIMIT) {
            inFlight.decrementAndGet();
            throw queueFull();
        }
        try {
            Optional<PackRevision> stored = storedRevision(packId);
            preconditions.requireMet(stored);
            Job job = new Job(this, packId, preconditions, origin, stored, upload, archiveBytes);
            admit(job);
            if (claimed != null) {
                claimed.adopt();
            }
            return job;
        } catch (RuntimeException | Error refused) {
            inFlight.decrementAndGet();
            throw refused;
        }
    }

    /**
     * The admission of an upload, before its body is read: refuses what {@link #reserve} would refuse now, without taking
     * a slot, so that a full queue or a stale {@code If-Match} is answered before 32 MiB are sent. The same checks are
     * made again when the body is spooled, since the queue and the pack move meanwhile.
     *
     * @param packId the target pack
     * @param preconditions the conditional headers
     * @throws RetryLaterException when two jobs run and eight wait already, or the service is stopping
     * @throws PreconditionException {@code STALE} when the preconditions do not hold against the stored pack
     */
    public void precheck(String packId, Preconditions preconditions) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(preconditions, "preconditions");
        requireOpen();
        if (inFlight.get() >= RUNNING_LIMIT + WAITING_LIMIT) {
            throw queueFull();
        }
        preconditions.requireMet(storedRevision(packId));
    }

    /**
     * Opens the directory a body is spooled in. It holds no slot of the queue, but a place among at most
     * {@link #UPLOAD_LIMIT} uploads open at once, and the room it will need is asked for on top of what the open uploads
     * are declared to write: each is bounded at 32 MiB by the body guard, and the sweep leaves a directory that is open
     * (#418, A4, F1). The place is given back when a reservation adopts the directory or {@link Upload#discard()} runs.
     *
     * @param declaredBytes the declared length of the body, or {@link StorageRoom#UNKNOWN_BYTES}
     * @return the upload
     * @throws RetryLaterException when the service is stopping, or {@link #UPLOAD_LIMIT} uploads are open already
     * @throws CharterRefusal {@code PACK_STORAGE_FULL} when the volumes have no room left for it
     */
    public Upload openUpload(long declaredBytes) {
        requireOpen();
        long bytes = declaredBytes < 0 || declaredBytes > StorageRoom.ARCHIVE_BOUND_BYTES
                ? StorageRoom.ARCHIVE_BOUND_BYTES
                : declaredBytes;
        String id = Ulid.next(clock.instant(), random);
        synchronized (uploads) {
            if (uploads.size() >= UPLOAD_LIMIT) {
                throw uploadsFull();
            }
            long open = uploads.values().stream().mapToLong(Long::longValue).sum();
            dependencies.room().require(declaredBytes, open);
            Path directory;
            try {
                directory = dependencies.staging().createJobDirectory(id);
            } catch (IOException failure) {
                throw new UncheckedIOException("cannot create the staging directory of an upload", failure);
            }
            uploads.put(directory, bytes);
            return new OpenUpload(id, directory);
        }
    }

    private void requireOpen() {
        if (closing) {
            throw new RetryLaterException(STOPPING_DETAIL, RETRY_AFTER_SECONDS);
        }
    }

    private static RetryLaterException queueFull() {
        return new RetryLaterException(
                "Two registrations run and " + WAITING_LIMIT + " wait; send this one again shortly.",
                RETRY_AFTER_SECONDS);
    }

    private List<Path> openUploads() {
        synchronized (uploads) {
            return List.copyOf(uploads.keySet());
        }
    }

    private boolean forget(Path directory) {
        synchronized (uploads) {
            return uploads.remove(directory) != null;
        }
    }

    private static RetryLaterException uploadsFull() {
        return new RetryLaterException(
                UPLOAD_LIMIT + " uploads are open already; send this one again shortly.", RETRY_AFTER_SECONDS);
    }

    /** An upload in progress. */
    private final class OpenUpload implements Upload {
        private final String id;
        private final Path directory;
        private final AtomicBoolean adopted = new AtomicBoolean();

        OpenUpload(String id, Path directory) {
            this.id = id;
            this.directory = directory;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Path directory() {
            return directory;
        }

        /** Takes the upload for one reservation: {@code false} when one holds it already. */
        boolean claim() {
            return adopted.compareAndSet(false, true);
        }

        /** Gives the upload back, when the reservation that claimed it was refused. */
        void unclaim() {
            adopted.set(false);
        }

        /** The reservation holds the directory now: the upload's place is free. */
        void adopt() {
            forget(directory);
        }

        @Override
        public void discard() {
            if (adopted.get() || !forget(directory)) {
                return;
            }
            dependencies.staging().delete(directory);
        }
    }

    /** Seconds a refused client waits: a registration takes a few seconds, and a slot frees as soon as one ends. */
    private static final int RETRY_AFTER_SECONDS = 5;

    private Optional<PackRevision> storedRevision(String packId) {
        try {
            return Optional.of(dependencies.revisions().currentRevision(new PackIdentifier(packId)));
        } catch (CharterRefusal absent) {
            if (absent.code() == RefusalCode.PACK_NOT_FOUND) {
                return Optional.empty();
            }
            throw absent;
        }
    }

    private void admit(Job job) {
        synchronized (jobs) {
            evict(clock.instant(), true);
            jobs.put(job.id, job);
        }
    }

    /** Forgets the statuses an hour old and, to make room, the oldest ended ones. Called with {@code jobs} held. */
    private void evict(Instant now, boolean makeRoom) {
        jobs.values().removeIf(job -> job.expired(now));
        if (makeRoom) {
            Iterator<Job> oldest = jobs.values().iterator();
            while (jobs.size() >= MAX_RETAINED && oldest.hasNext()) {
                if (oldest.next().ended()) {
                    oldest.remove();
                }
            }
        }
    }

    /**
     * The status of a registration.
     *
     * @param id the registration id
     * @return the status; empty when the id is unknown, ended more than an hour ago or was evicted
     */
    public Optional<Status> status(String id) {
        synchronized (jobs) {
            Job job = jobs.get(id);
            if (job == null) {
                return Optional.empty();
            }
            if (job.expired(clock.instant())) {
                jobs.remove(id);
                return Optional.empty();
            }
            return Optional.of(job.status());
        }
    }

    /** Ends the jobs that passed the deadline and have not begun their commit; the scheduler calls it, a test calls it. */
    public void expireOverdue() {
        Instant now = clock.instant();
        List<Job> snapshot;
        synchronized (jobs) {
            evict(now, false);
            snapshot = List.copyOf(jobs.values());
        }
        for (Job job : snapshot) {
            if (!now.isBefore(job.acceptedAt.plus(DEADLINE))) {
                job.expire();
            }
        }
    }

    /**
     * The staging directories of jobs that have not ended, which the sweep never deletes.
     *
     * @return the directories
     */
    public Set<Path> owned() {
        synchronized (jobs) {
            Set<Path> owned = new LinkedHashSet<>(openUploads());
            jobs.values().stream().filter(job -> !job.ended()).forEach(job -> owned.add(job.directory));
            return owned;
        }
    }

    /**
     * The statuses held now.
     *
     * @return how many
     */
    public int retained() {
        synchronized (jobs) {
            return jobs.size();
        }
    }

    /**
     * Stops taking registrations, ends what has not begun its commit {@code refused} ({@code 503}: send it again), lets a
     * commit that has begun finish, and removes the staging directories. A job the process never got to end is forgotten
     * with it: its status URI answers {@code 404} after a restart and the client sends the pack again, which the version
     * rule makes idempotent.
     */
    @Override
    public void close() {
        closing = true;
        ScheduledExecutorService scheduler = watchdog;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        ScheduledFuture<?> runs = watchdogRuns;
        if (runs != null) {
            runs.cancel(false);
        }
        List<Job> snapshot;
        synchronized (jobs) {
            snapshot = List.copyOf(jobs.values());
        }
        snapshot.forEach(Job::stop);
        openUploads().forEach(directory -> {
            if (forget(directory)) {
                dependencies.staging().delete(directory);
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---- one job -------------------------------------------------------------------------------------------------

    /** Where a job is in its life, which only moves forward. */
    private enum Phase {
        RESERVED,
        QUEUED,
        RUNNING,
        COMMITTING,
        ENDED
    }

    /** Raised inside a job that has ended while its worker still ran: it must write nothing. */
    private static final class JobEnded extends RuntimeException {
        private static final long serialVersionUID = 1L;

        JobEnded() {
            super("The registration ended before its commit.", null, false, false);
        }
    }

    /** One registration. Every change of its phase takes the job's monitor, so one thread wins the end. */
    private static final class Job implements Reservation {
        private final RegistrationJobs owner;
        private final String id;
        private final String packId;
        private final Preconditions preconditions;
        private final Origin origin;
        private final String revisionBefore;
        private final long archiveBytes;
        private final Instant acceptedAt;
        private final Path directory;
        private Phase phase = Phase.RESERVED;
        private @Nullable Instant finishedAt;
        private @Nullable Ending ending;
        private final AtomicBoolean slotHeld = new AtomicBoolean(true);
        private @Nullable Future<?> task;
        private @Nullable Thread worker;
        private @Nullable Stager stager;

        Job(
                RegistrationJobs owner,
                String packId,
                Preconditions preconditions,
                Origin origin,
                Optional<PackRevision> stored,
                @Nullable Upload upload,
                long archiveBytes) {
            this.owner = owner;
            this.acceptedAt = owner.clock.instant();
            this.archiveBytes = archiveBytes;
            this.id = upload != null ? upload.id() : Ulid.next(acceptedAt, owner.random);
            this.packId = packId;
            this.preconditions = preconditions;
            this.origin = origin;
            this.revisionBefore = stored.map(PackRevision::token).orElse("");
            if (upload != null) {
                this.directory = upload.directory();
            } else {
                try {
                    this.directory = owner.dependencies.staging().createJobDirectory(id);
                } catch (IOException failure) {
                    throw new UncheckedIOException("cannot create the staging directory of a registration", failure);
                }
            }
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Path directory() {
            return directory;
        }

        @Override
        public String revisionBefore() {
            return revisionBefore;
        }

        @Override
        public synchronized Status status() {
            State state = switch (phase) {
                case RESERVED, QUEUED -> State.PENDING;
                case RUNNING, COMMITTING -> State.RUNNING;
                case ENDED -> ending instanceof Succeeded ? State.SUCCEEDED : State.REFUSED;
            };
            return new Status(id, packId, state, acceptedAt, finishedAt, ending);
        }

        synchronized boolean ended() {
            return phase == Phase.ENDED;
        }

        synchronized boolean expired(Instant now) {
            return phase == Phase.ENDED && finishedAt != null && !now.isBefore(finishedAt.plus(RETENTION));
        }

        @Override
        public boolean start(Stager work) {
            synchronized (this) {
                if (phase == Phase.ENDED && stager == null) {
                    // The deadline or the shutdown ended the job while it was reserved: its end stands, and the
                    // request that reserved it answers with that status instead of failing (#418, A4).
                    return false;
                }
                if (phase != Phase.RESERVED) {
                    throw new IllegalStateException("The registration was started or ended already.");
                }
                phase = Phase.QUEUED;
                stager = Objects.requireNonNull(work, "stager");
            }
            try {
                task = owner.executor.submit(this::run);
            } catch (RejectedExecutionException stopped) {
                end(new Refused(Kind.BUSY, null, STOPPING_DETAIL), false);
            }
            return true;
        }

        @Override
        public void abandon() {
            boolean released;
            synchronized (this) {
                released = phase == Phase.RESERVED;
                if (released) {
                    phase = Phase.ENDED;
                    finishedAt = owner.clock.instant();
                }
            }
            if (released) {
                releaseSlot();
                deleteDirectory();
                synchronized (owner.jobs) {
                    owner.jobs.remove(id);
                }
            }
        }

        /**
         * The worker's whole run of the job. The staging directory is removed before the end is published, so a client
         * that reads a finished status finds nothing of the job on disk.
         */
        private void run() {
            Ending result = null;
            try {
                result = attempt();
            } finally {
                deleteDirectory();
                if (result != null) {
                    end(result, false);
                }
                clearWorker();
                releaseSlot();
            }
        }

        private @Nullable Ending attempt() {
            try {
                if (ended()) {
                    return null;
                }
                owner.dependencies.gate().beforeRun(id);
                if (!markRunning()) {
                    return null;
                }
                owner.dependencies.gate().whileRunning(id);
                return work();
            } catch (JobEnded gone) {
                // The deadline or the shutdown ended the job first: its answer stands, and nothing was written.
                return null;
            } catch (RuntimeException | Error failure) {
                LOG.log(System.Logger.Level.WARNING, "A registration job failed unexpectedly.", failure);
                return new Refused(Kind.FAILED, null, INTERNAL_DETAIL);
            }
        }

        /**
         * Gives the admission slot back, once. A job ended while its worker is still on an executor thread keeps the
         * slot until the worker leaves {@code run()}, so the executor never holds more than two running and eight
         * waiting however late a stager answers.
         */
        private void releaseSlot() {
            if (slotHeld.compareAndSet(true, false)) {
                owner.inFlight.decrementAndGet();
            }
        }

        private synchronized void clearWorker() {
            worker = null;
        }

        private synchronized boolean markRunning() {
            if (phase != Phase.QUEUED) {
                return false;
            }
            phase = Phase.RUNNING;
            worker = Thread.currentThread();
            return true;
        }

        /** The decisive step passed: from here the deadline no longer ends the job. */
        private synchronized void beginCommit() {
            if (phase != Phase.RUNNING) {
                throw new JobEnded();
            }
            phase = Phase.COMMITTING;
        }

        private @Nullable Ending work() {
            try {
                owner.dependencies.room().requireForRun(archiveBytes);
                Staged staged = Objects.requireNonNull(stager).stage(directory);
                RegistrationOutcome outcome =
                        owner.dependencies.service().register(packId, staged.pack(), preconditions, this::beginCommit);
                return new Succeeded(outcome, staged.source());
            } catch (CharterRefusal refusal) {
                return new Refused(Kind.CODED, refusal, detail(refusal));
            } catch (PreconditionException stale) {
                return new Refused(Kind.STALE, null, detail(stale));
            } catch (RetryLaterException busy) {
                return new Refused(Kind.BUSY, null, detail(busy));
            } catch (SourceUpstreamException upstream) {
                return new Refused(
                        upstream.timedOut() ? Kind.UPSTREAM_TIMEOUT : Kind.UPSTREAM_FAILED, null, detail(upstream));
            } catch (JobEnded gone) {
                throw gone;
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (ended()) {
                    return null;
                }
                LOG.log(System.Logger.Level.WARNING, "A registration job failed unexpectedly.", failure);
                return new Refused(Kind.FAILED, null, INTERNAL_DETAIL);
            }
        }

        private static String detail(Throwable failure) {
            return Objects.requireNonNullElse(failure.getMessage(), "");
        }

        /** The deadline: ends the job unless its commit began. A worker still staging is interrupted. */
        void expire() {
            Refused timedOut = new Refused(
                    Kind.CODED,
                    new CharterRefusal(
                            RefusalCode.REGISTRATION_TIMED_OUT,
                            "The registration did not finish within " + DEADLINE.toSeconds() + " seconds."),
                    "The registration did not finish within " + DEADLINE.toSeconds() + " seconds.");
            endUnlessCommitting(timedOut);
        }

        /** The shutdown: ends the job unless its commit began, which is let to finish. */
        void stop() {
            endUnlessCommitting(new Refused(Kind.BUSY, null, STOPPING_DETAIL));
        }

        private void endUnlessCommitting(Refused refusal) {
            if (!end(refusal, true)) {
                return;
            }
            Future<?> queued = task;
            if (queued == null) {
                return;
            }
            queued.cancel(false);
            if (queued instanceof Runnable waiting) {
                owner.executor.remove(waiting);
            }
        }

        /**
         * Ends the job with {@code result} if it has not ended: the one end, whoever reaches it, and the one audit entry
         * of the attempt. A job whose worker is not running owns its directory no longer.
         *
         * @param result how the job ends
         * @param unlessCommitting whether a job that began its commit is left alone, which the check makes in the same
         *     critical section as the end, so a commit that begins meanwhile is never overwritten
         * @return whether this call ended it
         */
        private boolean end(Ending result, boolean unlessCommitting) {
            Instant now = owner.clock.instant();
            boolean idle;
            Thread active;
            synchronized (this) {
                if (phase == Phase.ENDED || (unlessCommitting && phase == Phase.COMMITTING)) {
                    return false;
                }
                active = worker;
                idle = active == null;
                phase = Phase.ENDED;
                finishedAt = now;
                ending = result;
                // Under the job's own lock, so a reader that sees the end sees the attempt's entry too.
                record(now, result);
            }
            if (unlessCommitting && active != null) {
                active.interrupt();
            }
            if (idle) {
                releaseSlot();
                deleteDirectory();
            }
            return true;
        }

        private void record(Instant now, Ending result) {
            try {
                owner.dependencies.audit().record(auditEntry(now, result));
            } catch (RuntimeException lost) {
                LOG.log(System.Logger.Level.ERROR, "The audit entry of a registration could not be written.", lost);
            }
        }

        private AuditLog.Entry auditEntry(Instant now, Ending result) {
            String after =
                    result instanceof Succeeded done ? done.outcome().revision().token() : revisionBefore;
            return new AuditLog.Entry(
                    now,
                    origin.worktree(),
                    origin.credential(),
                    origin.operation(),
                    packId,
                    origin.sourceKind(),
                    origin.locator(),
                    outcomeOf(result),
                    revisionBefore,
                    after);
        }

        private static String outcomeOf(Ending result) {
            if (result instanceof Succeeded done) {
                return "succeeded:" + done.outcome().outcome().name().toLowerCase(Locale.ROOT);
            }
            Refused refused = (Refused) result;
            return switch (refused.kind()) {
                case STALE -> "refused:precondition_failed";
                case BUSY -> "refused:registry_busy";
                case FAILED -> "failed:internal_error";
                case UPSTREAM_FAILED -> "refused:upstream_failed";
                case UPSTREAM_TIMEOUT -> "refused:upstream_timeout";
                case CODED ->
                    "refused:"
                            + Objects.requireNonNull(refused.refusal()).code().token();
            };
        }

        private void deleteDirectory() {
            owner.dependencies.staging().delete(directory);
        }
    }
}
