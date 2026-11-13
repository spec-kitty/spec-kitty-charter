package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.OwnerOnly;
import ai.speckitty.charter.packs.application.StagingArea;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The staging area on the file system: owner-only directories under one root. */
public final class FileStagingArea implements StagingArea {

    private static final Logger LOG = LoggerFactory.getLogger(FileStagingArea.class);

    private final Path root;

    /**
     * A staging area under {@code root}, which is created, with its missing parents, when the first directory is.
     *
     * @param root the staging root
     * @throws IllegalStateException when the root's file system cannot make a path owner-only (#510)
     */
    public FileStagingArea(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        OwnerOnly.requireSupported(this.root);
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public Path createJobDirectory(String id) throws IOException {
        Objects.requireNonNull(id, "id");
        return createDirectory(root.resolve(id));
    }

    @Override
    public Path createDirectory(Path directory) throws IOException {
        return OwnerOnly.directories(Objects.requireNonNull(directory, "directory"));
    }

    @Override
    public void delete(Path directory) {
        Path target =
                Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (target.equals(root) || !target.startsWith(root)) {
            throw new IllegalArgumentException("only a directory inside the staging root is removed");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | UncheckedIOException failure) {
            LOG.warn(
                    "The staging directory {} could not be removed; the sweep takes it later.",
                    target.getFileName(),
                    failure);
        }
    }
}
