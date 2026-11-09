package ai.speckitty.charter.packs.infra.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** The file-system side of an extraction: owner-only creation, and copying that tells a read failure from a write one. */
final class ArchiveIo {

    private static final FileAttribute<Set<PosixFilePermission>> FILE_MODE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    private static final FileAttribute<Set<PosixFilePermission>> DIR_MODE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    private ArchiveIo() {}

    /** Creates a directory with mode {@code 0700}. */
    static void createDirectory(Path directory) {
        try {
            Files.createDirectory(directory, DIR_MODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether the path is a real directory, not a link to one. */
    static boolean isDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    /** Creates a new file with mode {@code 0600}, never following a link and never replacing a file. */
    static OutputStream createFile(Path file) {
        Set<OpenOption> options =
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            return Channels.newOutputStream(Files.newByteChannel(file, options, FILE_MODE));
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
