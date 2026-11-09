package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The required floor of a composition closure (FR-023): every must-have item of every governing preset the closure
 * holds, each with the packs that require it. A requirement is authored as a preset (ADR-0031, ADR-0042), so a
 * convenience preset, and a convenience item of a governing preset, require nothing.
 *
 * <p>The floor is closure-scoped: only the presets of the closure's packs count (planning decision
 * {@code DM-01M6PCWSA65497T9A7J80K6V56}, an assumed default flagged to the operator). The rule lives in {@link #of} alone,
 * so a later ruling changes one place.
 *
 * @param requiredBy each required URN, mapped to the descriptor {@code pack_id}s whose governing presets require it,
 *     each pack once, in closure order
 */
public record RequiredFloor(Map<Urn, List<String>> requiredBy) {

    /**
     * Checks and copies the map.
     *
     * @param requiredBy each required URN with its requiring packs
     */
    public RequiredFloor {
        Objects.requireNonNull(requiredBy, "requiredBy");
        Map<Urn, List<String>> copy = new LinkedHashMap<>();
        requiredBy.forEach((urn, packs) -> copy.put(urn, List.copyOf(packs)));
        requiredBy = Collections.unmodifiableMap(copy);
    }

    /**
     * The floor of a closure: for each pack, in closure order, the must-have artifact items ({@code <kind>:<id>}) and
     * the must-have mission types ({@code mission_type:<id>}) of its presets.
     *
     * @param presetsByPackInOrder each closure pack's descriptor {@code pack_id} with the presets it holds, in closure
     *     order
     * @return the floor; empty when no preset of the closure governs
     */
    public static RequiredFloor of(List<Map.Entry<String, List<Preset>>> presetsByPackInOrder) {
        Objects.requireNonNull(presetsByPackInOrder, "presetsByPackInOrder");
        Map<Urn, List<String>> requiredBy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Preset>> pack : presetsByPackInOrder) {
            for (Preset preset : pack.getValue()) {
                mustHaveOf(preset).forEach(urn -> require(requiredBy, urn, pack.getKey()));
            }
        }
        return new RequiredFloor(requiredBy);
    }

    /**
     * Whether a governing preset of the closure requires the URN.
     *
     * @param urn the item
     * @return {@code true} when some pack of the closure requires it
     */
    public boolean isRequired(Urn urn) {
        return requiredBy.containsKey(Objects.requireNonNull(urn, "urn"));
    }

    /**
     * The packs that require the URN.
     *
     * @param urn the item
     * @return their descriptor {@code pack_id}s, in closure order; empty when no pack requires it
     */
    public List<String> requiringPacks(Urn urn) {
        return requiredBy.getOrDefault(Objects.requireNonNull(urn, "urn"), List.of());
    }

    /** The must-have items of one preset, artifacts first, then mission types; none for a convenience preset. */
    private static List<Urn> mustHaveOf(Preset preset) {
        List<Urn> items = new ArrayList<>();
        preset.required().forEach((kind, ids) -> ids.forEach(id -> items.add(new Urn(NodeKind.of(kind), id))));
        preset.requiredMissionTypes().forEach(id -> items.add(Urn.missionType(id)));
        return items;
    }

    private static void require(Map<Urn, List<String>> requiredBy, Urn urn, String packId) {
        List<String> packs = requiredBy.computeIfAbsent(urn, ignored -> new ArrayList<>());
        if (!packs.contains(packId)) {
            packs.add(packId);
        }
    }
}
