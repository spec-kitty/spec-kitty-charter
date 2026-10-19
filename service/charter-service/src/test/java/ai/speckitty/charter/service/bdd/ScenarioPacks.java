package ai.speckitty.charter.service.bdd;

import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.yaml.PackLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            Files.writeString(file, content.endsWith("\n") ? content : content + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
