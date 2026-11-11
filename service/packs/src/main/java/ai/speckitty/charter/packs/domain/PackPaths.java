package ai.speckitty.charter.packs.domain;

import java.util.Arrays;
import java.util.List;

/**
 * The lexical rule for a pack-relative path that leaves its root: an asset path its {@code assets/} root (validate mode's
 * {@code ASSET_PATH_ESCAPE}, the load's sidecar rule, #382) and a fragment node's {@code body_path} the pack root (#254).
 */
public final class PackPaths {

    private PackPaths() {}

    /**
     * Whether a path, read as relative to some root, leaves it as written. Core's
     * {@code resolve_relative_path_within_root} judges the path as both a {@code PurePosixPath} and a
     * {@code PureWindowsPath}: absolute in either flavour, or a {@code ..} part in either, escapes. The adapter's
     * containment check cannot see the Windows forms on Linux, where {@code \} and {@code C:} are ordinary name
     * characters, so the domain reads them itself.
     *
     * @param path the path as written
     * @return {@code true} when it is absolute in either flavour or has a {@code ..} part
     */
    public static boolean leavesRoot(String path) {
        return isAbsolute(path) || hasDotDotPart(path);
    }

    /**
     * POSIX absolute (a leading {@code /}), or Windows absolute: a UNC prefix of two separators of either kind, or a
     * drive (any one character but a separator, and a colon) followed by a separator. {@code C:x} and a single leading {@code \} are not absolute.
     */
    private static boolean isAbsolute(String path) {
        return path.startsWith("/") || hasUncPrefix(path) || hasDriveRoot(path) || hasBackslashColonRoot(path);
    }

    /**
     * A quirk of Python 3.13's {@code ntpath.isabs}, which reads the normalized text: {@code \:\x} counts as a
     * drive-and-root form ({@code \} as the "drive" character) once a real segment follows; {@code \:\} alone,
     * or {@code \:\.}, does not.
     */
    private static boolean hasBackslashColonRoot(String path) {
        return path.startsWith("\\:")
                && path.length() > 2
                && isSeparator(path.charAt(2))
                && Arrays.stream(path.substring(3).split("[/\\\\]", -1)).anyMatch(s -> !s.isEmpty() && !".".equals(s));
    }

    private static boolean hasUncPrefix(String path) {
        return path.length() >= 2 && isSeparator(path.charAt(0)) && isSeparator(path.charAt(1));
    }

    private static boolean hasDriveRoot(String path) {
        int afterDrive = driveLength(path);
        return afterDrive > 0 && afterDrive < path.length() && isSeparator(path.charAt(afterDrive));
    }

    /** The length of a leading {@code <char>:} drive, or 0. */
    private static int driveLength(String path) {
        if (path.isEmpty()) {
            return 0;
        }
        int first = path.codePointAt(0);
        int width = Character.charCount(first);
        return !isSeparator(first) && width < path.length() && path.charAt(width) == ':' ? width + 1 : 0;
    }

    /** A {@code ..} between separators of either flavour, with a Windows drive taken off first. */
    private static boolean hasDotDotPart(String path) {
        String rest = path.substring(driveLength(path));
        return List.of(rest.split("[/\\\\]", -1)).contains("..");
    }

    private static boolean isSeparator(int c) {
        return c == '/' || c == '\\';
    }
}
