package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The storage rules of a preset file that are not in its schema, shared by the store's load and by validate mode so the
 * two refuse the same files (ADR-0042): a preset's {@code id} is its file stem, and its persistence shape maps to the
 * domain {@link Preset} and back.
 */
final class PresetFiles {

    /** The file kind of a preset, as {@code contracts/packs/file-kinds.yaml} names it. */
    static final String KIND = "preset";

    /** The pack's preset directory, with its trailing slash. */
    static final String PRESETS = "presets/";

    private static final String SUFFIX = ".yaml";

    private static final String ACTIVATED = "activated_";

    private PresetFiles() {}

    /**
     * Why a preset file's declared id is not its file stem, or empty when it is.
     *
     * @param file the pack-relative path of the preset file, {@code presets/<stem>.yaml}
     * @param id the {@code id} the file declares
     * @return the reason, {@code preset id '<id>' is not its file stem '<stem>'}; empty when they agree
     */
    static Optional<String> stemMismatch(String file, String id) {
        return FileStems.mismatch(KIND, file, SUFFIX, id);
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
        dso.lists()
                .forEach((key, items) -> artifacts.put(
                        kindOf(key.substring(ACTIVATED.length())),
                        items.stream()
                                .map(item -> new PresetItem(item.id(), item.mustHave()))
                                .toList()));
        return new Preset(dso.id(), dso.description(), artifacts);
    }

    /**
     * The persistence shape of a domain preset, its lists in the kind registry's order.
     *
     * @param preset the preset
     * @return the shape
     */
    static PresetDso toDso(Preset preset) {
        Map<String, List<PresetDso.Item>> lists = new LinkedHashMap<>();
        for (ArtifactKind kind : Preset.ACTIVATION_KINDS) {
            List<PresetItem> items = preset.artifacts().getOrDefault(kind, List.of());
            if (!items.isEmpty()) {
                lists.put(
                        PresetYaml.keyOf(kind),
                        items.stream()
                                .map(item -> new PresetDso.Item(item.id(), item.mustHave()))
                                .toList());
            }
        }
        return new PresetDso("1.0", preset.id(), preset.description(), lists);
    }

    private static ArtifactKind kindOf(String plural) {
        return ArtifactKind.fromPlural(plural)
                .orElseThrow(
                        () -> new IllegalStateException("'" + plural + "' passed the preset schema but is no kind"));
    }
}
