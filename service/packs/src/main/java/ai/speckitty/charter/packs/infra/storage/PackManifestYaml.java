package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The YAML form of {@code pack-manifest.yaml} (ADR-0042): how a {@link PackManifestDso} is written from scratch and
 * read back from a document that has already passed the bundled {@code pack-manifest} schema. Keys are written in
 * schema order ({@code schema_version}, {@code manifest_hash}, {@code constituents}, {@code presets}); {@code presets}
 * is left out when the manifest lists none, as the schema refuses an empty list. Not a {@link YamlFormat}: the manifest
 * is a pack-level file, not a kind.
 */
final class PackManifestYaml {

    /** The schema that validates a manifest, as {@code contracts/packs/file-kinds.yaml} names it. */
    static final String SCHEMA = "pack-manifest";

    private PackManifestYaml() {}

    /**
     * Maps a schema-valid document to the persistence shape.
     *
     * @param root the document's root mapping
     * @return the shape
     */
    static PackManifestDso read(Map<?, ?> root) {
        List<PackManifestDso.Constituent> constituents = new ArrayList<>();
        for (Object item : list(root, "constituents")) {
            Map<?, ?> entry = (Map<?, ?>) item;
            constituents.add(new PackManifestDso.Constituent(
                    required(entry, "kind"),
                    required(entry, "id"),
                    required(entry, "path"),
                    required(entry, "content_hash")));
        }
        List<PackManifestDso.PresetEntry> presets = new ArrayList<>();
        for (Object item : list(root, "presets")) {
            Map<?, ?> entry = (Map<?, ?>) item;
            presets.add(new PackManifestDso.PresetEntry(
                    required(entry, "id"), required(entry, "path"), required(entry, "content_hash")));
        }
        return new PackManifestDso(
                required(root, "schema_version"), required(root, "manifest_hash"), constituents, presets);
    }

    /**
     * Serializes the persistence shape from scratch.
     *
     * @param manifest the shape
     * @return the file text
     */
    static String write(PackManifestDso manifest) {
        List<Map<String, Object>> constituents = new ArrayList<>();
        for (PackManifestDso.Constituent constituent : manifest.constituents()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("kind", constituent.kind());
            out.put("id", constituent.id());
            out.put("path", constituent.path());
            out.put("content_hash", constituent.contentHash());
            constituents.add(out);
        }
        List<Map<String, Object>> presets = new ArrayList<>();
        for (PackManifestDso.PresetEntry preset : manifest.presets()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("id", preset.id());
            out.put("path", preset.path());
            out.put("content_hash", preset.contentHash());
            presets.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("schema_version", manifest.schemaVersion())
                .scalar("manifest_hash", manifest.manifestHash())
                // An empty constituent list is a value the schema requires, so it is written as [].
                .present("constituents", new ArrayList<Object>(constituents))
                .mappings("presets", presets)
                .dump();
    }
}
