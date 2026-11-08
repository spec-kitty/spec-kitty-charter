package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Where a zip's central directory is and how many entries it declares, read from its end-of-central-directory record
 * (and the ZIP64 record it points to) before any entry is touched (NFR-004). The search takes the last signature in the
 * final 64 KiB, as the library does. When the ZIP64 locator signature sits just before it, the ZIP64 record decides where
 * the directory is, as in the library, and an end record that disagrees is refused.
 */
final class ZipEndRecord {

    /**
     * What the end record declares.
     *
     * @param count the number of central-directory entries
     * @param offset where the central directory starts
     * @param size how many bytes it declares
     */
    record Location(long count, long offset, long size) {}

    private static final int END_SIZE = 22;
    private static final int MAX_COMMENT = 0xFFFF;
    private static final int END_SIGNATURE = 0x06054b50;
    private static final int LOCATOR_SIZE = 20;
    private static final int LOCATOR_SIGNATURE = 0x07064b50;
    private static final int ZIP64_END_SIZE = 56;
    private static final int ZIP64_END_SIGNATURE = 0x06064b50;
    private static final int COUNT_OVERFLOW = 0xFFFF;
    private static final long FIELD_OVERFLOW = 0xFFFFFFFFL;

    private ZipEndRecord() {}

    /** The central directory the archive declares. */
    static Location locate(FileChannel channel) throws IOException {
        long size = channel.size();
        int tailLength = (int) Math.min(size, END_SIZE + MAX_COMMENT);
        long tailStart = size - tailLength;
        ByteBuffer tail = read(channel, tailStart, tailLength);
        int at = lastSignature(tail);
        if (at < 0) {
            throw new IOException("no end of central directory record");
        }
        Location end = new Location(
                Short.toUnsignedInt(tail.getShort(at + 10)),
                Integer.toUnsignedLong(tail.getInt(at + 16)),
                Integer.toUnsignedLong(tail.getInt(at + 12)));
        return zip64(channel, tailStart + at, end);
    }

    private static int lastSignature(ByteBuffer tail) {
        for (int at = tail.limit() - END_SIZE; at >= 0; at--) {
            if (tail.getInt(at) == END_SIGNATURE) {
                return at;
            }
        }
        return -1;
    }

    /**
     * The ZIP64 record's location when the locator signature sits just before the end record, whatever the end record's
     * fields say, which is how the library decides (it then takes the directory's offset from that record). The
     * end-record fields that are not overflow values must agree with it.
     */
    private static Location zip64(FileChannel channel, long endAt, Location end) throws IOException {
        long locatorAt = endAt - LOCATOR_SIZE;
        if (locatorAt < 0) {
            return end;
        }
        ByteBuffer locator = read(channel, locatorAt, LOCATOR_SIZE);
        if (locator.getInt(0) != LOCATOR_SIGNATURE) {
            return end;
        }
        long recordAt = locator.getLong(8);
        if (recordAt < 0 || recordAt + ZIP64_END_SIZE > channel.size()) {
            throw new IOException("ZIP64 end record is outside the archive");
        }
        ByteBuffer record = read(channel, recordAt, ZIP64_END_SIZE);
        if (record.getInt(0) != ZIP64_END_SIGNATURE) {
            throw new IOException("ZIP64 end record has a bad signature");
        }
        long total = record.getLong(32);
        Location wide = new Location(total < 0 ? Long.MAX_VALUE : total, record.getLong(48), record.getLong(40));
        if (disagree(end.count(), COUNT_OVERFLOW, wide.count())
                || disagree(end.offset(), FIELD_OVERFLOW, wide.offset())
                || disagree(end.size(), FIELD_OVERFLOW, wide.size())) {
            throw new ArchiveLimitException(Code.ZIP_COUNT_MISMATCH, "");
        }
        return wide;
    }

    private static boolean disagree(long narrow, long overflow, long wide) {
        return narrow != overflow && narrow != wide;
    }

    static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        fill(channel, buffer, position);
        return buffer;
    }

    /** Fills the buffer from the position; the buffer is left full, with its position at the end. */
    static void fill(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        int start = buffer.position();
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position + buffer.position() - start);
            if (n < 0) {
                throw new IOException("unexpected end of archive");
            }
        }
    }
}
