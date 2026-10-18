package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.PackLevel;
import java.nio.file.Path;
import java.util.Objects;

/** One configured pack: the name and level it is configured with, and its directory. */
public record PackLocation(String name, PackLevel level, Path root) {
    public PackLocation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(root, "root");
    }
}
