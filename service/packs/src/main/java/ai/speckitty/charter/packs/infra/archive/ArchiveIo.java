package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.OwnerOnly;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** The file-system side of an extraction: owner-only creation, and copying that tells a read failure from a write one. */
final class ArchiveIo {

    private ArchiveIo() {}

    /** Creates an owner-only directory ({@link OwnerOnly}). */
    static void createDirectory(Path directory) {
        try {
            OwnerOnly.directory(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether the path is a real directory, not a link to one. */
    static boolean isDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    /** Creates a new owner-only file ({@link OwnerOnly}), never following a link and never replacing a file. */
    static OutputStream createFile(Path file) {
        try {
            return OwnerOnly.newFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Copies a stream. A failure reading {@code in} is an {@link IOException} (the archive is bad); a failure writing
     * {@code out} is an {@link UncheckedIOException} (the host is).
     *
     * @return the bytes copied
     */
    static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
            write(out, buffer, n);
            total += n;
        }
        return total;
    }

    private static void write(OutputStream out, byte[] buffer, int length) {
        try {
            out.write(buffer, 0, length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Closes a stream the extractor wrote. */
    static void close(OutputStream out) {
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
