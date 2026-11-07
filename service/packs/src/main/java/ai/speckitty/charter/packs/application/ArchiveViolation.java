package ai.speckitty.charter.packs.application;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * One reason an archive was refused: a {@link Code} and the offending entry path. The path is built from untrusted
 * input, so it is escaped and truncated here, whoever builds the violation (NFR-012).
 *
 * @param code why the archive or entry was refused
 * @param path the entry path concerned, control characters escaped and cut to {@value #MAX_VALUE_BYTES} bytes; empty
 *     when the whole archive is concerned
 */
public record ArchiveViolation(Code code, String path) {

    /** The longest value, in UTF-8 bytes, a refusal carries (NFR-012). */
    public static final int MAX_VALUE_BYTES = 256;

    /** Why an archive or one of its entries is refused. */
    public enum Code {
        /** The body is over the size limit (answered {@code 413}). */
        BODY_TOO_LARGE,
        /** The archive expands to more than the limit. */
        EXPANDED_TOO_LARGE,
        /** The archive holds more entries than the limit. */
        TOO_MANY_ENTRIES,
        /** An entry is a symlink, hard link, device, FIFO, socket, sparse file or another type than file or directory. */
        ENTRY_TYPE,
        /** The path is absolute. */
        PATH_ABSOLUTE,
        /** The path starts with a drive letter. */
        PATH_DRIVE_LETTER,
        /** The path holds a backslash. */
        PATH_BACKSLASH,
        /** The path holds a NUL. */
        PATH_NUL,
        /** The path has a {@code ..} segment. */
        PATH_TRAVERSAL,
        /** The path has a {@code .} segment (other than the one leading {@code ./}). */
        PATH_DOT_SEGMENT,
        /** The path has an empty segment. */
        PATH_EMPTY_SEGMENT,
        /** A segment has a character outside {@code [A-Za-z0-9._-]}. */
        PATH_CHARACTER,
        /** The path is longer than 4,096 bytes. */
        PATH_TOO_LONG,
        /** The path collides with another entry's after Unicode normalisation and case folding. */
        PATH_COLLISION,
        /** A zip entry is encrypted. */
        ENCRYPTED,
        /** A zip entry uses a method other than STORED or DEFLATED. */
        COMPRESSION_METHOD,
        /** A zip entry has a non-ASCII name without the language-encoding (EFS) flag. */
        ZIP_NAME_ENCODING,
        /** A zip entry's local-header name differs from its central-directory name. */
        ZIP_NAME_MISMATCH,
        /** Two zip entries' data overlap. */
        ZIP_OVERLAP,
        /** A zip entry carries an Info-ZIP Unicode path or comment extra field. */
        ZIP_EXTRA_FIELD,
        /** A zip entry's declared size differs from the bytes it holds. */
        ZIP_SIZE_MISMATCH,
        /** The end record's entry count differs from the central-directory headers the archive holds. */
        ZIP_COUNT_MISMATCH,
        /** A zip header carries more than 1,024 bytes of extra fields. */
        ZIP_EXTRA_FIELD_TOO_LARGE,
        /** A tar PAX or GNU long-name header is larger than 64 KiB. */
        HEADER_TOO_LARGE,
        /** The archive is truncated or malformed. */
        ARCHIVE_UNREADABLE;

        /**
         * The code on the wire.
         *
         * @return the lower-case, underscore-separated name
         */
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Escapes and truncates the path. */
    public ArchiveViolation {
        path = clean(path);
    }

    private static String clean(String raw) {
        StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_VALUE_BYTES));
        int bytes = 0;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            String piece =
                    isControl(cp) ? String.format(Locale.ROOT, "\\u%04x", cp) : new String(Character.toChars(cp));
            int width = piece.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + width > MAX_VALUE_BYTES) {
                break;
            }
            out.append(piece);
            bytes += width;
        }
        return out.toString();
    }

    private static boolean isControl(int cp) {
        return Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029;
    }
}
