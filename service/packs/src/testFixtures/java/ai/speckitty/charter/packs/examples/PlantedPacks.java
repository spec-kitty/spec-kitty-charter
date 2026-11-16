package ai.speckitty.charter.packs.examples;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The planted packs: the error-handling fixtures, each an invalid pack or a control beside one, under
 * {@code planted/<plant>/}. A plant holds its pack under {@code files/} (none when a named test proves it instead) and
 * what it expects in {@code expected.json}; {@code planted/core-verdicts.json} is core's own verdict on each
 * ({@code scripts/packs/core_verdicts.py}). They are test fixtures, so {@code :pack-validation}'s tests read them too
 * (#554, M-8).
 *
 * <p>On first use the tree is copied once per JVM to a temporary directory, as {@link ExamplePacks} copies its packs;
 * a test that loads or changes a plant copies it again into its own temporary directory ({@link #copyOf}).
 */
public final class PlantedPacks {

    private static final String RESOURCE = "/planted";

    private static volatile Path copied;

    private PlantedPacks() {}

    /**
     * The directory that holds every plant, one sub-directory each, and {@code core-verdicts.json}.
     *
     * @return a real directory
     */
    public static Path directory() {
        Path local = copied;
        if (local == null) {
            synchronized (PlantedPacks.class) {
                local = copied;
                if (local == null) {
                    local = ClasspathTree.copyOut(PlantedPacks.class, RESOURCE, "planted");
                    copied = local;
                }
            }
        }
        return local;
    }

    /**
     * Every plant's name, sorted.
     *
     * @return the plant directories' names
     */
    public static List<String> names() {
        try (Stream<Path> dirs = Files.list(directory())) {
            return dirs.filter(Files::isDirectory)
                    .map(dir -> String.valueOf(dir.getFileName()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * One plant's directory: its {@code expected.json}, and its pack under {@code files/} when it has one.
     *
     * @param plant the plant's name
     * @return its directory
     */
    public static Path plant(String plant) {
        Path dir = directory().resolve(plant);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("no plant named " + plant);
        }
        return dir;
    }

    /**
     * A fresh copy of one plant's pack, its {@code files/}, at {@code target/qa}, so a test may load or change it in
     * place. The symbolic links an {@code expected.json} names are not made here.
     *
     * @param plant the plant's name
     * @param target the directory to copy into
     * @return the copy, {@code target/qa}
     */
    public static Path copyOf(String plant, Path target) {
        Path source = plant(plant).resolve("files");
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("the plant " + plant + " holds no pack");
        }
        Path destination = target.resolve("qa");
        ClasspathTree.copyTree(source, destination);
        return destination;
    }
}
