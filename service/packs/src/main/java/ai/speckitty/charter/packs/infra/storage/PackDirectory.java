package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The name of a directory under a storage root that holds a pack: a live pack's directory, a staging directory
 * ({@code .pack-commit-*}), an aside copy ({@code <id>.pack-old-*}) or a link's real target. It names a directory, not a
 * pack: a {@link PackIdentifier} is the id a pack is registered under, and a live pack's directory is named after it
 * ({@link #of(PackIdentifier)}).
 *
 * @param name the directory's name: one path segment
 */
record PackDirectory(String name) {

    /**
     * Checks that the name is one path segment: not blank, with no separator, and neither {@code .} nor {@code ..}.
     *
     * @param name the directory's name
     * @throws IllegalArgumentException when the name is not one path segment
     */
    PackDirectory {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a pack directory name must not be blank");
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("a pack directory name must not hold a separator: " + name);
        }
        if (name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("a pack directory name must not be " + name);
        }
    }

    /**
     * The directory a registered pack lives in, named after its id.
     *
     * @param id the registered pack id
     * @return the directory named after the id
     */
    static PackDirectory of(PackIdentifier id) {
        return new PackDirectory(id.value());
    }

    /**
     * The directory a path names, by its file name.
     *
     * @param directory the directory's path
     * @return the directory named by the path's last segment
     * @throws IllegalArgumentException when the path has no file name, or it is not one path segment
     */
    static PackDirectory of(Path directory) {
        Path fileName = directory.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("a path with no file name names no pack directory: " + directory);
        }
        return new PackDirectory(fileName.toString());
    }

    @Override
    public String toString() {
        return name;
    }
}
