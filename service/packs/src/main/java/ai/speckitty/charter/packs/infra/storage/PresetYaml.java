package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.Preset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of {@code presets/<id>.yaml} (ADR-0042): how a {@link PresetDso} is written from scratch and read back
 * from a document that has already passed the bundled {@code preset} schema, which refuses the removed kind gate
 * {@code activated_kinds}. The scalar keys are written first, then the lists in the kind registry's order;
 * {@code must_have} is written only on a must-have item, as the schema has no {@code false} spelling, and a list the
 * preset does not hold is left out, as the schema has no empty one. Not a {@link YamlFormat}: a preset is a pack-level
 * file, not a kind.
 */
final class PresetYaml {

    /** The schema that validates a preset, as {@code contracts/packs/file-kinds.yaml} names it. */
    static final String SCHEMA = "preset";

    /**
     * The activation-list keys, {@code activated_<plural>} of every kind a preset may name, in the kernel registry's
     * order (one kind model, IC-20): skills, glossary packs and mission types are lists like any other.
     */
    static final List<String> LIST_KEYS =
            Preset.ACTIVATION_KINDS.stream().map(PresetYaml::keyOf).toList();

    private static final String MUST_HAVE = "must_have";

    private static final String ACTIVATED = "activated_";

    private PresetYaml() {}

    /**
     * The activation-list key of a kind.
     *
     * @param kind the kind
     * @return {@code activated_<plural>}
     */
    static String keyOf(ArtifactKind kind) {
        return ACTIVATED + kind.plural();
    }

    /**
     * The kind of an activation-list key, the inverse of {@link #keyOf(ArtifactKind)}.
     *
     * @param key an {@code activated_<plural>} key
     * @return the kind
     * @throws IllegalStateException when the key is no activation-list key of a kind
     */
    static ArtifactKind kindOf(String key) {
        return Optional.of(key)
                .filter(candidate -> candidate.startsWith(ACTIVATED))
                .flatMap(candidate -> ArtifactKind.fromPlural(candidate.substring(ACTIVATED.length())))
                .orElseThrow(() -> new IllegalStateException("'" + key + "' passed the preset schema but is no kind"));
    }

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
                required(root, "schema_version"), required(root, "id"), required(root, "description"), lists);
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
                .scalar("description", preset.description());
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
