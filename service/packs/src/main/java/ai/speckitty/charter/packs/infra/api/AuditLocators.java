package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.SourceUrls;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The locator of a source as the audit record keeps it (NFR-010, NFR-012): the URL with its query, its fragment and its
 * user information cut, so a credential or a query secret is never stored; the relative path of a local source as sent.
 * Control, line-separator and Unicode format characters are escaped and the value is held to 256 UTF-8 bytes, never cutting
 * a character in two, so a request cannot forge a line of the log or inflate it.
 */
final class AuditLocators {

    private static final int MAX_BYTES = 256;

    private AuditLocators() {}

    static String of(PackSourceRequest request) {
        String url = request.url();
        if (url != null) {
            return clean(SourceUrls.withoutSecrets(url));
        }
        String path = request.path();
        return path == null ? "" : clean(path);
    }

    private static String clean(@Nullable String raw) {
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; raw != null && i < raw.length(); ) {
            int codePoint = raw.codePointAt(i);
            i += Character.charCount(codePoint);
            String piece = isEscaped(codePoint)
                    ? String.format(Locale.ROOT, "\\u%04x", codePoint)
                    : new String(Character.toChars(codePoint));
            int width = piece.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + width > MAX_BYTES) {
                break;
            }
            out.append(piece);
            bytes += width;
        }
        return out.toString();
    }

    private static boolean isEscaped(int codePoint) {
        return Character.isISOControl(codePoint)
                || codePoint == 0x2028
                || codePoint == 0x2029
                || Character.getType(codePoint) == Character.FORMAT;
    }
}
