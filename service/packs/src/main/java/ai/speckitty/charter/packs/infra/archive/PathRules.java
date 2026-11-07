package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** The path rules every entry name must meet (IC-03, NFR-005), applied to a name as the library reports it. */
final class PathRules {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*", Pattern.DOTALL);

    private PathRules() {}

    /** A name's segments, or the rule it breaks. */
    record Parsed(List<String> segments, @Nullable Code violation) {
        static Parsed ok(List<String> segments) {
            return new Parsed(segments, null);
        }

        static Parsed refused(Code code) {
            return new Parsed(List.of(), code);
        }
    }

    /**
     * Checks a name. One leading {@code ./} is dropped and, for a directory, one trailing slash; the name of the archive's
     * own root ({@code ./}) has no segments.
     */
    static Parsed parse(String raw, boolean directory, int maxBytes) {
        Code whole = wholeNameViolation(raw, maxBytes);
        if (whole != null) {
            return Parsed.refused(whole);
        }
        String name = raw.startsWith("./") ? raw.substring(2) : raw;
        if (directory && name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.isEmpty()) {
            return directory ? Parsed.ok(List.of()) : Parsed.refused(Code.PATH_EMPTY_SEGMENT);
        }
        return segments(name);
    }

    private static @Nullable Code wholeNameViolation(String name, int maxBytes) {
        if (name.indexOf('\0') >= 0) {
            return Code.PATH_NUL;
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            return Code.PATH_TOO_LONG;
        }
        if (name.indexOf('\\') >= 0) {
            return Code.PATH_BACKSLASH;
        }
        if (name.startsWith("/")) {
            return Code.PATH_ABSOLUTE;
        }
        return DRIVE.matcher(name).matches() ? Code.PATH_DRIVE_LETTER : null;
    }

    private static Parsed segments(String name) {
        List<String> segments = Arrays.asList(name.split("/", -1));
        for (String segment : segments) {
            Code code = segmentViolation(segment);
            if (code != null) {
                return Parsed.refused(code);
            }
        }
        return Parsed.ok(List.copyOf(segments));
    }

    private static @Nullable Code segmentViolation(String segment) {
        if (segment.isEmpty()) {
            return Code.PATH_EMPTY_SEGMENT;
        }
        if (segment.equals("..")) {
            return Code.PATH_TRAVERSAL;
        }
        if (segment.equals(".")) {
            return Code.PATH_DOT_SEGMENT;
        }
        return SEGMENT.matcher(segment).matches() ? null : Code.PATH_CHARACTER;
    }
}
