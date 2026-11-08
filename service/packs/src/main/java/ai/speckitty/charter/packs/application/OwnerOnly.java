package ai.speckitty.charter.packs.application;

import java.io.IOException;
import java.io.OutputStream;
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

/**
 * What a registration writes to disk before it is stored, whose mode is the owner's alone: the staging root, a job's
 * directory, the uploaded archive and the tree extracted from it (plan, security squad 8). A staged upload is
 * untrusted input of one client, and the process may share a host or a volume with others.
 */
public final class OwnerOnly {

    private static final FileAttribute<Set<PosixFilePermission>> DIRECTORY_MODE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    private static final FileAttribute<Set<PosixFilePermission>> FILE_MODE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private OwnerOnly() {}

    /**
     * Creates the directory and every parent that is missing, each with mode {@code 0700}. A directory that exists is
     * left as it is.
     *
     * @param directory the directory
     * @return the directory
     * @throws IOException when it cannot be made
     */
    public static Path directories(Path directory) throws IOException {
        return Files.createDirectories(directory, DIRECTORY_MODE);
    }

    /**
     * Opens a new file for writing with mode {@code 0600}, never following a link and never replacing a file.
     *
     * @param file the file, which must not exist
     * @return the stream to write it with; the caller closes it
     * @throws IOException when the file exists, is a link, or cannot be made
     */
    public static OutputStream newFile(Path file) throws IOException {
        Set<OpenOption> options =
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        return Channels.newOutputStream(Files.newByteChannel(file, options, FILE_MODE));
    }
}
