package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One configured pack: the name and level it is configured with, and its directory.
 *
 * @param name the name the pack is configured under
 * @param level the level the pack is configured at
 * @param root the pack's directory
 */
public record PackLocation(String name, PackLevel level, Path root) {
    /**
     * Checks that every field is present.
     *
     * @param name the pack's name
     * @param level the pack's level
     * @param root the pack's directory; it need not exist yet, loading refuses when it does not
     */
    public PackLocation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(root, "root");
    }
}
