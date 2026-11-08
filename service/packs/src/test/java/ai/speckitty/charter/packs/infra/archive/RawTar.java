package ai.speckitty.charter.packs.infra.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * A hand-written tar writer for the hostile corpus: every header field is under the test's control, so it can write
 * what a well-behaved library refuses to (a PAX path override, a GNU long name, a hard link outside the pack, a NUL).
 */
final class RawTar {

    private static final int BLOCK = 512;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /** A regular file. */
    RawTar file(String name, String content) {
        return entry(name, (byte) '0', content.getBytes(StandardCharsets.UTF_8), "");
    }

    /** A directory. */
    RawTar dir(String name) {
        return entry(name, (byte) '5', new byte[0], "");
    }

    /** An entry of any type flag; {@code data} is written after the header, padded to a block. */
    RawTar entry(String name, byte type, byte[] data, String linkName) {
        write(header(name, type, data.length, linkName));
        write(data);
        pad(data.length);
        return this;
    }

    /** An entry of the given type with no data, such as a link, a device or a FIFO. */
    RawTar special(String name, char type, String linkName) {
        return entry(name, (byte) type, new byte[0], linkName);
    }

    /** A PAX extended header ({@code x}) that applies to the next entry. */
    RawTar pax(Map<String, String> records) {
        return entry("PaxHeader/0", (byte) 'x', paxData(records), "");
    }

    /** A PAX extended header whose one record overrides the next entry's path. */
    RawTar paxPath(String path) {
        Map<String, String> records = new LinkedHashMap<>();
        records.put("path", path);
        return pax(records);
    }

    /** A PAX global header ({@code g}), which applies to every later entry. */
    RawTar globalPaxPath(String path) {
        Map<String, String> records = new LinkedHashMap<>();
        records.put("path", path);
        return entry("GlobalHead/0", (byte) 'g', paxData(records), "");
    }

    /** A GNU long-name header ({@code L}) naming the next entry. */
    RawTar gnuLongName(String name) {
        byte[] data = (name + "\0").getBytes(StandardCharsets.UTF_8);
        return entry("././@LongLink", (byte) 'L', data, "");
    }

    /** The tar's bytes, ended with two zero blocks. */
    byte[] tar() {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.writeBytes(out.toByteArray());
        all.writeBytes(new byte[2 * BLOCK]);
        return all.toByteArray();
    }

    /** The tar, gzip-compressed. */
    byte[] gz() {
        return gzip(tar());
    }

    /** Gzip of the given bytes. */
    static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buffer)) {
            gz.write(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    /**
     * A gzip of a tar holding one regular file of {@code size} zero bytes, written in chunks, so a bomb of tens of MiB
     * costs a few KiB.
     */
    static byte[] gzBombOfZeros(String name, long size) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buffer)) {
            gz.write(header(name, (byte) '0', size, ""));
            byte[] zeros = new byte[64 * 1024];
            for (long left = size; left > 0; left -= zeros.length) {
                gz.write(zeros, 0, (int) Math.min(zeros.length, left));
            }
            gz.write(new byte[(int) ((BLOCK - size % BLOCK) % BLOCK) + 2 * BLOCK]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    private static byte[] paxData(Map<String, String> records) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        records.forEach((key, value) -> {
            byte[] body = (" " + key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8);
            int length = body.length + 1;
            while (String.valueOf(length).length() + body.length != length) {
                length = String.valueOf(length).length() + body.length;
            }
            data.writeBytes(String.valueOf(length).getBytes(StandardCharsets.US_ASCII));
            data.writeBytes(body);
        });
        return data.toByteArray();
    }

    private static byte[] header(String name, byte type, long size, String linkName) {
        byte[] block = new byte[BLOCK];
        put(block, 0, 100, name);
        put(block, 100, 8, "0000644");
        put(block, 108, 8, "0000000");
        put(block, 116, 8, "0000000");
        put(block, 124, 12, String.format("%011o", size));
        put(block, 136, 12, "00000000000");
        Arrays.fill(block, 148, 156, (byte) ' ');
        block[156] = type;
        put(block, 157, 100, linkName);
        put(block, 257, 6, "ustar");
        put(block, 263, 2, "00");
        long sum = 0;
        for (byte b : block) {
            sum += b & 0xff;
        }
        put(block, 148, 7, String.format("%06o", sum));
        block[154] = 0;
        block[155] = ' ';
        return block;
    }

    private static void put(byte[] block, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, block, offset, Math.min(bytes.length, length));
    }

    private void pad(int length) {
        write(new byte[(BLOCK - length % BLOCK) % BLOCK]);
    }

    private void write(byte[] bytes) {
        out.writeBytes(bytes);
    }
}
