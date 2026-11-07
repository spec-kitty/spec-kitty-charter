package ai.speckitty.charter.packs.infra.archive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** What the archive tests share: reading back a staged tree, and listing a directory. */
final class ArchiveFixtures {

    static final Set<PosixFilePermission> FILE_MODE = PosixFilePermissions.fromString("rw-------");
    static final Set<PosixFilePermission> DIR_MODE = PosixFilePermissions.fromString("rwx------");

    private ArchiveFixtures() {}

    /** The tree under {@code root}: a path (directories end with a slash) mapped to its content ("" for a directory). */
    static Map<String, String> tree(Path root) {
        TreeMap<String, String> tree = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> !p.equals(root)).forEach(p -> tree.put(relative(root, p), content(p)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return tree;
    }

    /** The names directly under {@code dir}, sorted. */
    static java.util.List<String> listing(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(Path root, Path p) {
        String rel = root.relativize(p).toString();
        return Files.isDirectory(p) ? rel + "/" : rel;
    }

    private static String content(Path p) {
        try {
            return Files.isDirectory(p) ? "" : Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The owner-only modes every staged file and directory must have. */
    static void assertOwnerOnly(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.forEach(p -> check(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void check(Path p) {
        try {
            Set<PosixFilePermission> actual = Files.getPosixFilePermissions(p);
            Set<PosixFilePermission> wanted = Files.isDirectory(p) ? DIR_MODE : FILE_MODE;
            if (!actual.equals(wanted)) {
                throw new AssertionError(p + " has mode " + PosixFilePermissions.toString(actual) + ", wanted "
                        + PosixFilePermissions.toString(wanted));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
