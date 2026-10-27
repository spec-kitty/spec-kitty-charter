package ai.speckitty.charter.packs.infra.storage;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The persistence shape of an asset sidecar: a flat mirror of the fields of a {@code *.asset.yaml}, not the domain
 * entity. Its record equality is the round-trip fidelity assertion. The payload the sidecar names is not part of it:
 * the store reads payloads as asset files of the pack.
 *
 * @param id the asset's {@code id}
 * @param mime the declared {@code mime}, as authored
 * @param path the {@code path} of the payload, relative to the pack's {@code assets/} directory, as authored
 * @param title the {@code title}; null when absent or written as {@code null}
 */
record AssetDso(
        String id, String mime, String path, @Nullable String title) {

    /**
     * Checks that the required fields are present.
     *
     * @param id the id
     * @param mime the mime
     * @param path the path
     * @param title the title, or null
     */
    AssetDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mime, "mime");
        Objects.requireNonNull(path, "path");
    }
}
