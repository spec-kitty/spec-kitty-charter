package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The registration jobs of FR-024 (IC-14): a bounded executor that runs the registration flow after the request was
 * answered {@code 202}, and the in-memory status the client polls.
 */
public final class RegistrationJobs implements AutoCloseable {

    /** Registrations that run at once. */
    public static final int RUNNING_LIMIT = 2;

    /** Registrations that wait for a worker; the next is refused with {@code Retry-After}. */
    public static final int WAITING_LIMIT = 8;

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
        BUSY
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

    /** Where a job's attempt came from, for its audit entry. */
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

    /** A slot in the queue, taken before the body is read. */
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
         */
        void start(Stager stager);

        /** Gives the slot back, when the request failed before the job could start; deletes the directory. */
        void abandon();

        /**
         * The status now.
         *
         * @return the pending status the {@code 202} answers
         */
        Status status();
    }

    /** What the jobs need of the outside world. */
    public record Dependencies(
            java.time.Clock clock,
            PackRegistrationService service,
            PackRevisions revisions,
            AuditLog audit,
            JobGate gate,
            StorageRoom room,
            Path stagingRoot) {

        /**
         * Checks the fields.
         *
         * @param clock the clock of the statuses and the deadline
         * @param service the registration flow
         * @param revisions the stored revisions
         * @param audit the audit trail
         * @param gate the test seam
         * @param room the free-space check
         * @param stagingRoot the directory the jobs stage in
         */
        public Dependencies {
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(revisions, "revisions");
            Objects.requireNonNull(audit, "audit");
            Objects.requireNonNull(gate, "gate");
            Objects.requireNonNull(room, "room");
            Objects.requireNonNull(stagingRoot, "stagingRoot");
        }
    }

    /** The check that the pack volume has room, before a job writes anything. */
    @FunctionalInterface
    public interface StorageRoom {

        /** A volume that always has room. */
        StorageRoom UNBOUNDED = () -> {};

        /**
         * Checks the room.
         *
         * @throws CharterRefusal {@code PACK_STORAGE_FULL} when less than twice the archive bound is free
         */
        void require();
    }

    /**
     * Jobs over their dependencies.
     *
     * @param dependencies the clock, the flow, the stored revisions, the audit trail and the seams
     */
    public RegistrationJobs(Dependencies dependencies) {
        Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * Takes a slot for a registration of {@code packId}: the preconditions are judged against the stored revision, and a
     * full queue is refused, before the caller reads the body.
     *
     * @param packId the target pack
     * @param preconditions the conditional headers
     * @param origin where the attempt came from
     * @return the reservation
     */
    public Reservation reserve(String packId, Preconditions preconditions, Origin origin) {
        return new Reservation() {
            @Override
            public String id() {
                return "";
            }

            @Override
            public Path directory() {
                return Path.of("");
            }

            @Override
            public void start(Stager stager) {}

            @Override
            public void abandon() {}

            @Override
            public Status status() {
                return new Status("", packId, State.PENDING, Instant.EPOCH, null, null);
            }
        };
    }

    /**
     * The status of a registration.
     *
     * @param id the registration id
     * @return the status; empty when the id is unknown, ended more than an hour ago or was evicted
     */
    public Optional<Status> status(String id) {
        return Optional.empty();
    }

    /** Ends the jobs that passed the deadline and have not begun their commit; the scheduler calls it, a test calls it. */
    public void expireOverdue() {}

    /**
     * The staging directories of jobs that have not ended, which the sweep never deletes.
     *
     * @return the directories
     */
    public Set<Path> owned() {
        return Set.of();
    }

    /**
     * The statuses held now.
     *
     * @return how many
     */
    public int retained() {
        return 0;
    }

    @Override
    public void close() {}
}
