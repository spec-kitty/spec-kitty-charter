package ai.speckitty.charter.packs.infra.fetch.git;

import java.util.Optional;

/**
 * A git version, the numbers of {@code git --version}.
 *
 * @param major the major number
 * @param minor the minor number
 * @param patch the patch number, zero when the output names none
 */
public record GitVersion(int major, int minor, int patch) implements Comparable<GitVersion> {

    /**
     * Reads the version out of the output of {@code git --version}.
     *
     * @param output what git printed
     * @return the version; empty when the output is not {@code git version X.Y[.Z]...}
     */
    public static Optional<GitVersion> parse(String output) {
        return Optional.empty();
    }

    @Override
    public int compareTo(GitVersion other) {
        return 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
