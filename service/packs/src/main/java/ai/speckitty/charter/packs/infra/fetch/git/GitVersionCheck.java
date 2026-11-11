package ai.speckitty.charter.packs.infra.fetch.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Whether the host has a git the source may use (NFR-011, C-008): the binary is resolved once, from
 * {@code charter.pack-sources.git.executable} or the {@code PATH}, and asked {@code --version}. Below 2.50.1, without a
 * binary, or with one that does not answer with a version, the git source is off and answers
 * {@code 503 pack_source_unavailable}; nothing else changes, and the other kinds of source keep working.
 *
 * <p>The binary is started with an argument list that is fixed and an environment built from empty ({@link
 * GitEnvironment}), under a deadline, and only {@code --version} is asked of it.
 */
public final class GitVersionCheck {

    /** The oldest git the source runs (C-008, NFR-011). */
    public static final GitVersion MINIMUM_VERSION = new GitVersion(2, 50, 1);

    private static final String BINARY = "git";
    private static final long ASK_SECONDS = 10;
    private static final int MAX_ANSWER_BYTES = 4096;
    private static final String PROPERTY = "charter.pack-sources.git.executable";
    private static final String HOME_PREFIX = "charter-git-version-";
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    /** What the check found. */
    public enum Status {
        /** A git of the minimum version or later; the source is on. */
        ENABLED,
        /** No executable git was found. */
        MISSING,
        /** A git older than the minimum was found. */
        TOO_OLD,
        /** The binary did not answer {@code --version} with a version. */
        UNREADABLE
    }

    private final Status status;
    private final @Nullable Path executable;
    private final @Nullable GitVersion version;
    private final GitVersion minimum;

    private GitVersionCheck(
            Status status, @Nullable Path executable, @Nullable GitVersion version, GitVersion minimum) {
        this.status = status;
        this.executable = executable;
        this.version = version;
        this.minimum = minimum;
    }

    /**
     * Resolves git and checks its version against {@link #MINIMUM_VERSION}.
     *
     * @param executable the configured absolute path of git, or null to look on the path
     * @param path the {@code PATH} to look on
     * @return the check
     */
    public static GitVersionCheck resolve(@Nullable Path executable, @Nullable String path) {
        return resolve(executable, path, MINIMUM_VERSION);
    }

    /**
     * Resolves git and checks its version against a minimum.
     *
     * @param executable the configured absolute path of git, or null to look on the path
     * @param path the {@code PATH} to look on
     * @param minimum the oldest version accepted
     * @return the check
     */
    public static GitVersionCheck resolve(@Nullable Path executable, @Nullable String path, GitVersion minimum) {
        Objects.requireNonNull(minimum, "minimum");
        Optional<Path> found = executable != null ? usable(executable) : onPath(path);
        if (found.isEmpty()) {
            return new GitVersionCheck(Status.MISSING, null, null, minimum);
        }
        Path binary = found.get();
        Optional<GitVersion> version = ask(binary);
        if (version.isEmpty()) {
            return new GitVersionCheck(Status.UNREADABLE, binary, null, minimum);
        }
        Status status = version.get().compareTo(minimum) >= 0 ? Status.ENABLED : Status.TOO_OLD;
        return new GitVersionCheck(status, binary, version.get(), minimum);
    }

    private static Optional<Path> usable(Path candidate) {
        boolean runnable = candidate.isAbsolute() && Files.isRegularFile(candidate) && Files.isExecutable(candidate);
        return runnable ? Optional.of(candidate.normalize()) : Optional.empty();
    }

    private static Optional<Path> onPath(@Nullable String path) {
        if (path == null) {
            return Optional.empty();
        }
        for (String entry : path.split(java.io.File.pathSeparator, -1)) {
            Path directory = entry.isEmpty() ? null : Path.of(entry);
            if (directory != null && directory.isAbsolute()) {
                Optional<Path> found = usable(directory.resolve(BINARY));
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** The version the binary reports; empty when it does not start, does not answer in time or does not say one. */
    private static Optional<GitVersion> ask(Path binary) {
        Path home = null;
        try {
            home = privateHome();
            ProcessBuilder builder = new ProcessBuilder(List.of(binary.toString(), "--version"));
            builder.environment().clear();
            builder.environment().putAll(GitEnvironment.from(binary, home));
            builder.directory(home.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try {
                return answer(process);
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            remove(home);
        }
    }

    /**
     * The directory git runs in and takes as its {@code HOME}, made the owner's alone: in the shared temporary
     * directory, another user could otherwise plant a config or a hook for git to read, or read what git writes.
     * Where the file system has no POSIX modes this throws, and the check answers unreadable, like the git fetcher.
     */
    private static Path privateHome() throws IOException {
        return Files.createTempDirectory(HOME_PREFIX, OWNER_ONLY);
    }

    private static Optional<GitVersion> answer(Process process) throws IOException, InterruptedException {
        process.getOutputStream().close();
        byte[] output;
        try (InputStream stdout = process.getInputStream()) {
            // A binary that never ends is cut by the deadline, which closes its output.
            Thread watchdog = Thread.ofVirtual().start(() -> kill(process));
            try {
                output = stdout.readNBytes(MAX_ANSWER_BYTES);
            } finally {
                watchdog.interrupt();
            }
        }
        if (!process.waitFor(ASK_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
            return Optional.empty();
        }
        return GitVersion.parse(new String(output, StandardCharsets.UTF_8));
    }

    private static void kill(Process process) {
        try {
            if (!process.waitFor(ASK_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException done) {
            Thread.currentThread().interrupt();
        }
    }

    private static void remove(@Nullable Path directory) {
        if (directory == null) {
            return;
        }
        try (var entries = Files.walk(directory)) {
            for (Path entry :
                    entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException ignored) {
            // A temporary directory the operating system will clear.
        }
    }

    /**
     * What was found.
     *
     * @return the status
     */
    public Status status() {
        return status;
    }

    /**
     * Whether the git source is on.
     *
     * @return true when the status is {@link Status#ENABLED}
     */
    public boolean enabled() {
        return status == Status.ENABLED;
    }

    /**
     * The git to run.
     *
     * @return the absolute path of the binary
     * @throws IllegalStateException when the source is not enabled
     */
    public Path executable() {
        if (!enabled() || executable == null) {
            throw new IllegalStateException("the git source is not enabled");
        }
        return executable;
    }

    /**
     * The version found.
     *
     * @return the version; empty when none was read
     */
    public Optional<GitVersion> version() {
        return Optional.ofNullable(version);
    }

    /**
     * A line for the operator's log: what was found and what the source does about it.
     *
     * @return the line
     */
    public String describe() {
        String off = " The git source answers 503 pack_source_unavailable; the other kinds of source are not affected.";
        return switch (status) {
            case ENABLED -> "git " + version + " at " + executable + " is on: the git source is available.";
            case MISSING -> "No executable git was found on the PATH or at " + PROPERTY + "." + off;
            case TOO_OLD -> "git " + version + " at " + executable + " is older than " + minimum + "." + off;
            case UNREADABLE -> "The binary at " + executable + " did not answer --version with a git version." + off;
        };
    }
}
