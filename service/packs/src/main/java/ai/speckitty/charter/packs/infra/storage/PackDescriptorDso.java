package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of {@code pack.yaml}: a flat mirror of its fields, not the domain descriptor. Its record
 * equality carries the round-trip fidelity assertion, {@code active_charter} and {@code pack_drg} included.
 *
 * <p>{@code undeclaredKeys} holds the names of top-level keys the schema does not declare. A write cannot restore
 * them (their values are not modelled), so round-trip equality is scoped to declared content: a pack with undeclared
 * keys refetches with none.
 *
 * @param packId the {@code pack_id}
 * @param packVersion the {@code pack_version}
 * @param parentPack the {@code parent_pack}, when stated
 * @param name the {@code name}
 * @param packLevel the {@code pack_level} token as written, when stated
 * @param requiresCore the {@code requires_core} text as written, when stated
 * @param activeCharter the {@code active_charter} path, when stated
 * @param packDrg the {@code pack_drg} path, when stated
 * @param undeclaredKeys the undeclared top-level key names, sorted
 */
record PackDescriptorDso(
        String packId,
        String packVersion,
        Optional<String> parentPack,
        String name,
        Optional<String> packLevel,
        Optional<String> requiresCore,
        Optional<String> activeCharter,
        Optional<String> packDrg,
        List<String> undeclaredKeys) {

    /**
     * Checks the fields and copies the list.
     *
     * @param packId the pack id
     * @param packVersion the pack version
     * @param parentPack the parent pack
     * @param name the name
     * @param packLevel the level token
     * @param requiresCore the core requirement
     * @param activeCharter the active charter path
     * @param packDrg the pack DRG path
     * @param undeclaredKeys the undeclared key names
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
        undeclaredKeys = List.copyOf(undeclaredKeys);
    }
}
