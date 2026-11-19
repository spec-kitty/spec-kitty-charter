package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One configured pack: the name and level it is configured with, and its directory.
 *
 * @param name the name the pack is configured under: a pack id ({@link PackIdentifier#isPackId}), the name the
 *     service registers it under (#583)
 * @param level the level the pack is configured at
 * @param root the pack's directory
 */
public record PackLocation(String name, PackLevel level, Path root) {
    /**
     * Checks that every field is present and that the name is a pack id. The locations refuse a directory whose name
     * is no pack id with {@code pack_id_invalid} before they build its location, so a location never holds one.
     *
     * @param name the pack's name
     * @param level the pack's level
     * @param root the pack's directory; it need not exist yet, loading refuses when it does not
     * @throws IllegalArgumentException when the name is no pack id
     */
    public PackLocation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(root, "root");
        if (!PackIdentifier.isPackId(name)) {
            throw new IllegalArgumentException(
                    "A pack location is named by a pack id, not '" + name + "'. " + PackIdentifier.GRAMMAR_DETAIL);
        }
    }

    /**
     * The registered id the pack is configured under.
     *
     * @return the name, as a pack identifier
     */
    public PackIdentifier id() {
        return new PackIdentifier(name);
    }
}
