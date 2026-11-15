package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.util.List;
import java.util.Objects;

/**
 * The effective agent profile under an activation (R8, #102; rulings D5 to D7 of ADR-0078), as the packs slice composes
 * it: each profile of the in-force {@code specializes_from} chain merged with its own in-force enhancers, then the chain
 * folded root to leaf. A profile that enhances another agent profile is answered as its pack stores it.
 *
 * @param profile the effective profile
 * @param lineage the in-force ancestors, nearest first
 * @param enhances the ids of the agent profiles it enhances, in id order; empty for a profile that enhances none
 */
public record ProfileResolution(CharterArtifact profile, List<Lineage> lineage, List<String> enhances) {

    /**
     * One ancestor of the lineage chain.
     *
     * @param id the ancestor's id
     * @param pack the configured identifier of the pack that holds the version in force
     * @param depth the length of the longest in-force {@code specializes_from} path from the profile, at least 1
     */
    public record Lineage(String id, String pack, int depth) {

        /**
         * Checks the fields.
         *
         * @param id the ancestor
         * @param pack the holding pack
         * @param depth the longest path length
         */
        public Lineage {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(pack, "pack");
            if (depth < 1) {
                throw new IllegalArgumentException("an ancestor is at least one hop away: " + depth);
            }
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param profile the effective profile
     * @param lineage the ancestors
     * @param enhances the enhanced profiles
     */
    public ProfileResolution {
        Objects.requireNonNull(profile, "profile");
        lineage = List.copyOf(Objects.requireNonNull(lineage, "lineage"));
        enhances = List.copyOf(Objects.requireNonNull(enhances, "enhances"));
    }
}
