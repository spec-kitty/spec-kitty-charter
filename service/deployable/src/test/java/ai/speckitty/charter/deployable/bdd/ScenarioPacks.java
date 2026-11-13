package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** The packs of the running scenario, written to a fresh directory that goes when the scenario ends. */
public final class ScenarioPacks {

    private static final String CONFIG_DIR = ".kittify";

    private @Nullable Path root;
    private boolean failing;
    private final List<PackLocation> locations = new ArrayList<>();

    /** Starts a scenario: deletes the previous scenario's directory and creates an empty one. */
    void reset() {
        delete();
        try {
            root = Files.createDirectories(
                            Files.createTempDirectory("charter-scenario").resolve(CONFIG_DIR))
                    .getParent();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The scenario's project configuration directory, {@code <root>/.kittify}. It always exists during a scenario; a
     * step writes or deletes the files in it.
     *
     * @return the directory, or empty when no scenario is running
     */
    Optional<Path> configDir() {
        return Optional.ofNullable(root).map(r -> r.resolve(CONFIG_DIR));
    }

    /**
     * The directory of the running scenario, which holds its fixed packs and its pack domain.
     *
     * @return the directory, or empty when no scenario is running
     */
    Optional<Path> rootDirectory() {
        return Optional.ofNullable(root);
    }

    /** Writes {@code content} exactly, with no added newline, to a file of the project configuration directory. */
    void writeConfigFile(String name, String content) {
        try {
            Path directory = Files.createDirectories(configDirectory());
            Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Deletes a file of the project configuration directory; nothing happens when it is not there. */
    void deleteConfigFile(String name) {
        try {
            Files.deleteIfExists(configDirectory().resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Replaces a file of the project configuration directory with an empty directory of that name. */
    void makeConfigFileADirectory(String name) {
        try {
            Path target = Files.createDirectories(configDirectory()).resolve(name);
            Files.deleteIfExists(target);
            Files.createDirectory(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path configDirectory() {
        return configDir().orElseThrow(() -> new IllegalStateException("no scenario is running"));
    }

    /** Ends a scenario: deletes its directory and forgets its packs; nothing happens when there is none. */
    void delete() {
        failing = false;
        locations.clear();
        Path current = root;
        root = null;
        if (current == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(current)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void addFile(PackLevel level, String pack, String relativePath, String content) {
        addBytes(
                level,
                pack,
                relativePath,
                (content.endsWith("\n") ? content : content + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes one file of a pack byte for byte, configuring the pack at that level on its first file: a binary payload.
     *
     * @param level the pack's level
     * @param pack the configured name of the pack
     * @param relativePath the file's path below the pack directory
     * @param bytes the file's exact content
     */
    void addBytes(PackLevel level, String pack, String relativePath, byte[] bytes) {
        if (root == null) {
            throw new IllegalStateException("no scenario is running");
        }
        Path packRoot = root.resolve(level.token()).resolve(pack);
        if (locations.stream().noneMatch(l -> l.root().equals(packRoot))) {
            locations.add(new PackLocation(pack, level, packRoot));
        }
        Path file = packRoot.resolve(relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The exact bytes of one file of a configured pack, as stored now.
     *
     * @param pack the configured pack's name
     * @param relativePath the path inside the pack
     * @return the file's content
     */
    byte[] bytesOf(String pack, String relativePath) {
        PackLocation location = locations.stream()
                .filter(l -> l.name().equals(pack))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no pack " + pack + " is configured"));
        try {
            return Files.readAllBytes(location.root().resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Configures one of charter's example packs (ADR-0070) as it is held, every file byte for byte, under its own name
     * and at its own level.
     *
     * @param name {@code built-in}, {@code org-base} or {@code project}
     */
    void addExamplePack(String name) {
        if (root == null) {
            throw new IllegalStateException("no scenario is running");
        }
        PackLevel level = ExamplePacks.level(name);
        Path levelRoot = root.resolve(level.token());
        Path packRoot = levelRoot.resolve(name);
        if (locations.stream()
                .anyMatch(l -> l.root().equals(packRoot) || l.name().equals(name))) {
            throw new IllegalStateException("the pack " + name + " is configured already");
        }
        try {
            Files.createDirectories(levelRoot);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        locations.add(new PackLocation(name, level, ExamplePacks.copyOf(name, levelRoot)));
    }

    /**
     * The bytes of one file of a configured pack, as stored now; the file must exist.
     *
     * @param pack the configured name of the pack
     * @param relativePath the file's path below the pack directory
     * @return its bytes
     */
    byte[] fileOf(String pack, String relativePath) {
        PackLocation location = locations.stream()
                .filter(l -> l.name().equals(pack))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no pack " + pack + " is configured"));
        try {
            return Files.readAllBytes(location.root().resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deletes one file of a configured pack, which must exist: a scenario that deletes nothing proves nothing.
     *
     * @param pack the configured name of the pack
     * @param relativePath the file's path below the pack directory
     */
    void deleteFile(String pack, String relativePath) {
        PackLocation location = locations.stream()
                .filter(l -> l.name().equals(pack))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no pack " + pack + " is configured"));
        try {
            Files.delete(location.root().resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Whether a configured pack holds a file or a directory at the path, as stored now.
     *
     * @param pack the configured pack's name
     * @param relativePath the path inside the pack
     * @return whether anything is stored there
     */
    boolean holds(String pack, String relativePath) {
        PackLocation location = locations.stream()
                .filter(l -> l.name().equals(pack))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no pack " + pack + " is configured"));
        return Files.exists(location.root().resolve(relativePath), LinkOption.NOFOLLOW_LINKS);
    }

    /** Plants a failure no refusal names: asking for the packs throws, until the scenario ends. */
    void failUnexpectedly() {
        failing = true;
    }

    List<PackLocation> locations() {
        if (failing) {
            throw new IllegalStateException("a planted failure the response must not show");
        }
        return List.copyOf(locations);
    }
}
