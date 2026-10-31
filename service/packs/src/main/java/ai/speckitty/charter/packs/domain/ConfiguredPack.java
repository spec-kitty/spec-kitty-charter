package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.Objects;

/**
 * One pack the service is configured with: the identifier it is configured under and the level it is configured at.
 * The level is the configured one, the level storage serves the pack by, never the one its {@code pack.yaml} declares;
 * the pack policy reports a difference between the two.
 *
 * @param id the configured identifier of the pack
 * @param level the level the pack is configured at
 */
public record ConfiguredPack(PackIdentifier id, PackLevel level) {

    /**
     * A configured pack.
     *
     * @param id the configured identifier
     * @param level the configured level
     */
    public ConfiguredPack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(level, "level");
    }
}
