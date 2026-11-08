package ai.speckitty.charter.packs.application;

import java.io.Serial;
import java.util.List;

/**
 * An archive the extractor refused (NFR-004, NFR-005, NFR-012). It lists at most {@value #MAX_LISTED} violations; the
 * rest are only counted. Nothing the archive held is left in staging.
 */
public final class ArchiveRefusal extends Exception {

    /** The most violations a refusal lists. */
    public static final int MAX_LISTED = 100;

    @Serial
    private static final long serialVersionUID = 1L;

    /** The listed violations, first found first. */
    private final transient List<ArchiveViolation> violations;

    /** How many further violations were found and not listed. */
    private final int omitted;

    /** The decompressed bytes read before the refusal. */
    private final long decompressedBytes;

    /**
     * A refusal.
     *
     * @param violations what was found, in order; only the first {@value #MAX_LISTED} are kept
     * @param omitted how many violations were found beyond the ones given
     * @param decompressedBytes the decompressed bytes read before the refusal
     */
    public ArchiveRefusal(List<ArchiveViolation> violations, int omitted, long decompressedBytes) {
        super(describe(violations, omitted));
        int kept = Math.min(violations.size(), MAX_LISTED);
        this.violations = List.copyOf(violations.subList(0, kept));
        this.omitted = omitted + (violations.size() - kept);
        this.decompressedBytes = decompressedBytes;
    }

    private static String describe(List<ArchiveViolation> violations, int omitted) {
        if (violations.isEmpty()) {
            return "archive refused";
        }
        ArchiveViolation first = violations.get(0);
        int more = violations.size() - 1 + omitted;
        return "archive refused: " + first.code().wire() + " '" + first.path() + "'"
                + (more > 0 ? " and " + more + " more" : "");
    }

    /**
     * What was found.
     *
     * @return the listed violations
     */
    public List<ArchiveViolation> violations() {
        return violations;
    }

    /**
     * The violations found and not listed.
     *
     * @return the count
     */
    public int omitted() {
        return omitted;
    }

    /**
     * The decompressed bytes read before the refusal (NFR-004 bounds this at 32 MiB + 64 KiB).
     *
     * @return the count
     */
    public long decompressedBytes() {
        return decompressedBytes;
    }

    /**
     * Whether the body was over the size limit, which is answered {@code 413} where every other refusal is {@code 422}.
     *
     * @return true for {@link ArchiveViolation.Code#BODY_TOO_LARGE}
     */
    public boolean bodyTooLarge() {
        return violations.stream().anyMatch(v -> v.code() == ArchiveViolation.Code.BODY_TOO_LARGE);
    }
}
