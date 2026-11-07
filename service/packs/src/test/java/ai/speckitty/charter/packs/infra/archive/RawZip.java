package ai.speckitty.charter.packs.infra.archive;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A hand-written zip writer for the hostile corpus: the central name, the local name, the flags, the method, the
 * declared sizes, the Unix mode, the extra fields and the end-of-central-directory count are each set separately, so
 * the writer can produce the disagreements a hostile archive uses.
 */
final class RawZip {

    static final int STORED = 0;
    static final int DEFLATED = 8;
    static final int FLAG_ENCRYPTED = 0x1;
    static final int FLAG_EFS = 0x800;

    /** One entry; fields are public so a test sets exactly what it needs. */
    static final class Entry {
        byte[] name;
        byte[] localName;
        byte[] stored;
        int method = STORED;
        int flags;
        int unixMode;
        long declaredSize = -1;
        long declaredCompressed = -1;
        byte[] centralExtra = new byte[0];
        byte[] localExtra = new byte[0];
        int crc;
        long uncompressed;

        Entry(String name, byte[] content) {
            this.name = name.getBytes(StandardCharsets.UTF_8);
            this.stored = content;
            this.uncompressed = content.length;
            CRC32 crc32 = new CRC32();
            crc32.update(content);
            this.crc = (int) crc32.getValue();
        }

        Entry deflated() {
            byte[] raw = stored;
            this.stored = RawZip.deflate(raw);
            this.method = DEFLATED;
            return this;
        }

        Entry method(int value) {
            this.method = value;
            return this;
        }

        Entry flags(int value) {
            this.flags = value;
            return this;
        }

        Entry unixMode(int value) {
            this.unixMode = value;
            return this;
        }

        Entry localName(String value) {
            this.localName = value.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        Entry rawName(byte[] value) {
            this.name = value;
            return this;
        }

        Entry declaredSize(long value) {
            this.declaredSize = value;
            return this;
        }

        Entry declaredCompressed(long value) {
            this.declaredCompressed = value;
            return this;
        }

        Entry extra(byte[] value) {
            this.centralExtra = value;
            this.localExtra = value;
            return this;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int declaredCount = -1;

    /** A regular file, STORED. */
    Entry file(String name, String content) {
        return add(new Entry(name, content.getBytes(StandardCharsets.UTF_8)));
    }

    /** A regular file of arbitrary bytes, STORED. */
    Entry bytes(String name, byte[] content) {
        return add(new Entry(name, content));
    }

    /** A directory entry. */
    Entry dir(String name) {
        return add(new Entry(name, new byte[0]).unixMode(040755));
    }

    /** Adds an entry. */
    Entry add(Entry entry) {
        entries.add(entry);
        return entry;
    }

    /** Overrides the entry count the end-of-central-directory record carries. */
    RawZip declaredCount(int count) {
        this.declaredCount = count;
        return this;
    }

    /** An Info-ZIP Unicode Path extra field (0x7075) carrying {@code unicodeName}. */
    static byte[] unicodePathExtra(String asciiName, String unicodeName) {
        byte[] name = unicodeName.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(asciiName.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(4 + 5 + name.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0x7075).putShort((short) (5 + name.length)).put((byte) 1);
        buffer.putInt((int) crc.getValue()).put(name);
        return buffer.array();
    }

    /** Raw DEFLATE of the bytes. */
    static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[64 * 1024];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    /** A DEFLATED entry holding {@code size} zero bytes, deflated in chunks. */
    static Entry zeros(String name, long size) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] zeros = new byte[64 * 1024];
        byte[] chunk = new byte[64 * 1024];
        for (long left = size; left > 0; left -= zeros.length) {
            deflater.setInput(zeros, 0, (int) Math.min(zeros.length, left));
            while (!deflater.needsInput()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
        }
        deflater.finish();
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        Entry entry = new Entry(name, new byte[0]);
        entry.stored = out.toByteArray();
        entry.method = DEFLATED;
        entry.uncompressed = size;
        return entry;
    }

    /** The zip's bytes. */
    byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        for (Entry entry : entries) {
            offsets.add(out.size());
            out.writeBytes(local(entry));
            out.writeBytes(entry.stored);
        }
        int centralStart = out.size();
        for (int i = 0; i < entries.size(); i++) {
            out.writeBytes(central(entries.get(i), offsets.get(i)));
        }
        int centralSize = out.size() - centralStart;
        int count = declaredCount >= 0 ? declaredCount : entries.size();
        out.writeBytes(end(count, centralSize, centralStart));
        return out.toByteArray();
    }

    private static byte[] local(Entry e) {
        byte[] name = e.localName != null ? e.localName : e.name;
        ByteBuffer b =
                ByteBuffer.allocate(30 + name.length + e.localExtra.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x04034b50).putShort((short) 20).putShort((short) e.flags).putShort((short) e.method);
        b.putShort((short) 0).putShort((short) 0x21).putInt(e.crc);
        b.putInt((int) compressed(e)).putInt((int) size(e));
        b.putShort((short) name.length)
                .putShort((short) e.localExtra.length)
                .put(name)
                .put(e.localExtra);
        return b.array();
    }

    private static byte[] central(Entry e, int offset) {
        ByteBuffer b =
                ByteBuffer.allocate(46 + e.name.length + e.centralExtra.length).order(ByteOrder.LITTLE_ENDIAN);
        int madeBy = e.unixMode != 0 ? (3 << 8) | 20 : 20;
        b.putInt(0x02014b50).putShort((short) madeBy).putShort((short) 20).putShort((short) e.flags);
        b.putShort((short) e.method).putShort((short) 0).putShort((short) 0x21).putInt(e.crc);
        b.putInt((int) compressed(e)).putInt((int) size(e));
        b.putShort((short) e.name.length)
                .putShort((short) e.centralExtra.length)
                .putShort((short) 0);
        b.putShort((short) 0).putShort((short) 0).putInt(e.unixMode << 16).putInt(offset);
        b.put(e.name).put(e.centralExtra);
        return b.array();
    }

    private static byte[] end(int count, int centralSize, int centralStart) {
        ByteBuffer b = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x06054b50)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) count)
                .putShort((short) count);
        b.putInt(centralSize).putInt(centralStart).putShort((short) 0);
        return b.array();
    }

    private static long compressed(Entry e) {
        return e.declaredCompressed >= 0 ? e.declaredCompressed : e.stored.length;
    }

    private static long size(Entry e) {
        return e.declaredSize >= 0 ? e.declaredSize : e.uncompressed;
    }
}
