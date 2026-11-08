package ai.speckitty.charter.packs.infra.archive;

/**
 * The bounds of one extraction (NFR-004). The defaults are the contract's: a 32 MiB body, 32 MiB expanded, 10,000
 * entries. Tests build smaller ones to exercise a bound quickly.
 *
 * @param maxBodyBytes the most body bytes read
 * @param maxExpandedBytes the most bytes the archive's files may hold, added up
 * @param maxEntries the most entries an archive may hold
 * @param readSlackBytes what may be read beyond {@code maxExpandedBytes} for tar headers and padding
 * @param maxPathBytes the longest entry path, in UTF-8 bytes
 */
public record ArchiveLimits(
        long maxBodyBytes, long maxExpandedBytes, int maxEntries, long readSlackBytes, int maxPathBytes) {

    private static final long MIB = 1024L * 1024L;

    /** The limits the contract sets. */
    public static final ArchiveLimits DEFAULTS = new ArchiveLimits(32 * MIB, 32 * MIB, 10_000, 64 * 1024, 4_096);

    /**
     * The most decompressed bytes a read may consume before it stops.
     *
     * @return {@code maxExpandedBytes + readSlackBytes}
     */
    public long maxDecompressedRead() {
        return maxExpandedBytes + readSlackBytes;
    }
}
