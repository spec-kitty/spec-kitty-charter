package ai.speckitty.charter.packs.examples;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The charter-owned example packs every pack-reading test reads (ADR-0070): {@code built-in}, the org pack
 * {@code org-base} that depends on it, and the project pack {@code project} that depends on {@code org-base}. They carry
 * only canonical shapes; an invalid shape belongs to an error-handling fixture, never here.
 *
 * <p>The packs ship as classpath resources, inside a jar when another module reads them. On first use the tree is
 * copied once per JVM to a temporary directory, so every caller gets real paths it can walk; a test that changes a
 * file copies the pack again into its own temporary directory.
 */
public final class ExamplePacks {

    /** The built-in example pack. */
    public static final String BUILT_IN = "built-in";

    /** The org example pack; it depends on {@link #BUILT_IN}. */
    public static final String ORG = "org-base";

    /** The project example pack; it depends on {@link #ORG}. */
    public static final String PROJECT = "project";

    private static final String RESOURCE = "/example-packs";
    private static final Map<String, PackLevel> LEVELS =
            Map.of(BUILT_IN, PackLevel.BUILT_IN, ORG, PackLevel.ORG, PROJECT, PackLevel.PROJECT);

    private static volatile Path copied;

    private ExamplePacks() {}

    /**
     * The directory that holds the three packs, one sub-directory each.
     *
     * @return a real directory
     */
    public static Path packsDirectory() {
        Path local = copied;
        if (local == null) {
            synchronized (ExamplePacks.class) {
                local = copied;
                if (local == null) {
                    local = copyOut();
                    copied = local;
                }
            }
        }
        return local;
    }

    /**
     * One example pack's directory.
     *
     * @param name {@link #BUILT_IN}, {@link #ORG} or {@link #PROJECT}
     * @return its directory
     */
    public static Path pack(String name) {
        if (!LEVELS.containsKey(name)) {
            throw new IllegalArgumentException("no example pack named " + name);
        }
        return packsDirectory().resolve(name);
    }

    /**
     * The three packs as the store configures them, built-in first.
     *
     * @return their locations, with their levels
     */
    public static List<PackLocation> packs() {
        return Stream.of(BUILT_IN, ORG, PROJECT)
                .map(name -> new PackLocation(name, LEVELS.get(name), pack(name)))
                .toList();
    }

    /**
     * A fresh copy of one pack under {@code target}, for a test that changes files.
     *
     * @param name the pack
     * @param target the directory to copy into; the pack lands at {@code target/<name>}
     * @return the copy
     */
    public static Path copyOf(String name, Path target) {
        Path destination = target.resolve(name);
        copyTree(pack(name), destination);
        return destination;
    }

    private static Path copyOut() {
        URL url = ExamplePacks.class.getResource(RESOURCE);
        if (url == null) {
            throw new IllegalStateException("the example packs are not on the classpath at " + RESOURCE);
        }
        try {
            Path target = Files.createTempDirectory("example-packs");
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    copyTree(jar.getPath(RESOURCE), target);
                }
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

    private static void copyTree(Path source, Path target) {
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
}
