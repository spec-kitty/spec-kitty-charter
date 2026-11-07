package ai.speckitty.charter.packs.infra.archive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * The entry count of a zip, read from its end-of-central-directory record (and the ZIP64 record it points to) before
 * any entry is touched (NFR-004). The search takes the last signature in the final 64 KiB, as the library does.
 */
final class ZipEndRecord {

    private static final int END_SIZE = 22;
    private static final int MAX_COMMENT = 0xFFFF;
    private static final int END_SIGNATURE = 0x06054b50;
    private static final int LOCATOR_SIZE = 20;
    private static final int LOCATOR_SIGNATURE = 0x07064b50;
    private static final int ZIP64_END_SIZE = 56;
    private static final int ZIP64_END_SIGNATURE = 0x06064b50;
    private static final int COUNT_OVERFLOW = 0xFFFF;

    private ZipEndRecord() {}

    /** The number of entries the archive declares. */
    static long entryCount(FileChannel channel) throws IOException {
        long size = channel.size();
        int tailLength = (int) Math.min(size, END_SIZE + MAX_COMMENT);
        long tailStart = size - tailLength;
        ByteBuffer tail = read(channel, tailStart, tailLength);
        int at = lastSignature(tail);
        if (at < 0) {
            throw new IOException("no end of central directory record");
        }
        long count = Short.toUnsignedInt(tail.getShort(at + 10));
        return count == COUNT_OVERFLOW ? zip64Count(channel, tailStart + at, count) : count;
    }

    private static int lastSignature(ByteBuffer tail) {
        for (int at = tail.limit() - END_SIZE; at >= 0; at--) {
            if (tail.getInt(at) == END_SIGNATURE) {
                return at;
            }
        }
        return -1;
    }

    private static long zip64Count(FileChannel channel, long endAt, long fallback) throws IOException {
        long locatorAt = endAt - LOCATOR_SIZE;
        if (locatorAt < 0) {
            return fallback;
        }
        ByteBuffer locator = read(channel, locatorAt, LOCATOR_SIZE);
        if (locator.getInt(0) != LOCATOR_SIGNATURE) {
            return fallback;
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
        return total < 0 ? Long.MAX_VALUE : total;
    }

    static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position + buffer.position());
            if (n < 0) {
                throw new IOException("unexpected end of archive");
            }
        }
        return buffer;
    }
}
