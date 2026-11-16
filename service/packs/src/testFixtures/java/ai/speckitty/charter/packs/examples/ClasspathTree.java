package ai.speckitty.charter.packs.examples;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A tree of test-fixture resources copied out of the classpath to a real directory. The fixtures ship inside the
 * test-fixtures jar when a test reads them, this module's own tests included, so a caller that needs paths it can walk
 * gets a copy, deleted when the JVM exits.
 */
final class ClasspathTree {

    private ClasspathTree() {}

    /**
     * Copies the resource tree at {@code resource} to a new temporary directory.
     *
     * @param anchor a class whose loader sees the resource
     * @param resource the absolute resource path of the tree's root
     * @param prefix the temporary directory's name prefix
     * @return the copy
     */
    static Path copyOut(Class<?> anchor, String resource, String prefix) {
        URL url = anchor.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("no resource tree on the classpath at " + resource);
        }
        try {
            Path target = Files.createTempDirectory(prefix);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(target)));
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                copyOutOfJar(uri, resource, target);
            } else {
                copyTree(Path.of(uri), target);
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Copies a directory tree, file by file.
     *
     * @param source the tree's root
     * @param target where the copy's root lands
     */
    static void copyTree(Path source, Path target) {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path from : walk.toList()) {
                Path to = target.resolve(source.relativize(from).toString());
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Files.createDirectories(to.getParent());
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies out of the jar, whether this call mounts it or something else in the JVM has mounted it already. */
    private static void copyOutOfJar(URI uri, String resource, Path target) throws IOException {
        FileSystem jar;
        boolean mounted;
        try {
            jar = FileSystems.newFileSystem(uri, Map.of());
            mounted = true;
        } catch (FileSystemAlreadyExistsException alreadyMounted) {
            jar = FileSystems.getFileSystem(uri);
            mounted = false;
        }
        try {
            copyTree(jar.getPath(resource), target);
        } finally {
            if (mounted) {
                jar.close();
            }
        }
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | UncheckedIOException e) {
            // best effort on exit: the temporary directory is the operating system's to clean in the end
        }
    }
}
