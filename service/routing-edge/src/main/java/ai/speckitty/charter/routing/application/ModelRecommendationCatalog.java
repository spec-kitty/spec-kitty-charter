package ai.speckitty.charter.routing.application;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.Objects;

/**
 * The routing catalog in force that answered a recommendation: its id, and the pack and level that hold the winner.
 *
 * @param id the catalog's id
 * @param packId the configured id of the pack that holds it
 * @param level that pack's level
 */
public record ModelRecommendationCatalog(String id, String packId, PackLevel level) {

    /**
     * Checks the fields.
     *
     * @param id the id
     * @param packId the pack
     * @param level the level
     */
    public ModelRecommendationCatalog {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(level, "level");
    }
}
