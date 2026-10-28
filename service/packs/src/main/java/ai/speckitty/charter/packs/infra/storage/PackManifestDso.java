package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;

/**
 * The persistence shape of {@code pack-manifest.yaml}, in the sanitized authored-pack shape of ADR-0043: a flat mirror
 * of the {@code pack-manifest} schema. {@link #manifestHash()} and every {@code contentHash} are derived fields: a
 * whole-pack write recomputes them ({@link PackManifests#recompute}), the reader takes them as written, and the gate
 * verifies them. Membership (which constituents and presets are listed, and where) is carried as written.
 *
 * @param schemaVersion the {@code schema_version}, {@code "1.0"}
 * @param manifestHash the {@code manifest_hash}, 64 lowercase hex characters
 * @param constituents the {@code constituents}, in written order
 * @param presets the {@code presets}, in written order; empty when the manifest lists none
 */
record PackManifestDso(
        String schemaVersion, String manifestHash, List<Constituent> constituents, List<PresetEntry> presets) {

    /**
     * One constituent artifact file.
     *
     * @param kind the artifact kind token
     * @param id the artifact's id
     * @param path the file's path relative to the pack directory, {@code /}-separated
     * @param contentHash the derived {@code content_hash}
     */
    record Constituent(String kind, String id, String path, String contentHash) {
        /**
         * Checks the fields.
         *
         * @param kind the kind token
         * @param id the id
         * @param path the path
         * @param contentHash the content hash
         */
        Constituent {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(contentHash, "contentHash");
        }
    }

    /**
     * One preset file.
     *
     * @param id the preset's id
     * @param path the preset's path relative to the pack directory, {@code presets/<id>.yaml}
     * @param contentHash the derived {@code content_hash}
     */
    record PresetEntry(String id, String path, String contentHash) {
        /**
         * Checks the fields.
         *
         * @param id the id
         * @param path the path
         * @param contentHash the content hash
         */
        PresetEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(contentHash, "contentHash");
        }
    }

    /**
     * Checks the fields and copies the lists.
     *
     * @param schemaVersion the schema version
     * @param manifestHash the manifest hash
     * @param constituents the constituents
     * @param presets the presets
     */
    PackManifestDso {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(manifestHash, "manifestHash");
        constituents = List.copyOf(constituents);
        presets = List.copyOf(presets);
    }
}
