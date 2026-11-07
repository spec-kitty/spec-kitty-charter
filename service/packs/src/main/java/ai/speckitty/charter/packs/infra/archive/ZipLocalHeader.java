package ai.speckitty.charter.packs.infra.archive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/** What the extractor reads of a zip entry's local file header: its name and where its data starts. */
final class ZipLocalHeader {

    private static final int FIXED_SIZE = 30;
    private static final int SIGNATURE = 0x04034b50;

    private final byte[] name;
    private final long dataStart;

    private ZipLocalHeader(byte[] name, long dataStart) {
        this.name = name;
        this.dataStart = dataStart;
    }

    /** Reads the header at the offset; null when the offset or the signature is wrong. */
    static @Nullable ZipLocalHeader read(FileChannel channel, long offset) throws IOException {
        if (offset < 0 || offset + FIXED_SIZE > channel.size()) {
            return null;
        }
        ByteBuffer fixed = ZipEndRecord.read(channel, offset, FIXED_SIZE);
        if (fixed.getInt(0) != SIGNATURE) {
            return null;
        }
        int nameLength = Short.toUnsignedInt(fixed.getShort(26));
        int extraLength = Short.toUnsignedInt(fixed.getShort(28));
        if (offset + FIXED_SIZE + nameLength + extraLength > channel.size()) {
            return null;
        }
        byte[] name = ZipEndRecord.read(channel, offset + FIXED_SIZE, nameLength).array();
        return new ZipLocalHeader(name, offset + FIXED_SIZE + nameLength + extraLength);
    }

    /** Whether the local name is byte for byte the central directory's. */
    boolean nameEquals(byte[] centralName) {
        return Arrays.equals(name, centralName);
    }

    /** The offset of the entry's data. */
    long dataStart() {
        return dataStart;
    }
}
