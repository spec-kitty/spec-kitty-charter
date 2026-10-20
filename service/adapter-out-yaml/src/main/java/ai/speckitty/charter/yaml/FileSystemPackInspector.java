package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackContents;
import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.RawGraph;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackInspector;
import ai.speckitty.charter.domain.PackRoot;
import ai.speckitty.charter.domain.RefusalCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Reads a pack directory as facts: {@code pack.yaml}, the root {@code *.graph.yaml} shards (sorted by file name) and
 * {@code drg/fragment.yaml}, the places core's runtime reads, each as {@link FileRead.Absent},
 * {@link FileRead.Unreadable} or {@link FileRead.Read}. Every call reads the files again. It decides nothing:
 * {@code PackPolicy} judges what it finds.
 *
 * <p>Only the load path is read here; a full read of every file of the pack arrives with the validation.
 */
public final class FileSystemPackInspector implements PackInspector {

    private static final String PACK_YAML = "pack.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";

    /** An inspector; it keeps no state. */
    public FileSystemPackInspector() {}

    /**
     * Reads the load path of a pack directory.
     *
     * @param root the pack to read; its {@code locator} is the directory
     * @param scope how much of it to read; only {@link Scope#LOAD_PATH} is supported
     * @return the facts of {@code pack.yaml}, the shards and the fragment
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory does not exist, cannot be listed or a file
     *     cannot be read; the content of a file never throws
     * @throws UnsupportedOperationException for {@link Scope#FULL}
     */
    @Override
    public PackContents inspect(PackRoot root, Scope scope) {
        if (scope == Scope.FULL) {
            throw new UnsupportedOperationException("FULL scope lands in WP08");
        }
        Path directory = Path.of(root.locator());
        String name = root.name();
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name + " is not a directory");
        }
        FileRead<PackDescriptor> descriptor = new FileRead.Absent<>(PACK_YAML);
        Path packYaml = directory.resolve(PACK_YAML);
        if (Files.isRegularFile(packYaml)) {
            descriptor = PackDescriptorReader.read(text(packYaml, PACK_YAML, name));
        }
        List<FileRead<RawGraph>> shards = new ArrayList<>();
        for (Path shard : shards(directory, name)) {
            String file = directory.relativize(shard).toString();
            shards.add(GraphDocumentReader.read(text(shard, file, name), GraphDocumentReader.Shape.SHARD, file));
        }
        FileRead<RawGraph> fragment = new FileRead.Absent<>(FRAGMENT);
        Path fragmentYaml = directory.resolve("drg").resolve("fragment.yaml");
        if (Files.isRegularFile(fragmentYaml)) {
            fragment = GraphDocumentReader.read(
                    text(fragmentYaml, FRAGMENT, name), GraphDocumentReader.Shape.FRAGMENT, FRAGMENT);
        }
        return new PackContents(root, descriptor, shards, fragment, List.of(), List.of(), List.of(), List.of());
    }

    private static List<Path> shards(Path directory, String name) {
        try (Stream<Path> files = Files.list(directory)) {
            return graphShards(files, name, Files::isRegularFile);
        } catch (IOException e) {
            throw unlistable(name, e);
        }
    }

    private static String text(Path path, String file, String packName) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, packName + "/" + file + " cannot be read", e);
        }
    }

    /**
     * The graph shards of a pack directory listing: its {@code *.graph.yaml} regular files, sorted by name.
     *
     * @param files the directory listing; reading it may fail with an {@link UncheckedIOException}
     * @param packName the pack the listing belongs to, named in a refusal
     * @param regularFile whether a listed path is a regular file
     * @return the shards, sorted
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when reading the listing fails, as when opening it fails
     */
    static List<Path> graphShards(Stream<Path> files, String packName, Predicate<Path> regularFile) {
        try {
            return files.filter(p -> p.getFileName().toString().endsWith(".graph.yaml") && regularFile.test(p))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException e) {
            // The JDK always sets the cause; the fallback only satisfies the nullness checker.
            throw unlistable(packName, Objects.<Exception>requireNonNullElse(e.getCause(), e));
        }
    }

    static CharterRefusal unlistable(String packName, Exception cause) {
        return new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + packName + " cannot be listed", cause);
    }
}
