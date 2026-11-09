package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the layer merge answers for one composed closure (FR-009 to FR-012): which version of each held artifact is in
 * force and every version a later level replaced. It is a value of {@link LayerMerge#merge}, so the records travel with
 * the winners and no second channel can disagree (research R3).
 *
 * @param winners each held URN with the version in force, in the order the closure first holds the URNs
 * @param layerOverrides every version a later level replaced, in the order the closure first holds the artifacts and,
 *     per artifact, from the nearest built-in level to the farthest
 * @param languages each winner's {@code applies_to_languages}, read from the winner's own pack alone; a winner that
 *     declares none is absent
 * @param presets each preset id once, with the copy in force and the must-have items of the copy in force at each level (A1, rule C31)
 * @param floor the must-have items of the copy of every governing preset in force at each level that the merged closure holds, with the
 *     requiring packs; the items it holds no version of are in its {@link RequiredFloor#unheld()} report (A10)
 */
public record MergedClosure(
        Map<Urn, Winner> winners,
        List<LayerOverride> layerOverrides,
        Map<Urn, Set<String>> languages,
        Map<String, MergedPreset> presets,
        RequiredFloor floor) {

    /** A closure that holds nothing: no winner, no override, no preset. */
    public static final MergedClosure EMPTY =
            new MergedClosure(Map.of(), List.of(), Map.of(), Map.of(), new RequiredFloor(Map.of()));

    /**
     * A preset id as the merge answers it (ADR-0053 d6, A1): the copy in force, and the requirement that only ever
     * tightens, computed beside the winner.
     *
     * @param preset the copy held by the last pack in closure order, the one applied
     * @param descriptorPackId the {@code pack_id} of the pack that holds the copy in force
     * @param mustHave the must-have items of the copy of the id in force at each level, each once, in closure order; items no
     *     pack holds included (the floor reports those)
     */
    public record MergedPreset(Preset preset, String descriptorPackId, List<Urn> mustHave) {

        /**
         * Checks and copies the fields.
         *
         * @param preset the copy in force
         * @param descriptorPackId the holding pack
         * @param mustHave the union of the must-have items
         */
        public MergedPreset {
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            mustHave = List.copyOf(Objects.requireNonNull(mustHave, "mustHave"));
        }
    }

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
     * @param languages the languages of each winner
     * @param presets each preset id with its merged requirement
     * @param floor the floor over the copy of every governing preset in force at each level
     */
    public MergedClosure {
        winners = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(winners, "winners")));
        layerOverrides = List.copyOf(Objects.requireNonNull(layerOverrides, "layerOverrides"));
        Map<Urn, Set<String>> languageCopy = new LinkedHashMap<>();
        Objects.requireNonNull(languages, "languages")
                .forEach((urn, declared) ->
                        languageCopy.put(urn, Collections.unmodifiableSet(new LinkedHashSet<>(declared))));
        languages = Collections.unmodifiableMap(languageCopy);
        presets = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(presets, "presets")));
        Objects.requireNonNull(floor, "floor");
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
