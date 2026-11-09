package ai.speckitty.charter.packs.infra.archive;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The paths already claimed in one extraction, keyed after Unicode NFC normalisation and case folding, so two entries
 * that would meet on a case-insensitive file system, or a file that would sit where a directory is, collide (IC-03).
 */
final class PathLedger {

    private record Claim(String spelling, boolean directory) {}

    private final Map<String, Claim> claims = new HashMap<>();

    /** Claims the path of an entry and its parent directories; false when it collides with an earlier claim. */
    boolean claim(List<String> segments, boolean directory) {
        StringBuilder key = new StringBuilder();
        StringBuilder spelling = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            key.append(fold(segment)).append('/');
            spelling.append(segment).append('/');
            boolean leaf = i == segments.size() - 1;
            if (!claimOne(key.toString(), spelling.toString(), !leaf || directory)) {
                return false;
            }
        }
        return true;
    }

    private boolean claimOne(String key, String spelling, boolean directory) {
        Claim existing = claims.get(key);
        if (existing == null) {
            claims.put(key, new Claim(spelling, directory));
            return true;
        }
        return existing.directory() && directory && existing.spelling().equals(spelling);
    }

    private static String fold(String segment) {
        return Normalizer.normalize(segment, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
