package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The authored {@code pack.yaml} of a Charter Pack, as facts: what the file states, with nothing defaulted. The
 * schema is closed, so a key it does not declare never reaches this record (ADR-0036 decision 3).
 *
 * @param packId the pack's {@code pack_id}
 * @param packVersion the pack's {@code pack_version}
 * @param parentPack the pack's {@code parent_pack}; empty for a root pack
 * @param name the pack's {@code name}
 * @param declaredLevel the pack's {@code pack_level}; empty when the file declares none
 * @param requiresCore the raw {@code requires_core} text; empty when the file has none
 * @param paths the declared {@code active_charter} and {@code pack_drg} paths, grouped
 */
public record PackDescriptor(
        String packId,
        String packVersion,
        Optional<String> parentPack,
        String name,
        Optional<PackLevel> declaredLevel,
        Optional<String> requiresCore,
        PackPaths paths) {
    /**
     * Builds a descriptor that declares neither {@code active_charter} nor {@code pack_drg}.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param declaredLevel the declared level, possibly empty
     * @param requiresCore the raw {@code requires_core}, possibly empty
     */
    public PackDescriptor(
            String packId,
            String packVersion,
            Optional<String> parentPack,
            String name,
            Optional<PackLevel> declaredLevel,
            Optional<String> requiresCore) {
        this(packId, packVersion, parentPack, name, declaredLevel, requiresCore, PackPaths.none());
    }

    /**
     * Checks that every field is present.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param declaredLevel the declared level, possibly empty
     * @param requiresCore the raw {@code requires_core}, possibly empty
     * @param paths the declared paths
     */
    public PackDescriptor {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(parentPack, "parentPack");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(declaredLevel, "declaredLevel");
        Objects.requireNonNull(requiresCore, "requiresCore");
        Objects.requireNonNull(paths, "paths");
    }
}
