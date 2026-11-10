package ai.speckitty.charter.kernel.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
 * @param dependencies the packs this pack declares it depends on; empty when it declares none
 * @param overrides the sibling conflicts this pack settles (any pack of the compose may declare one until #358), each
 *     by the conflicting artifact and the member meant to win; empty when it settles none
 * @param schemaVersion the pack's {@code schema_version} as written; empty for an unversioned (legacy) descriptor
 * @param fallbackProfile the profile-id of the pack's declared fallback agent profile ({@code fallback_profile},
 *     ADR-0068); empty when the pack declares none
 */
public record PackDescriptor(
        String packId,
        String packVersion,
        Optional<String> parentPack,
        String name,
        Optional<PackLevel> declaredLevel,
        Optional<String> requiresCore,
        PackPaths paths,
        List<PackDependency> dependencies,
        List<PackOverride> overrides,
        Optional<String> schemaVersion,
        Optional<String> fallbackProfile) {
    /**
     * Builds a descriptor that declares no fallback profile.
     *
     * @param packId the pack id
     * @param packVersion the pack version
     * @param parentPack the parent pack
     * @param name the name
     * @param declaredLevel the declared level
     * @param requiresCore the core range
     * @param paths the pack-relative paths
     * @param dependencies the dependencies
     * @param overrides the overrides
     * @param schemaVersion the schema version
     */
    public PackDescriptor(
            String packId,
            String packVersion,
            Optional<String> parentPack,
            String name,
            Optional<PackLevel> declaredLevel,
            Optional<String> requiresCore,
            PackPaths paths,
            List<PackDependency> dependencies,
            List<PackOverride> overrides,
            Optional<String> schemaVersion) {
        this(
                packId,
                packVersion,
                parentPack,
                name,
                declaredLevel,
                requiresCore,
                paths,
                dependencies,
                overrides,
                schemaVersion,
                Optional.empty());
    }

    /**
     * Builds an unversioned descriptor: one that states no {@code schema_version}.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param declaredLevel the declared level, possibly empty
     * @param requiresCore the raw {@code requires_core}, possibly empty
     * @param paths the declared paths
     * @param dependencies the declared dependencies
     * @param overrides the declared sibling conflicts this pack settles
     */
    public PackDescriptor(
            String packId,
            String packVersion,
            Optional<String> parentPack,
            String name,
            Optional<PackLevel> declaredLevel,
            Optional<String> requiresCore,
            PackPaths paths,
            List<PackDependency> dependencies,
            List<PackOverride> overrides) {
        this(
                packId,
                packVersion,
                parentPack,
                name,
                declaredLevel,
                requiresCore,
                paths,
                dependencies,
                overrides,
                Optional.empty());
    }

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
        this(
                packId,
                packVersion,
                parentPack,
                name,
                declaredLevel,
                requiresCore,
                PackPaths.none(),
                List.of(),
                List.of(),
                Optional.empty());
    }

    /**
     * Builds a descriptor that declares no dependencies and settles no sibling conflict.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param declaredLevel the declared level, possibly empty
     * @param requiresCore the raw {@code requires_core}, possibly empty
     * @param paths the declared paths
     */
    public PackDescriptor(
            String packId,
            String packVersion,
            Optional<String> parentPack,
            String name,
            Optional<PackLevel> declaredLevel,
            Optional<String> requiresCore,
            PackPaths paths) {
        this(packId, packVersion, parentPack, name, declaredLevel, requiresCore, paths, List.of(), List.of());
    }

    /**
     * Builds a descriptor that settles no sibling conflict.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param declaredLevel the declared level, possibly empty
     * @param requiresCore the raw {@code requires_core}, possibly empty
     * @param paths the declared paths
     * @param dependencies the declared dependencies
     */
    public PackDescriptor(
            String packId,
            String packVersion,
            Optional<String> parentPack,
            String name,
            Optional<PackLevel> declaredLevel,
            Optional<String> requiresCore,
            PackPaths paths,
            List<PackDependency> dependencies) {
        this(packId, packVersion, parentPack, name, declaredLevel, requiresCore, paths, dependencies, List.of());
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
     * @param dependencies the declared dependencies
     * @param overrides the declared sibling conflicts this pack settles
     * @param schemaVersion the declared {@code schema_version}, possibly empty
     * @throws IllegalArgumentException when two {@code overrides} edges settle one artifact
     */
    public PackDescriptor {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(parentPack, "parentPack");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(declaredLevel, "declaredLevel");
        Objects.requireNonNull(requiresCore, "requiresCore");
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(overrides, "overrides");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(fallbackProfile, "fallbackProfile");
        dependencies = List.copyOf(dependencies);
        overrides = List.copyOf(overrides);
        Optional<String> twice = artifactSettledTwice(overrides);
        if (twice.isPresent()) {
            throw new IllegalArgumentException("overrides settles the artifact `" + twice.get() + "` more than once");
        }
    }

    /**
     * The artifact a second {@code overrides} edge settles again, when there is one: a descriptor holds one edge per
     * artifact. Artifacts are the same when their bare URNs are, so {@code directive:X} and {@code core::directive:X}
     * name one artifact, since the {@code <pack>::} qualifier says where it is read, not which artifact it is.
     *
     * @param overrides the edges, in file order
     * @return the second edge's artifact as written; empty when every edge settles a different artifact
     */
    public static Optional<String> artifactSettledTwice(List<PackOverride> overrides) {
        Set<String> settled = new HashSet<>();
        for (PackOverride override : overrides) {
            if (!settled.add(identity(override.artifact()))) {
                return Optional.of(override.artifact());
            }
        }
        return Optional.empty();
    }

    private static String identity(String artifact) {
        try {
            return PackQualifiedReference.parse(artifact).urn();
        } catch (IllegalArgumentException malformed) {
            return artifact;
        }
    }
}
