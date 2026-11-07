package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * The walk of a zip's central directory that runs before the library is opened (NFR-004, the operator's ruling
 * DM-01M6TZRAA7JQR2H5WN4SKNXGQ8). {@code ZipFile} ignores the end record's count, builds an entry and a name map for
 * every header it finds, and merges extra fields in quadratic time, so the walk checks what it would otherwise build:
 * it follows the headers from the offset the end record gives, with one fixed-size read per header and one reused
 * buffer, stops and refuses at the first header past the entry limit, refuses an extra field over
 * {@value #MAX_EXTRA_BYTES} bytes, and refuses when the headers found are not the count the end record declares.
 */
final class ZipCentralDirectory {

    /** The most extra-field bytes one header may carry. */
    static final int MAX_EXTRA_BYTES = 1_024;

    private static final int FIXED_SIZE = 46;
    private static final int SIGNATURE = 0x02014b50;
    private static final int NAME_LENGTH_AT = 28;
    private static final int EXTRA_LENGTH_AT = 30;
    private static final int COMMENT_LENGTH_AT = 32;

    private final FileChannel channel;
    private final ArchiveLimits limits;
    private final ByteBuffer fixed = ByteBuffer.allocate(FIXED_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private int headers;

    ZipCentralDirectory(FileChannel channel, ArchiveLimits limits) {
        this.channel = channel;
        this.limits = limits;
    }

    /** The number of headers read so far. */
    int headersRead() {
        return headers;
    }

    /**
     * Walks the directory.
     *
     * @throws ArchiveLimitException when the archive declares or holds too many entries, the counts disagree, or a
     *     header's extra fields are too large
     * @throws IOException when the archive is truncated or has no end record
     */
    void verify() throws IOException {
        ZipEndRecord.Location end = ZipEndRecord.locate(channel);
        if (end.count() > limits.maxEntries()) {
            throw new ArchiveLimitException(Code.TOO_MANY_ENTRIES, "");
        }
        long fileSize = channel.size();
        if (end.offset() < 0 || end.offset() > fileSize) {
            throw new IOException("central directory is outside the archive");
        }
        long position = end.offset();
        for (long next = step(position, fileSize); next >= 0; next = step(position, fileSize)) {
            position = next;
        }
        if (headers != end.count() || position - end.offset() > end.size()) {
            throw new ArchiveLimitException(Code.ZIP_COUNT_MISMATCH, "");
        }
    }

    /** Reads the header at the position; returns where the next one starts, or -1 when none starts here. */
    private long step(long position, long fileSize) throws IOException {
        if (position + FIXED_SIZE > fileSize) {
            return -1;
        }
        fixed.clear();
        ZipEndRecord.fill(channel, fixed, position);
        if (fixed.getInt(0) != SIGNATURE) {
            return -1;
        }
        if (++headers > limits.maxEntries()) {
            throw new ArchiveLimitException(Code.TOO_MANY_ENTRIES, "");
        }
        int nameLength = Short.toUnsignedInt(fixed.getShort(NAME_LENGTH_AT));
        int extraLength = Short.toUnsignedInt(fixed.getShort(EXTRA_LENGTH_AT));
        if (extraLength > MAX_EXTRA_BYTES) {
            throw new ArchiveLimitException(Code.ZIP_EXTRA_FIELD_TOO_LARGE, name(position, nameLength, fileSize));
        }
        long next = position
                + FIXED_SIZE
                + nameLength
                + extraLength
                + Short.toUnsignedInt(fixed.getShort(COMMENT_LENGTH_AT));
        if (next > fileSize) {
            throw new IOException("central directory header runs past the end of the archive");
        }
        return next;
    }

    /** The header's name, for the refusal only; empty when the name runs past the file. */
    private String name(long position, int length, long fileSize) throws IOException {
        long at = position + FIXED_SIZE;
        if (at + length > fileSize) {
            return "";
        }
        return new String(ZipEndRecord.read(channel, at, length).array(), StandardCharsets.UTF_8);
    }
}
