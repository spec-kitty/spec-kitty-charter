package ai.speckitty.charter.packs.infra.fetch.git;

import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Whether the host has a git the source may use (NFR-011); resolved once. */
public final class GitVersionCheck {

    /** The oldest git the source runs (C-008, NFR-011). */
    public static final GitVersion MINIMUM = new GitVersion(2, 50, 1);

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

    private GitVersionCheck() {}

    /**
     * Resolves git and checks its version against {@link #MINIMUM}.
     *
     * @param executable the configured absolute path of git, or null to look on the path
     * @param path the {@code PATH} to look on
     * @return the check
     */
    public static GitVersionCheck resolve(@Nullable Path executable, @Nullable String path) {
        return resolve(executable, path, MINIMUM);
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
        return new GitVersionCheck();
    }

    /**
     * What was found.
     *
     * @return the status
     */
    public Status status() {
        return Status.MISSING;
    }

    /**
     * Whether the git source is on.
     *
     * @return true when the status is {@link Status#ENABLED}
     */
    public boolean enabled() {
        return false;
    }

    /**
     * The git to run.
     *
     * @return the absolute path of the binary
     * @throws IllegalStateException when the source is not enabled
     */
    public Path executable() {
        if (!enabled()) {
            throw new IllegalStateException("the git source is not enabled");
        }
        return Path.of("/");
    }

    /**
     * The version found.
     *
     * @return the version; empty when none was read
     */
    public Optional<GitVersion> version() {
        return Optional.empty();
    }

    /**
     * A line for the operator's log: what was found and what the source does about it.
     *
     * @return the line
     */
    public String describe() {
        return "";
    }
}
