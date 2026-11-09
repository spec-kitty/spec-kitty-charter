package ai.speckitty.charter.packs.infra.archive;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/** Reads the {@code path} record from the body of a PAX header: records of the form {@code "<length> key=value\n"}. */
final class PaxRecords {

    private PaxRecords() {}

    /**
     * The value of the last {@code path} record, or null when there is none. A record that does not parse ends the
     * read: the library judges such a header itself.
     */
    static @Nullable String path(byte[] data) {
        String path = null;
        int at = 0;
        while (at < data.length) {
            int length = length(data, at);
            if (length <= 0 || at + length > data.length) {
                return path;
            }
            String value = pathValue(data, at, length);
            path = value != null ? value : path;
            at += length;
        }
        return path;
    }

    private static int length(byte[] data, int at) {
        int length = 0;
        for (int i = at; i < data.length && data[i] != ' '; i++) {
            if (data[i] < '0' || data[i] > '9' || length > data.length) {
                return -1;
            }
            length = length * 10 + (data[i] - '0');
        }
        return length;
    }

    private static @Nullable String pathValue(byte[] data, int at, int length) {
        String record = new String(data, at, length, StandardCharsets.UTF_8);
        int space = record.indexOf(' ');
        if (space < 0 || !record.startsWith("path=", space + 1)) {
            return null;
        }
        String value = record.substring(space + 1 + "path=".length());
        return value.endsWith("\n") ? value.substring(0, value.length() - 1) : value;
    }
}
