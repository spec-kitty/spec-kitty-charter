package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * @param executable the {@code executable} block, when stated (ADR-0054 decision 5)
 */
record AssetDso(
        String id, String mime, String path, @Nullable String title, Optional<Executable> executable) {

    /**
     * Checks that the required fields are present.
     *
     * @param id the id
     * @param mime the mime
     * @param path the path
     * @param title the title, or null
     * @param executable the executable block
     */
    AssetDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mime, "mime");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(executable, "executable");
    }

    /**
     * An asset sidecar without an executable block.
     *
     * @param id the id
     * @param mime the mime
     * @param path the path
     * @param title the title, or null
     */
    AssetDso(String id, String mime, String path, @Nullable String title) {
        this(id, mime, path, title, Optional.empty());
    }

    /**
     * The {@code executable} block, as authored.
     *
     * @param runtime the {@code runtime} token
     * @param minVersion the {@code min_version}, when stated
     * @param entry the {@code entry}
     * @param args the {@code args}, in order
     */
    record Executable(String runtime, Optional<String> minVersion, String entry, List<String> args) {
        /**
         * Checks that every component is present and copies the arguments.
         *
         * @param runtime the runtime
         * @param minVersion the minimum version
         * @param entry the entry
         * @param args the arguments
         */
        Executable {
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(minVersion, "minVersion");
            Objects.requireNonNull(entry, "entry");
            args = List.copyOf(args);
        }
    }
}
