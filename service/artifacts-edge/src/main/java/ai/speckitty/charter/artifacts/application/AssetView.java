package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One asset of a pack as the edge speaks of it (#105): the descriptor its sidecar declares, in the edge's own words, so
 * that the inbound adapter names no packs type. The packs adapter maps the packs slice's asset into it, and judges the
 * mime by the packs domain's one media-type rule ({@link #wellFormedMime()}), the rule validate applies.
 *
 * @param id the asset's id within its pack
 * @param packId the pack that holds the asset, by the identifier the request named it with
 * @param mime the media type exactly as the sidecar writes it
 * @param wellFormedMime whether {@code mime} is a well-formed {@code type/subtype} by the packs domain's rule
 * @param path the payload's path, relative to the pack's {@code assets/} directory
 * @param title the title, empty when the sidecar declares none
 * @param executable how the asset runs, empty when it does not declare it
 */
public record AssetView(
        String id,
        PackIdentifier packId,
        String mime,
        boolean wellFormedMime,
        String path,
        Optional<String> title,
        Optional<Executable> executable) {

    /**
     * Checks the fields.
     *
     * @param id the id
     * @param packId the pack
     * @param mime the mime
     * @param wellFormedMime whether the mime is well-formed
     * @param path the payload path
     * @param title the title
     * @param executable the executable block
     */
    public AssetView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(mime, "mime");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(executable, "executable");
    }

    /**
     * How an executable asset runs (ADR-0054 decision 5).
     *
     * @param runtime the runtime's token, for example {@code python}
     * @param minVersion the lowest runtime version, empty when the sidecar states none
     * @param entry the entry the runtime is handed, a payload file of the same pack
     * @param args the arguments after the entry, in order
     */
    public record Executable(String runtime, Optional<String> minVersion, String entry, List<String> args) {

        /**
         * Checks and copies the fields.
         *
         * @param runtime the runtime token
         * @param minVersion the minimum version
         * @param entry the entry
         * @param args the arguments
         */
        public Executable {
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(minVersion, "minVersion");
            Objects.requireNonNull(entry, "entry");
            args = List.copyOf(Objects.requireNonNull(args, "args"));
        }
    }
}
