package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the layer merge answers for one composed closure (FR-009 to FR-012): which version of each held artifact is in
 * force and every version a later level replaced. It is a value of {@link LayerMerge#merge}, so the records travel with
 * the winners and no second channel can disagree (research R3).
 *
 * @param winners each held URN with the version in force, in the order the closure first holds the URNs
 * @param layerOverrides every version a later level replaced, in the order the closure first holds the artifacts and,
 *     per artifact, from the nearest built-in level to the farthest
 */
public record MergedClosure(Map<Urn, Winner> winners, List<LayerOverride> layerOverrides) {

    /** A closure that holds nothing: no winner and no override. */
    public static final MergedClosure EMPTY = new MergedClosure(Map.of(), List.of());

    /**
     * The version of an artifact in force.
     *
     * @param urn the artifact
     * @param packId the configured identifier of the pack that holds the version in force
     * @param descriptorPackId the {@code pack_id} that pack's descriptor declares
     * @param level the level that pack is configured at
     * @param policy the artifact's mutability level along the closure, and the pack that set it
     *     ({@link PerPackPolicy#levelIn})
     */
    public record Winner(
            Urn urn, PackIdentifier packId, String descriptorPackId, PackLevel level, PerPackPolicy.Level policy) {

        /**
         * Checks the fields.
         *
         * @param urn the artifact
         * @param packId the holding pack's configured identifier
         * @param descriptorPackId the holding pack's descriptor {@code pack_id}
         * @param level the holding pack's level
         * @param policy the artifact's level along the closure
         */
        public Winner {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(policy, "policy");
        }
    }

    /**
     * Checks and copies the fields.
     *
     * @param winners each held URN with its winner
     * @param layerOverrides every replaced version
     */
    public MergedClosure {
        winners = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(winners, "winners")));
        layerOverrides = List.copyOf(Objects.requireNonNull(layerOverrides, "layerOverrides"));
    }

    /**
     * The version of an artifact in force.
     *
     * @param urn the artifact
     * @return its winner; empty when no pack of the closure holds it
     */
    public Optional<Winner> winnerOf(Urn urn) {
        return Optional.ofNullable(winners.get(Objects.requireNonNull(urn, "urn")));
    }
}
