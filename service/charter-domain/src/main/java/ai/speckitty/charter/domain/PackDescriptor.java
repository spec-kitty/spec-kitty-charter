package ai.speckitty.charter.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The authored {@code pack.yaml} of a Charter Pack, as facts: what the file states, with nothing defaulted.
 *
 * @param packId the pack's {@code pack_id}
 * @param packVersion the pack's {@code pack_version}
 * @param parentPack the pack's {@code parent_pack}; empty for a root pack
 * @param name the pack's {@code name}
 * @param declaredLevel the pack's {@code pack_level}; empty when the file declares none
 * @param requiresCore the raw {@code requires_core} text; empty when the file has none
 * @param undeclaredKeys the keys the file has that the schema does not declare, sorted
 * @param activeCharter the pack-relative {@code active_charter} path; empty when absent or null
 * @param packDrg the pack-relative {@code pack_drg} path; empty when absent or null
 */
public record PackDescriptor(
        String packId,
        String packVersion,
        Optional<String> parentPack,
        String name,
        Optional<PackLevel> declaredLevel,
        Optional<String> requiresCore,
        List<String> undeclaredKeys,
        Optional<String> activeCharter,
        Optional<String> packDrg) {
    /**
     * Builds a descriptor that declares neither {@code active_charter} nor {@code pack_drg}.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param declaredLevel the declared level, possibly empty
     * @param requiresCore the raw {@code requires_core}, possibly empty
     * @param undeclaredKeys the undeclared keys
     */
    public PackDescriptor(
            String packId,
            String packVersion,
            Optional<String> parentPack,
            String name,
            Optional<PackLevel> declaredLevel,
            Optional<String> requiresCore,
            List<String> undeclaredKeys) {
        this(
                packId,
                packVersion,
                parentPack,
                name,
                declaredLevel,
                requiresCore,
                undeclaredKeys,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Checks that every field is present and copies the keys.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param declaredLevel the declared level, possibly empty
     * @param requiresCore the raw {@code requires_core}, possibly empty
     * @param undeclaredKeys the undeclared keys
     * @param activeCharter the {@code active_charter} path, possibly empty
     * @param packDrg the {@code pack_drg} path, possibly empty
     */
    public PackDescriptor {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(parentPack, "parentPack");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(declaredLevel, "declaredLevel");
        Objects.requireNonNull(requiresCore, "requiresCore");
        Objects.requireNonNull(activeCharter, "activeCharter");
        Objects.requireNonNull(packDrg, "packDrg");
        undeclaredKeys = List.copyOf(undeclaredKeys);
    }
}
