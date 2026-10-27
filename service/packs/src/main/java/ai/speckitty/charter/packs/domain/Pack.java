package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The pack aggregate root, identified by its {@link PackIdentifier}. It composes the pack's descriptor, its artifacts
 * keyed by {@link ArtifactKind} and the violations found while reading, so a partial-tolerant load can be
 * represented. Two packs are equal when they have the same identity, whatever else they
 * hold.
 *
 * <p>Every kind is held the same way (ADR-0037): a kind joins the aggregate by its artifacts implementing
 * {@link CharterArtifact}, with no field, accessor or builder slot of its own. The typed views
 * ({@link #tactics()}, …) are conveniences over the same map. It holds no doctrine graph.
 *
 * <p>The one recorded exception is the pack's {@linkplain #assetFiles() asset files}: the one pack-level collection
 * beside the kind map, with a field, accessor and builder slot of its own (operator ruling R1: an asset payload may
 * have no sidecar and no id, so it cannot be an artifact). By design they hold payload paths, relative to the pack's
 * {@code assets/} directory; no other part of the aggregate holds file paths. Every {@link Asset} names a held asset
 * file by its path, and no two assets name the same one.
 *
 * <p>Build one with {@link #builder()}, or derive one with {@link #with(CharterArtifact)}.
 */
public final class Pack {

    private final PackIdentifier id;
    private final PackDescriptor descriptor;
    private final Map<ArtifactKind, List<CharterArtifact>> artifacts;
    private final List<PackElementViolation> violations;
    private final Map<String, AssetFile> assetFiles;

    /**
     * Reconstructs a pack from its parts; reachable only inside the domain package.
     *
     * @param id the pack's identity
     * @param descriptor the pack's descriptor
     * @param artifacts the readable artifacts of each kind, each list in a stable order
     * @param violations the files that could not be read or validated
     * @param assetFiles the payload files under {@code assets/}, in any order; at most one per path
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Pack(
            PackIdentifier id,
            PackDescriptor descriptor,
            Map<ArtifactKind, List<CharterArtifact>> artifacts,
            List<PackElementViolation> violations,
            List<AssetFile> assetFiles) {
        this.id = Objects.requireNonNull(id, "id");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        Map<ArtifactKind, List<CharterArtifact>> copy = new EnumMap<>(ArtifactKind.class);
        artifacts.forEach((kind, list) -> {
            if (!list.isEmpty()) {
                copy.put(kind, List.copyOf(list));
            }
        });
        this.artifacts = copy;
        this.violations = List.copyOf(violations);
        for (Map.Entry<ArtifactKind, List<CharterArtifact>> entry : copy.entrySet()) {
            guardKind(entry.getKey(), entry.getValue());
        }
        this.assetFiles = sortedByPath(assetFiles);
        guardAssets();
    }

    /** The asset files keyed by path, in path order; a path held twice breaches. */
    private static Map<String, AssetFile> sortedByPath(List<AssetFile> files) {
        Map<String, AssetFile> byPath = new TreeMap<>();
        for (AssetFile file : files) {
            ModularRuleset.<AssetFile>emptyRules()
                    .must(Constraints.of(
                            "A pack must not hold two asset files with the path '" + file.path() + "'.",
                            candidate -> !byPath.containsKey(candidate.path())))
                    .guard(file);
            byPath.put(file.path(), file);
        }
        return byPath;
    }

    /** The asset-to-file link: every asset names a held asset file, and no two assets name the same one. */
    private void guardAssets() {
        Map<String, String> claimedBy = new HashMap<>();
        for (Asset asset : assets()) {
            String path = asset.path();
            ModularRuleset.<Asset>emptyRules()
                    .must(Constraints.of(
                            "The asset '" + asset.localId() + "' names the asset file '" + path
                                    + "', which the pack does not hold.",
                            candidate -> assetFiles.containsKey(path)))
                    .must(Constraints.of(
                            "The asset '" + asset.localId() + "' names the asset file '" + path + "', which the asset '"
                                    + claimedBy.get(path) + "' already names.",
                            candidate -> !claimedBy.containsKey(path)))
                    .guard(asset);
            claimedBy.put(path, asset.localId());
        }
    }

    /**
     * The per-kind invariants, the same for every kind: each artifact is of the kind it is filed under and belongs to
     * this pack, and no two share a local id.
     */
    private void guardKind(ArtifactKind kind, List<CharterArtifact> ofKind) {
        ModularRuleset.<List<CharterArtifact>>emptyRules()
                .must(Constraints.of(
                        "Every " + kind.token() + " of a pack must be a " + kind.token() + ".",
                        list -> list.stream().allMatch(artifact -> artifact.kind() == kind)))
                .must(Constraints.of(
                        "Every " + kind.token() + " of a pack must belong to that pack.",
                        list -> list.stream()
                                .allMatch(artifact -> artifact.packId().equals(id))))
                .must(Constraints.of(
                        "A pack must not hold two " + kind.plural() + " with the same identifier.", list -> {
                            var seen = new HashSet<String>();
                            return list.stream().allMatch(artifact -> seen.add(artifact.localId()));
                        }))
                .guard(ofKind);
    }

    /**
     * Starts a builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts a builder that holds everything this pack holds.
     *
     * @return a builder seeded with this pack's identity, descriptor, artifacts, violations and asset files
     */
    public Builder toBuilder() {
        Builder builder = builder().id(id).descriptor(descriptor);
        artifacts().forEach(builder::artifact);
        violations.forEach(builder::violation);
        assetFiles.values().forEach(builder::assetFile);
        return builder;
    }

    /**
     * This pack with {@code artifact} added, or replacing the artifact of the same kind and local id, revalidated.
     * Every other artifact, every violation and every asset file is kept; a replaced artifact moves to the end of its
     * kind.
     *
     * @param artifact the artifact to add or replace
     * @return the revalidated pack
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    public Pack with(CharterArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        Builder builder = toBuilder();
        builder.remove(artifact.kind(), artifact.localId());
        return builder.artifact(artifact).build();
    }

    /**
     * The pack's identity.
     *
     * @return the identifier
     */
    public PackIdentifier id() {
        return id;
    }

    /**
     * The pack's descriptor.
     *
     * @return the descriptor
     */
    public PackDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Every readable artifact, grouped by kind in {@link ArtifactKind} order, each kind in its stable order.
     *
     * @return an immutable list
     */
    public List<CharterArtifact> artifacts() {
        return artifacts.values().stream().flatMap(List::stream).toList();
    }

    /**
     * The readable artifacts of one kind, in a stable order.
     *
     * @param kind the kind
     * @return an immutable list; empty when the pack holds none
     */
    public List<CharterArtifact> artifacts(ArtifactKind kind) {
        return artifacts.getOrDefault(Objects.requireNonNull(kind, "kind"), List.of());
    }

    /**
     * The readable artifacts of one kind, typed.
     *
     * @param kind the kind
     * @param type the entity type of that kind
     * @param <T> the entity type
     * @return an immutable list; empty when the pack holds none
     * @throws ClassCastException when an artifact of {@code kind} is not a {@code type}
     */
    public <T extends CharterArtifact> List<T> artifacts(ArtifactKind kind, Class<T> type) {
        return artifacts(kind).stream().map(type::cast).toList();
    }

    /**
     * The readable tactics, in a stable order.
     *
     * @return an immutable list; empty for a descriptor-only pack
     */
    public List<Tactic> tactics() {
        return artifacts(ArtifactKind.TACTIC, Tactic.class);
    }

    /**
     * The readable directives, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Directive> directives() {
        return artifacts(ArtifactKind.DIRECTIVE, Directive.class);
    }

    /**
     * The readable styleguides, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Styleguide> styleguides() {
        return artifacts(ArtifactKind.STYLEGUIDE, Styleguide.class);
    }

    /**
     * The readable toolguides, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Toolguide> toolguides() {
        return artifacts(ArtifactKind.TOOLGUIDE, Toolguide.class);
    }

    /**
     * The readable paradigms, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Paradigm> paradigms() {
        return artifacts(ArtifactKind.PARADIGM, Paradigm.class);
    }

    /**
     * The readable assets, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Asset> assets() {
        return artifacts(ArtifactKind.ASSET, Asset.class);
    }

    /**
     * The readable templates, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Template> templates() {
        return artifacts(ArtifactKind.TEMPLATE, Template.class);
    }

    /**
     * The payload files under the pack's {@code assets/} directory, with or without a sidecar.
     *
     * @return an immutable list sorted by path, at most one per path
     */
    public List<AssetFile> assetFiles() {
        return List.copyOf(assetFiles.values());
    }

    /**
     * The files that could not be read or validated while loading. This is the single, canonical owner of
     * read-time violations; no other surface may report them.
     *
     * @return an immutable list
     */
    public List<PackElementViolation> violations() {
        return violations;
    }

    /** Entity identity: equal when the identifier is equal, whatever else the packs hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Pack that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Pack[" + id.value() + "]";
    }

    /** Fluent builder for a {@link Pack}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable PackIdentifier id;
        private @Nullable PackDescriptor descriptor;
        private final Map<ArtifactKind, List<CharterArtifact>> artifacts = new EnumMap<>(ArtifactKind.class);
        private final List<PackElementViolation> violations = new ArrayList<>();
        private final List<AssetFile> assetFiles = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the pack's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(PackIdentifier value) {
            this.id = value;
            return this;
        }

        /**
         * Sets the descriptor.
         *
         * @param value the descriptor
         * @return this builder
         */
        public Builder descriptor(PackDescriptor value) {
            this.descriptor = value;
            return this;
        }

        /**
         * Appends an artifact to the artifacts of its kind.
         *
         * @param value the artifact
         * @return this builder
         */
        public Builder artifact(CharterArtifact value) {
            this.artifacts
                    .computeIfAbsent(value.kind(), kind -> new ArrayList<>())
                    .add(value);
            return this;
        }

        /**
         * Appends a tactic.
         *
         * @param tactic the tactic
         * @return this builder
         */
        public Builder tactic(Tactic tactic) {
            return artifact(tactic);
        }

        /**
         * Appends a directive.
         *
         * @param value the directive
         * @return this builder
         */
        public Builder directive(Directive value) {
            return artifact(value);
        }

        /**
         * Appends a styleguide.
         *
         * @param value the styleguide
         * @return this builder
         */
        public Builder styleguide(Styleguide value) {
            return artifact(value);
        }

        /**
         * Appends a toolguide.
         *
         * @param value the toolguide
         * @return this builder
         */
        public Builder toolguide(Toolguide value) {
            return artifact(value);
        }

        /**
         * Appends a paradigm.
         *
         * @param value the paradigm
         * @return this builder
         */
        public Builder paradigm(Paradigm value) {
            return artifact(value);
        }

        /** Drops the artifact of {@code kind} with {@code localId}, if any; {@link Pack#with} replaces through it. */
        private void remove(ArtifactKind kind, String localId) {
            List<CharterArtifact> ofKind = artifacts.get(kind);
            if (ofKind != null) {
                ofKind.removeIf(existing -> existing.localId().equals(localId));
            }
        }

        /**
         * Adds a payload file under the pack's {@code assets/} directory; {@link #build()} breaches when two share a
         * path.
         *
         * @param value the asset file
         * @return this builder
         */
        public Builder assetFile(AssetFile value) {
            this.assetFiles.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Appends a read violation.
         *
         * @param violation the violation
         * @return this builder
         */
        public Builder violation(PackElementViolation violation) {
            this.violations.add(violation);
            return this;
        }

        /**
         * Builds the pack.
         *
         * @return the validated pack
         * @throws NullPointerException when identity or descriptor was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Pack build() {
            return new Pack(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(descriptor, "descriptor"),
                    artifacts,
                    violations,
                    assetFiles);
        }
    }
}
