package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The YAML form of {@code presets/<id>.yaml} (ADR-0042): how a {@link PresetDso} is written from scratch and read back
 * from a document that has already passed the bundled {@code preset} schema. Keys are written in schema order;
 * {@code must_have} is written only on a must-have item, as the schema has no {@code false} spelling, and a list the
 * preset does not hold is left out, as the schema has no empty one. Not a {@link YamlFormat}: a preset is a pack-level
 * file, not a kind.
 */
final class PresetYaml {

    /** The schema that validates a preset, as {@code contracts/packs/file-kinds.yaml} names it. */
    static final String SCHEMA = "preset";

    /** The activation-list keys, in the schema's order. */
    static final List<String> LIST_KEYS = List.of(
            "activated_directives",
            "activated_tactics",
            "activated_styleguides",
            "activated_toolguides",
            "activated_paradigms",
            "activated_procedures",
            "activated_agent_profiles",
            "activated_mission_step_contracts",
            "activated_anti_patterns",
            "activated_mission_types");

    private static final String MUST_HAVE = "must_have";

    private PresetYaml() {}

    /**
     * Maps a schema-valid document to the persistence shape.
     *
     * @param root the document's root mapping
     * @return the shape
     */
    static PresetDso read(Map<?, ?> root) {
        Map<String, List<PresetDso.Item>> lists = new LinkedHashMap<>();
        for (String key : LIST_KEYS) {
            if (root.containsKey(key)) {
                List<PresetDso.Item> items = new ArrayList<>();
                for (Object item : list(root, key)) {
                    Map<?, ?> entry = (Map<?, ?>) item;
                    items.add(new PresetDso.Item(required(entry, "id"), Boolean.TRUE.equals(entry.get(MUST_HAVE))));
                }
                lists.put(key, items);
            }
        }
        return new PresetDso(
                required(root, "schema_version"),
                required(root, "id"),
                required(root, "description"),
                strings(root, "activated_kinds"),
                lists);
    }

    /**
     * Serializes the persistence shape from scratch.
     *
     * @param preset the shape
     * @return the file text
     */
    static String write(PresetDso preset) {
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("schema_version", preset.schemaVersion())
                .scalar("id", preset.id())
                .scalar("description", preset.description())
                .strings("activated_kinds", preset.activatedKinds());
        for (String key : LIST_KEYS) {
            List<PresetDso.Item> items = preset.lists().getOrDefault(key, List.of());
            List<Map<String, Object>> out = new ArrayList<>();
            for (PresetDso.Item item : items) {
                Map<String, Object> entry = YamlDumpEmitter.mapping();
                entry.put("id", item.id());
                if (item.mustHave()) {
                    entry.put(MUST_HAVE, true);
                }
                out.add(entry);
            }
            emitter.mappings(key, out);
        }
        return emitter.dump();
    }
}
