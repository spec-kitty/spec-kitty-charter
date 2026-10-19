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
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** The packs of the running scenario, written to a fresh directory that goes when the scenario ends. */
public final class ScenarioPacks {

    private @Nullable Path root;
    private final List<PackLocation> locations = new ArrayList<>();

    /** Starts a scenario: deletes the previous scenario's directory and creates an empty one. */
    void reset() {
        delete();
        try {
            root = Files.createTempDirectory("charter-scenario");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ends a scenario: deletes its directory and forgets its packs; nothing happens when there is none. */
    void delete() {
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

    List<PackLocation> locations() {
        return List.copyOf(locations);
    }
}
