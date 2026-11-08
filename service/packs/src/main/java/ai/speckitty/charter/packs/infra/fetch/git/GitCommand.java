package ai.speckitty.charter.packs.infra.fetch.git;

import ai.speckitty.charter.packs.infra.fetch.git.GitFailure.Kind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Runs the system git for one fetch (T089, C-008, plan.md "The system git binary"). Every run is a {@link ProcessBuilder}
 * over a fixed argument list, with no shell and no command string, and nothing the service's own process holds:
 *
 * <ul>
 *   <li><b>Arguments.</b> {@code git}, then the {@link #PINS} each as {@code -c key=value}, then the transport's pins,
 *       then the caller's arguments. The pins turn off every transport but https, redirects, submodules, hooks, links
 *       in a checkout, the file-system monitor and the LFS filter, make git check every object it fetches, and bound
 *       the memory it inflates and maps (big-file threshold, pack window and window memory, mapped-pack limit, delta
 *       base cache, one thread).
 *   <li><b>Environment.</b> Built from empty ({@link GitEnvironment}), then the transport's variables: no proxy, no
 *       credential helper, no askpass, no {@code GIT_*} override and no {@code HOME} of the service.
 *   <li><b>One deadline.</b> Fixed when the command is built and shared by every run of the fetch, so the whole fetch is
 *       held to {@code charter.pack-sources.git.timeout}. When it passes the process and every descendant are destroyed
 *       forcibly and the run fails {@link Kind#DEADLINE}; a run started after it is never started.
 *   <li><b>A size watch.</b> A caller may give a test of "the fetch is too big", polled while git runs; a {@code true}
 *       destroys the tree and fails the run {@link Kind#TOO_LARGE}.
 *   <li><b>Output drained and capped.</b> stderr is drained on a thread of its own and only its first
 *       {@value #STDERR_CAP_BYTES} bytes are kept; captured stdout is held to the cap the caller names, and a longer one
 *       destroys the tree and fails {@link Kind#OUTPUT_TOO_LARGE}.
 * </ul>
 */
final class GitCommand {

    /** The {@code -c} pins of every run, in order. */
    static final List<String> PINS = List.of(
            "protocol.allow=never",
            "protocol.https.allow=always",
            "http.followRedirects=false",
            "submodule.recurse=false",
            "core.hooksPath=/dev/null",
            "core.symlinks=false",
            "core.fsmonitor=false",
            "transfer.fsckObjects=true",
            // Memory bounds (A5): git inflates and delta-resolves what it fetches in its own address space, so the
            // working set is bounded here, not by the 32 MiB source bound alone. A blob over bigFileThreshold is
            // streamed, never inflated whole; the pack window, the mapped-pack total and the delta base cache are
            // each a fraction of the bound, and one thread resolves deltas. Slower on a pack near the bound, never
            // refused for it. Address space (mmap) is not capped (DIV-613).
            "core.bigFileThreshold=1m",
            "pack.windowMemory=16m",
            "pack.threads=1",
            "core.packedGitLimit=64m",
            "core.packedGitWindowSize=16m",
            "core.deltaBaseCacheLimit=16m",
            "filter.lfs.clean=",
            "filter.lfs.smudge=",
            "filter.lfs.process=",
            "filter.lfs.required=false");

    private static final int STDERR_CAP_BYTES = 8 * 1024;
    private static final long WATCH_TICK_MILLIS = 25;
    private static final long DRAIN_JOIN_MILLIS = 2000;

    private final Path executable;
    private final Path home;
    private final long deadlineNanos;
    private final GitTransport transport;
    private final BooleanSupplier overLimit;

    /**
     * A command for one fetch.
     *
     * @param executable the git binary
     * @param home a directory of the fetch's own: {@code HOME}, {@code XDG_CONFIG_HOME} and the working directory
     * @param timeout the deadline over every run of the fetch, from now
     * @param transport what the remote adds
     */
    GitCommand(Path executable, Path home, Duration timeout, GitTransport transport) {
        this(executable, home, System.nanoTime() + timeout.toNanos(), transport, () -> false);
    }

    private GitCommand(
            Path executable, Path home, long deadlineNanos, GitTransport transport, BooleanSupplier overLimit) {
        this.executable = executable;
        this.home = home;
        this.deadlineNanos = deadlineNanos;
        this.transport = transport;
        this.overLimit = overLimit;
    }

    /**
     * The same command with a size watch.
     *
     * @param tooBig whether the fetch is over its bound; polled while git runs, and read from the watchdog's thread
     * @return the command
     */
    GitCommand watching(BooleanSupplier tooBig) {
        return new GitCommand(executable, home, deadlineNanos, transport, tooBig);
    }

    /**
     * Runs git and returns what it printed.
     *
     * @param arguments the arguments after the pins; the caller has validated every one
     * @param maxOutputBytes the most stdout taken
     * @return stdout
     * @throws GitFailure when the run does not end with status zero within the deadline and the bounds
     */
    byte[] run(List<String> arguments, int maxOutputBytes) {
        try (Running running = stream(arguments, null)) {
            byte[] output = running.read(maxOutputBytes);
            running.finish();
            return output;
        }
    }

    /**
     * Starts git, for output that is read as it is written.
     *
     * @param arguments the arguments after the pins
     * @param stdin a file git reads as its standard input, or null for none
     * @return the running git; closing it destroys what is still running
     * @throws GitFailure when the deadline has passed or git cannot be started
     */
    Running stream(List<String> arguments, @Nullable Path stdin) {
        if (System.nanoTime() - deadlineNanos >= 0) {
            throw new GitFailure(Kind.DEADLINE, "");
        }
        ProcessBuilder builder = new ProcessBuilder(argumentList(arguments));
        builder.environment().clear();
        builder.environment().putAll(GitEnvironment.from(executable, home));
        builder.environment().putAll(transport.environment());
        builder.directory(home.toFile());
        if (stdin != null) {
            builder.redirectInput(ProcessBuilder.Redirect.from(stdin.toFile()));
        }
        try {
            Process process = builder.start();
            if (stdin == null) {
                process.getOutputStream().close();
            }
            return new Running(process, deadlineNanos, overLimit);
        } catch (IOException unstartable) {
            throw new GitFailure(Kind.START, "");
        }
    }

    private List<String> argumentList(List<String> arguments) {
        List<String> argv = new ArrayList<>();
        argv.add(executable.toString());
        for (String pin : PINS) {
            argv.add("-c");
            argv.add(pin);
        }
        for (String pin : transport.pins()) {
            argv.add("-c");
            argv.add(pin);
        }
        argv.addAll(arguments);
        return argv;
    }

    /** A running git: its stdout, the stderr kept apart, and the watch that ends it at the deadline or the bound. */
    static final class Running implements AutoCloseable {

        private final Process process;
        private final AtomicReference<@Nullable Kind> killedFor = new AtomicReference<>();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private final Thread drain;
        private final Thread watchdog;

        private Running(Process process, long deadlineNanos, BooleanSupplier overLimit) {
            this.process = process;
            this.drain = Thread.ofPlatform().daemon().name("git-stderr").start(this::drainStderr);
            this.watchdog =
                    Thread.ofPlatform().daemon().name("git-watchdog").start(() -> watch(deadlineNanos, overLimit));
        }

        /**
         * What git writes, as it writes it.
         *
         * @return the standard output; ends when git does or is destroyed
         */
        InputStream stdout() {
            return process.getInputStream();
        }

        /**
         * What git has written to stderr so far, capped; for classification and the operator's log only.
         *
         * @return the text
         */
        String stderr() {
            synchronized (stderr) {
                return stderr.toString(StandardCharsets.UTF_8);
            }
        }

        /** Reads stdout to its end, or fails when it is longer than {@code max} bytes. */
        byte[] read(int max) {
            try (InputStream out = process.getInputStream()) {
                byte[] output = out.readNBytes(max + 1);
                if (output.length > max) {
                    kill(Kind.OUTPUT_TOO_LARGE);
                    throw new GitFailure(Kind.OUTPUT_TOO_LARGE, stderr());
                }
                return output;
            } catch (IOException unreadable) {
                // A destroyed process closes its output; finish says why.
                finish();
                throw new GitFailure(Kind.EXIT, stderr());
            }
        }

        /**
         * Says why git was destroyed, when it was: after a read of its output failed, the failure is the deadline's or the
         * size watch's and not the output's.
         *
         * @throws GitFailure when the watch destroyed git at the deadline or for size, or the thread was interrupted
         */
        void rethrowIfDestroyed() {
            Kind why = killedFor.get();
            if (why != null) {
                joinDrain();
                throw new GitFailure(why, stderr());
            }
        }

        /**
         * Waits for git to end.
         *
         * @throws GitFailure when git was destroyed at the deadline or for size, or ended with a status other than zero
         */
        void finish() {
            try {
                process.waitFor();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                kill(Kind.INTERRUPTED);
                throw new GitFailure(Kind.INTERRUPTED, stderr());
            }
            joinDrain();
            Kind why = killedFor.get();
            if (why != null) {
                throw new GitFailure(why, stderr());
            }
            int status = process.exitValue();
            if (status != 0) {
                throw new GitFailure(Kind.EXIT, status, stderr());
            }
        }

        @Override
        public void close() {
            watchdog.interrupt();
            if (process.isAlive()) {
                destroyTree();
            }
            joinDrain();
        }

        private void kill(Kind why) {
            killedFor.compareAndSet(null, why);
            destroyTree();
        }

        private void destroyTree() {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }

        private void watch(long deadlineNanos, BooleanSupplier overLimit) {
            try {
                while (process.isAlive()) {
                    if (System.nanoTime() - deadlineNanos >= 0) {
                        kill(Kind.DEADLINE);
                        return;
                    }
                    if (isOver(overLimit)) {
                        kill(Kind.TOO_LARGE);
                        return;
                    }
                    Thread.sleep(WATCH_TICK_MILLIS);
                }
            } catch (InterruptedException closed) {
                Thread.currentThread().interrupt();
            }
        }

        private static boolean isOver(BooleanSupplier overLimit) {
            try {
                return overLimit.getAsBoolean();
            } catch (RuntimeException unreadable) {
                return false;
            }
        }

        private void drainStderr() {
            byte[] buffer = new byte[4096];
            try (InputStream err = process.getErrorStream()) {
                for (int read = err.read(buffer); read >= 0; read = err.read(buffer)) {
                    keep(buffer, read);
                }
            } catch (IOException closed) {
                // The process ended or was destroyed; what was read stands.
            }
        }

        private void keep(byte[] buffer, int length) {
            synchronized (stderr) {
                stderr.write(buffer, 0, Math.min(length, Math.max(0, STDERR_CAP_BYTES - stderr.size())));
            }
        }

        private void joinDrain() {
            try {
                drain.join(DRAIN_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
