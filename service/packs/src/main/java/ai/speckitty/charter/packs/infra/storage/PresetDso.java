package ai.speckitty.charter.packs.infra.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The persistence shape of {@code presets/<id>.yaml}, in the sanitized shape of ADR-0042: a flat mirror of the
 * {@code preset} schema, not the domain {@link ai.speckitty.charter.packs.domain.Preset}.
 *
 * @param schemaVersion the {@code schema_version}, {@code "1.0"}
 * @param id the {@code id}, the file stem
 * @param description the {@code description}
 * @param activatedKinds the {@code activated_kinds} plurals as written; empty when the key is absent
 * @param lists each {@code activated_<plural>} list present, keyed by its YAML key, in the schema's key order
 */
record PresetDso(
        String schemaVersion,
        String id,
        String description,
        List<String> activatedKinds,
        Map<String, List<Item>> lists) {

    /**
     * One item of an activation list.
     *
     * @param id the {@code id}
     * @param mustHave whether the item carries {@code must_have: true}
     */
    record Item(String id, boolean mustHave) {
        /**
         * Checks the id.
         *
         * @param id the id
         * @param mustHave the mark
         */
        Item {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * Checks the fields and copies the collections, keeping the lists' order.
     *
     * @param schemaVersion the schema version
     * @param id the id
     * @param description the description
     * @param activatedKinds the kind gate
     * @param lists the activation lists
     */
    PresetDso {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        activatedKinds = List.copyOf(activatedKinds);
        Map<String, List<Item>> copy = new LinkedHashMap<>();
        lists.forEach((key, items) -> copy.put(key, List.copyOf(items)));
        lists = Collections.unmodifiableMap(copy);
    }
}
