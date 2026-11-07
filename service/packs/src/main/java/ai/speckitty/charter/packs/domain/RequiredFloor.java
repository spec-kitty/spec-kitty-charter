package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The required floor of a composition closure (FR-023, FR-028): every must-have item of every governing preset the
 * closure holds that some pack of the closure holds, each with the packs that require it. A requirement is authored as
 * a preset (ADR-0031, ADR-0042), so a convenience preset, and a convenience item of a governing preset, require nothing.
 *
 * <p>The floor is closure-scoped: only the presets of the closure's packs count (operator ruling of
 * 2026-11-06, which confirms planning decision {@code DM-01M6PCWSA65497T9A7J80K6V56}). It holds held ids only (A10,
 * decision {@code DM-01M6RA8MG55V8C2V9PXWDTRNQS}, rule C31): a must-have item no pack of the closure holds is dropped
 * from the floor and reported in {@link #unheld()}, never put in force. The rule lives in {@link #of} alone, so a later
 * ruling changes one place.
 *
 * @param requiredBy each required URN, mapped to the descriptor {@code pack_id}s whose governing presets require it,
 *     each pack once, in closure order
 * @param unheld each must-have URN no pack of the closure holds, with the packs whose governing presets name it, each
 *     pack once, in closure order: the report the reads surface
 */
public record RequiredFloor(Map<Urn, List<String>> requiredBy, Map<Urn, List<String>> unheld) {

    /**
     * Checks and copies the maps.
     *
     * @param requiredBy each required URN with its requiring packs
     * @param unheld each unheld must-have URN with its requiring packs
     */
    public RequiredFloor {
        requiredBy = copyOf(Objects.requireNonNull(requiredBy, "requiredBy"));
        unheld = copyOf(Objects.requireNonNull(unheld, "unheld"));
    }

    /**
     * A floor of the given items with nothing reported unheld.
     *
     * @param requiredBy each required URN with its requiring packs
     */
    public RequiredFloor(Map<Urn, List<String>> requiredBy) {
        this(requiredBy, Map.of());
    }

    /**
     * The floor of a closure: for each pack, in closure order, the must-have artifact items ({@code <kind>:<id>}) and
     * the must-have mission types ({@code mission_type:<id>}) of its presets, restricted to the URNs the closure holds;
     * the others are reported unheld.
     *
     * @param presetsByPackInOrder each closure pack's descriptor {@code pack_id} with the presets it holds, in closure
     *     order
     * @param held every URN some pack of the closure holds
     * @return the floor; empty when no preset of the closure governs
     */
    public static RequiredFloor of(List<Map.Entry<String, List<Preset>>> presetsByPackInOrder, Set<Urn> held) {
        Objects.requireNonNull(presetsByPackInOrder, "presetsByPackInOrder");
        Objects.requireNonNull(held, "held");
        Map<Urn, List<String>> requiredBy = new LinkedHashMap<>();
        Map<Urn, List<String>> unheld = new LinkedHashMap<>();
        for (Map.Entry<String, List<Preset>> pack : presetsByPackInOrder) {
            for (Preset preset : pack.getValue()) {
                mustHaveOf(preset)
                        .forEach(urn -> require(held.contains(urn) ? requiredBy : unheld, urn, pack.getKey()));
            }
        }
        return new RequiredFloor(requiredBy, unheld);
    }

    /**
     * Whether a governing preset of the closure requires the URN.
     *
     * @param urn the item
     * @return {@code true} when some pack of the closure requires it and some pack of the closure holds it
     */
    public boolean isRequired(Urn urn) {
        return requiredBy.containsKey(Objects.requireNonNull(urn, "urn"));
    }

    /**
     * The packs that require the URN.
     *
     * @param urn the item
     * @return their descriptor {@code pack_id}s, in closure order; empty when no pack requires it or no pack holds it
     */
    public List<String> requiringPacks(Urn urn) {
        return requiredBy.getOrDefault(Objects.requireNonNull(urn, "urn"), List.of());
    }

    /** The must-have items of one preset, of every kind, mission types included; none for a convenience preset. */
    private static List<Urn> mustHaveOf(Preset preset) {
        List<Urn> items = new ArrayList<>();
        preset.required().forEach((kind, ids) -> ids.forEach(id -> items.add(new Urn(NodeKind.of(kind), id))));
        return items;
    }

    private static void require(Map<Urn, List<String>> requiredBy, Urn urn, String packId) {
        List<String> packs = requiredBy.computeIfAbsent(urn, ignored -> new ArrayList<>());
        if (!packs.contains(packId)) {
            packs.add(packId);
        }
    }

    private static Map<Urn, List<String>> copyOf(Map<Urn, List<String>> byUrn) {
        Map<Urn, List<String>> copy = new LinkedHashMap<>();
        byUrn.forEach((urn, packs) -> copy.put(urn, List.copyOf(packs)));
        return Collections.unmodifiableMap(copy);
    }
}
