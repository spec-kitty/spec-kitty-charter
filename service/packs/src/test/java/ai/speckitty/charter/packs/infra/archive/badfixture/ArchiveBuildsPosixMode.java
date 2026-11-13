package ai.speckitty.charter.packs.infra.archive.badfixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Known-bad control for {@code OwnerOnlyBoundaryTest}: a class outside the owner-only helper that builds a POSIX mode
 * attribute itself, as the five call sites of #510 did.
 */
public final class ArchiveBuildsPosixMode {

    private ArchiveBuildsPosixMode() {}

    /**
     * Creates a directory with mode {@code 0700}; never called.
     *
     * @param directory the directory
     * @return the directory
     * @throws IOException never, the method is not called
     */
    public static Path create(Path directory) throws IOException {
        return Files.createDirectory(
                directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    /**
     * Sets mode {@code 0700} on a path after the fact; never called.
     *
     * @param path the path
     * @return the path
     * @throws IOException never, the method is not called
     */
    public static Path restrict(Path path) throws IOException {
        return Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
    }
}
