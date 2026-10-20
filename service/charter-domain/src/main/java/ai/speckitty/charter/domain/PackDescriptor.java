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
 */
public record PackDescriptor(
        String packId,
        String packVersion,
        Optional<String> parentPack,
        String name,
        Optional<PackLevel> declaredLevel,
        Optional<String> requiresCore,
        List<String> undeclaredKeys) {
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
     */
    public PackDescriptor {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(parentPack, "parentPack");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(declaredLevel, "declaredLevel");
        Objects.requireNonNull(requiresCore, "requiresCore");
        undeclaredKeys = List.copyOf(undeclaredKeys);
    }
}
