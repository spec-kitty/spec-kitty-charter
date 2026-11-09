package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Objects;

/**
 * One version of an artifact that a later level replaced whole (FR-009, ADR-0059 D3): the artifact, the version in
 * force and the version it replaced. There is one record per losing version, so an id held at all three levels yields
 * two. A sibling contest that a descriptor {@code overrides} entry settles is no layer override and has no record
 * (research R3): the winner's provenance shows it.
 *
 * @param urn the artifact
 * @param winner the version in force
 * @param loser the version it replaced
 */
public record LayerOverride(Urn urn, Side winner, Side loser) {

    /**
     * One version of the artifact: the pack that holds it and that pack's level.
     *
     * @param packId the configured identifier of the holding pack
     * @param level the level the pack is configured at
     */
    public record Side(PackIdentifier packId, PackLevel level) {

        /**
         * Checks the fields.
         *
         * @param packId the holding pack
         * @param level its level
         */
        public Side {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(level, "level");
        }
    }

    /**
     * Checks the fields.
     *
     * @param urn the artifact
     * @param winner the version in force
     * @param loser the version it replaced
     */
    public LayerOverride {
        Objects.requireNonNull(urn, "urn");
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loser, "loser");
    }
}
