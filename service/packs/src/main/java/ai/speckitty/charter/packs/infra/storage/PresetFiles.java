package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The storage rules of a preset file that are not in its schema, shared by the store's load and by validate mode so the
 * two refuse the same files (ADR-0043): a preset's {@code id} is its file stem, and its persistence shape maps to the
 * domain {@link Preset} and back.
 */
final class PresetFiles {

    /** The file kind of a preset, as {@code contracts/packs/file-kinds.yaml} names it. */
    static final String KIND = "preset";

    /** The pack's preset directory, with its trailing slash. */
    static final String PRESETS = "presets/";

    private static final String SUFFIX = ".yaml";

    private static final String ACTIVATED = "activated_";

    private static final String MISSION_TYPES = "activated_mission_types";

    private PresetFiles() {}

    /**
     * Why a preset file's declared id is not its file stem, or empty when it is.
     *
     * @param file the pack-relative path of the preset file, {@code presets/<stem>.yaml}
     * @param id the {@code id} the file declares
     * @return the reason, {@code preset id '<id>' is not its file stem '<stem>'}; empty when they agree
     */
    static Optional<String> stemMismatch(String file, String id) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        String stem = name.endsWith(SUFFIX) ? name.substring(0, name.length() - SUFFIX.length()) : name;
        return stem.equals(id)
                ? Optional.empty()
                : Optional.of("preset id '" + id + "' is not its file stem '" + stem + "'");
    }

    /**
     * Why a manifest's preset entry does not name its own preset's file, or empty when it does: the entry's
     * {@code path} must be {@code presets/<id>.yaml}, which the schema's pattern cannot tie to the entry's {@code id}
     * (research R14). The whole-pack load, validate mode and the gate apply it alike.
     *
     * @param id the entry's {@code id}
     * @param path the entry's {@code path}
     * @return the reason, {@code preset entry '<id>' names <path>, not presets/<id>.yaml}; empty when they agree
     */
    static Optional<String> entryPathMismatch(String id, String path) {
        String expected = pathOf(id);
        return expected.equals(path)
                ? Optional.empty()
                : Optional.of("preset entry '" + id + "' names " + path + ", not " + expected);
    }

    /**
     * The pack-relative path a preset is written to.
     *
     * @param id the preset's id
     * @return {@code presets/<id>.yaml}
     */
    static String pathOf(String id) {
        return PRESETS + id + SUFFIX;
    }

    /**
     * The domain preset a persistence shape holds.
     *
     * @param dso the shape, from a schema-valid file
     * @return the preset
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when a domain invariant is breached
     */
    static Preset toPreset(PresetDso dso) {
        Map<ArtifactKind, List<PresetItem>> artifacts = new EnumMap<>(ArtifactKind.class);
        List<PresetItem> missionTypes = List.of();
        for (Map.Entry<String, List<PresetDso.Item>> list : dso.lists().entrySet()) {
            List<PresetItem> items = list.getValue().stream()
                    .map(item -> new PresetItem(item.id(), item.mustHave()))
                    .toList();
            if (MISSION_TYPES.equals(list.getKey())) {
                missionTypes = items;
            } else {
                artifacts.put(kindOf(list.getKey().substring(ACTIVATED.length())), items);
            }
        }
        return new Preset(
                dso.id(),
                dso.description(),
                dso.activatedKinds().stream().map(PresetFiles::kindOf).toList(),
                artifacts,
                missionTypes);
    }

    /**
     * The persistence shape of a domain preset, its lists in the schema's key order.
     *
     * @param preset the preset
     * @return the shape
     */
    static PresetDso toDso(Preset preset) {
        Map<String, List<PresetDso.Item>> lists = new LinkedHashMap<>();
        for (String key : PresetYaml.LIST_KEYS) {
            List<PresetItem> items = MISSION_TYPES.equals(key)
                    ? preset.missionTypes()
                    : preset.artifacts().getOrDefault(kindOf(key.substring(ACTIVATED.length())), List.of());
            if (!items.isEmpty()) {
                List<PresetDso.Item> out = new ArrayList<>();
                items.forEach(item -> out.add(new PresetDso.Item(item.id(), item.mustHave())));
                lists.put(key, out);
            }
        }
        return new PresetDso(
                "1.0",
                preset.id(),
                preset.description(),
                preset.activatedKinds().stream().map(ArtifactKind::plural).toList(),
                lists);
    }

    private static ArtifactKind kindOf(String plural) {
        return ArtifactKind.fromPlural(plural)
                .orElseThrow(
                        () -> new IllegalStateException("'" + plural + "' passed the preset schema but is no kind"));
    }
}
