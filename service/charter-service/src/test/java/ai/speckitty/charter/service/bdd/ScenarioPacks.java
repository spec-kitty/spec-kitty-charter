package ai.speckitty.charter.service.bdd;

import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.yaml.PackLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The packs of the running scenario, written to a fresh directory. */
public final class ScenarioPacks {

    private Path root;
    private final List<PackLocation> locations = new ArrayList<>();

    void reset() {
        try {
            root = Files.createTempDirectory("charter-scenario");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        locations.clear();
    }

    void addFile(PackLevel level, String pack, String relativePath, String content) {
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
