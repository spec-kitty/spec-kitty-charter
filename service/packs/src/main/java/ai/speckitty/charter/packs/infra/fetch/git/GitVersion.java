package ai.speckitty.charter.packs.infra.fetch.git;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A git version, the numbers of {@code git --version}.
 *
 * @param major the major number
 * @param minor the minor number
 * @param patch the patch number, zero when the output names none
 */
public record GitVersion(int major, int minor, int patch) implements Comparable<GitVersion> {

    private static final Pattern OUTPUT = Pattern.compile("^git version (\\d{1,6})\\.(\\d{1,6})(?:\\.(\\d{1,6}))?(?!\\d)");
    private static final Comparator<GitVersion> ORDER =
            Comparator.comparingInt(GitVersion::major).thenComparingInt(GitVersion::minor).thenComparingInt(GitVersion::patch);

    /**
     * Reads the version out of the output of {@code git --version}: the first three numbers after {@code git version},
     * whatever follows them (a vendor suffix, a release candidate).
     *
     * @param output what git printed
     * @return the version; empty when the output is not {@code git version X.Y[.Z]...}
     */
    public static Optional<GitVersion> parse(String output) {
        Matcher matcher = OUTPUT.matcher(output.stripLeading());
        if (!matcher.find()) {
            return Optional.empty();
        }
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return Optional.of(new GitVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), patch));
    }

    @Override
    public int compareTo(GitVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
