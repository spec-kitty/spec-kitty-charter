package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.Objects;

/**
 * One pack the service is configured with: the identifier it is configured under and the level storage serves it at.
 * The configuration root's pack domain serves a pack at the {@code pack_level} its {@code pack.yaml} declares, and at
 * org when there is no readable declaration (ADR-0038 decision 3); a storage that assigns levels itself serves its own.
 * Nothing above storage recomputes the level from {@code pack.yaml}; the pack policy reports a difference between the
 * two.
 *
 * @param id the configured identifier of the pack
 * @param level the level storage serves the pack at
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
