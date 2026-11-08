package ai.speckitty.charter.packs.infra.fetch.git;

import java.io.Serial;

/**
 * A git run that did not end well. The message is fixed and carries nothing git printed; the stderr git wrote, which the
 * fetcher classifies and logs at DEBUG with the URL's path cut, is a separate member that never reaches a response
 * (NFR-012).
 */
final class GitFailure extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why the run failed. */
    enum Kind {
        /** The deadline over the whole fetch passed; the process tree was destroyed. */
        DEADLINE,
        /** The size watch found the fetch over its bound; the process tree was destroyed. */
        TOO_LARGE,
        /** The output was longer than the caller allows; the process tree was destroyed. */
        OUTPUT_TOO_LARGE,
        /** git ended with a status other than zero. */
        EXIT,
        /** The process could not be started. */
        START,
        /** The thread waiting for git was interrupted; the process tree was destroyed. */
        INTERRUPTED
    }

    private final Kind kind;
    private final int exitStatus;
    private final String stderr;

    GitFailure(Kind kind, int exitStatus, String stderr) {
        super("git failed: " + kind.name().toLowerCase(java.util.Locale.ROOT));
        this.kind = kind;
        this.exitStatus = exitStatus;
        this.stderr = stderr;
    }

    GitFailure(Kind kind, String stderr) {
        this(kind, -1, stderr);
    }

    Kind kind() {
        return kind;
    }

    int exitStatus() {
        return exitStatus;
    }

    /** What git wrote to stderr, capped; for classification and the operator's log only. */
    String stderr() {
        return stderr;
    }
}
