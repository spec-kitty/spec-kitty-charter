package ai.speckitty.charter.deployable.bdd;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The archives of the registration scenarios: each is a named map from the path of an entry, as written (so a path that
 * leaves the pack can be tested), to its text, built as a {@code tar.gz} or a {@code zip} with the JDK alone. A path named
 * twice holds the later text.
 */
final class ScenarioArchives {

    private static final int BLOCK = 512;
    private static final int NAME = 100;

    private final Map<String, Map<String, String>> archives = new LinkedHashMap<>();

    void put(String archive, String path, String text) {
        archives.computeIfAbsent(archive, name -> new LinkedHashMap<>()).put(path, text);
    }

    void copy(String archive, String of) {
        archives.put(archive, new LinkedHashMap<>(files(of)));
    }

    void remove(String archive, String path) {
        if (files(archive).remove(path) == null) {
            throw new IllegalStateException("the archive " + archive + " holds no file " + path + " to leave out");
        }
    }

    Map<String, String> files(String archive) {
        Map<String, String> files = archives.get(archive);
        if (files == null) {
            throw new IllegalStateException("no archive " + archive + " was described");
        }
        return files;
    }

    byte[] tarGz(String archive) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files(archive).entrySet()) {
                byte[] content = file.getValue().getBytes(StandardCharsets.UTF_8);
                gzip.write(header(file.getKey(), content.length));
                gzip.write(content);
                gzip.write(new byte[padding(content.length)]);
            }
            gzip.write(new byte[2 * BLOCK]);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }

    byte[] zip(String archive) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files(archive).entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }

    private static int padding(int length) {
        return (BLOCK - length % BLOCK) % BLOCK;
    }

    /** A POSIX ustar header of a regular file. */
    private static byte[] header(String path, int size) {
        byte[] name = path.getBytes(StandardCharsets.UTF_8);
        if (name.length > NAME) {
            throw new IllegalArgumentException("a scenario archive path is at most 100 bytes: " + path);
        }
        byte[] block = new byte[BLOCK];
        System.arraycopy(name, 0, block, 0, name.length);
        put(block, 100, "0000644");
        put(block, 108, "0000000");
        put(block, 116, "0000000");
        put(block, 124, String.format("%011o", size));
        put(block, 136, String.format("%011o", 0));
        java.util.Arrays.fill(block, 148, 156, (byte) ' ');
        block[156] = '0';
        put(block, 257, "ustar");
        put(block, 263, "00");
        long sum = 0;
        for (byte b : block) {
            sum += b & 0xff;
        }
        put(block, 148, String.format("%06o", sum));
        block[154] = 0;
        block[155] = ' ';
        return block;
    }

    private static void put(byte[] block, int offset, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, block, offset, bytes.length);
    }
}
