package ai.speckitty.charter.packvalidation.placement;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackReadout;
import java.util.Objects;
import java.util.Optional;

/**
 * What the placement rules judge (#554, S2, S6): one read of a pack, as the domain port returns it, and the level the
 * pack is configured at. The rules read the descriptor and the fragment through {@code read().contents()}; they read
 * no file.
 *
 * @param read the readout of the pack
 * @param configuredLevel the level the pack is configured at; empty for a bare path
 */
public record Placement(PackReadout read, Optional<PackLevel> configuredLevel) {
    /**
     * Checks that every field is present.
     *
     * @param read the readout
     * @param configuredLevel the configured level, possibly empty
     */
    public Placement {
        Objects.requireNonNull(read, "read");
        Objects.requireNonNull(configuredLevel, "configuredLevel");
    }

    /**
     * The name the pack is configured under, which the placement findings name.
     *
     * @return the pack's configured name
     */
    public String name() {
        return read.contents().root().name();
    }
}
