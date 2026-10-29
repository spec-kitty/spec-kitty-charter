package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of {@code pack.yaml}: a flat mirror of its fields, not the domain descriptor. Its record
 * equality carries the round-trip fidelity assertion, {@code active_charter} and {@code pack_drg} included.
 *
 * <p>The schema is closed (ADR-0036 decision 3): a {@code pack.yaml} with a key it does not declare is unreadable, so
 * every key the file holds is one of these fields and a write loses nothing.
 *
 * @param packId the {@code pack_id}
 * @param packVersion the {@code pack_version}
 * @param parentPack the {@code parent_pack}, when stated
 * @param name the {@code name}
 * @param packLevel the {@code pack_level} token as written, when stated
 * @param requiresCore the {@code requires_core} text as written, when stated
 * @param activeCharter the {@code active_charter} path, when stated
 * @param packDrg the {@code pack_drg} path, when stated
 */
record PackDescriptorDso(
        String packId,
        String packVersion,
        Optional<String> parentPack,
        String name,
        Optional<String> packLevel,
        Optional<String> requiresCore,
        Optional<String> activeCharter,
        Optional<String> packDrg) {

    /**
     * Checks the fields.
     *
     * @param packId the pack id
     * @param packVersion the pack version
     * @param parentPack the parent pack
     * @param name the name
     * @param packLevel the level token
     * @param requiresCore the core requirement
     * @param activeCharter the active charter path
     * @param packDrg the pack DRG path
     */
    PackDescriptorDso {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(parentPack, "parentPack");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(packLevel, "packLevel");
        Objects.requireNonNull(requiresCore, "requiresCore");
        Objects.requireNonNull(activeCharter, "activeCharter");
        Objects.requireNonNull(packDrg, "packDrg");
    }

    /**
     * The persistence shape of a domain descriptor; the one owner of this mapping.
     *
     * @param descriptor the descriptor
     * @return the shape
     */
    static PackDescriptorDso of(PackDescriptor descriptor) {
        return new PackDescriptorDso(
                descriptor.packId(),
                descriptor.packVersion(),
                descriptor.parentPack(),
                descriptor.name(),
                descriptor.declaredLevel().map(PackLevel::token),
                descriptor.requiresCore(),
                descriptor.paths().activeCharter(),
                descriptor.paths().packDrg());
    }

    /**
     * The domain descriptor this shape holds.
     *
     * @return the descriptor
     */
    PackDescriptor toDescriptor() {
        return new PackDescriptor(
                packId,
                packVersion,
                parentPack,
                name,
                packLevel.flatMap(PackLevel::fromToken),
                requiresCore,
                new PackPaths(activeCharter, packDrg));
    }
}
