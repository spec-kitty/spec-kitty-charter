package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The packs of one storage root: every directory directly under the root is a pack, read and written in place. The
 * directory named {@code built-in} is the built-in pack; every other directory is an org pack, named after itself.
 *
 * <p>The root is listed on every call, so a pack added or removed while the service runs is seen on the next read.
 * Hidden directories are not packs: {@link PackCommitStore} stages a commit in a sibling {@code .pack-commit-*}
 * directory. Neither is a {@code <name>.pack-old-*} directory, the aside copy a commit leaves when its swap is
 * interrupted.
 */
public final class StorageRootLocations implements DomainRepositoryService.Locations {

    /** The directory name of the built-in pack. */
    public static final String BUILT_IN = "built-in";

    private static final String ASIDE_MARKER = ".pack-old-";

    private final Path root;

    /**
     * The packs under {@code root}.
     *
     * @param root the storage root; an existing directory
     * @throws IllegalArgumentException when {@code root} is not an existing directory
     */
    public StorageRootLocations(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("the storage root is not an existing directory");
        }
        this.root = root;
    }

    @Override
    public List<PackLocation> all() {
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .filter(StorageRootLocations::isPack)
                    .map(StorageRootLocations::location)
                    .sorted(Comparator.comparing((PackLocation location) -> location.level() != PackLevel.BUILT_IN)
                            .thenComparing(PackLocation::name))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the storage root", e);
        }
    }

    private static boolean isPack(Path directory) {
        String name = String.valueOf(directory.getFileName());
        return !name.startsWith(".") && !name.contains(ASIDE_MARKER);
    }

    private static PackLocation location(Path directory) {
        String name = String.valueOf(directory.getFileName());
        return new PackLocation(name, BUILT_IN.equals(name) ? PackLevel.BUILT_IN : PackLevel.ORG, directory);
    }
}
