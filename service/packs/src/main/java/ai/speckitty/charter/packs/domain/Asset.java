package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An asset of a {@link Pack}: the metadata a sidecar manifest records about one payload file. The payload itself is an
 * {@link AssetFile} the pack holds; the asset names it by {@link #path()}. Two assets are equal when they have the same
 * identity.
 *
 * <p>{@code mime} and {@code path} are kept exactly as authored: the MIME vocabulary is checked by {@link PackPolicy},
 * and the path is not normalized. Build one with {@link #builder()}.
 */
public final class Asset implements CharterArtifact {

    private static final ModularRuleset<Asset> RULES = ModularRuleset.<Asset>emptyRules()
            .must(Constraints.of("An asset needs a mime that is not blank.", asset -> !asset.mime.isBlank()))
            .must(Constraints.of("An asset needs a path that is not blank.", asset -> !asset.path.isBlank()));

    private final AssetIdentifier id;
    private final PackIdentifier packId;
    private final String mime;
    private final String path;
    private final Optional<String> title;

    /**
     * Reconstructs an asset from its parts; reachable only inside the domain package.
     *
     * @param id the asset's identity
     * @param packId the pack that owns the asset
     * @param mime the authored MIME type
     * @param path the authored path of the payload file, relative to the pack's {@code assets/} directory
     * @param title the title, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Asset(AssetIdentifier id, PackIdentifier packId, String mime, String path, Optional<String> title) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.mime = Objects.requireNonNull(mime, "mime");
        this.path = Objects.requireNonNull(path, "path");
        this.title = Objects.requireNonNull(title, "title");
        RULES.guard(this);
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
     * The asset's identity.
     *
     * @return the identifier
     */
    public AssetIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ASSET;
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    /** An asset sidecar has no schema version. */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.empty();
    }

    /** The title, or the id when the asset has none. */
    @Override
    public String label() {
        return title.orElse(id.value());
    }

    /** An asset has no description. */
    @Override
    public Optional<String> description() {
        return Optional.empty();
    }

    /** An asset mints no relationships. */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /**
     * The authored MIME type.
     *
     * @return the MIME type, as authored
     */
    public String mime() {
        return mime;
    }

    /**
     * The authored path of the payload file, relative to the pack's {@code assets/} directory.
     *
     * @return the path, as authored
     */
    public String path() {
        return path;
    }

    /**
     * The {@code title}.
     *
     * @return the title, empty when absent
     */
    public Optional<String> title() {
        return title;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Asset that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Asset[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for an {@link Asset}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable AssetIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String mime;
        private @Nullable String path;
        private Optional<String> title = Optional.empty();

        private Builder() {}

        /**
         * Sets the asset's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(AssetIdentifier value) {
            this.id = value;
            return this;
        }

        /**
         * Sets the owning pack.
         *
         * @param value the pack's identifier
         * @return this builder
         */
        public Builder packId(PackIdentifier value) {
            this.packId = value;
            return this;
        }

        /**
         * Sets the MIME type.
         *
         * @param value the MIME type, as authored
         * @return this builder
         */
        public Builder mime(String value) {
            this.mime = value;
            return this;
        }

        /**
         * Sets the path of the payload file.
         *
         * @param value the path relative to the pack's {@code assets/} directory, as authored
         * @return this builder
         */
        public Builder path(String value) {
            this.path = value;
            return this;
        }

        /**
         * Sets the title.
         *
         * @param value the title, or null for none
         * @return this builder
         */
        public Builder title(@Nullable String value) {
            this.title = Optional.ofNullable(value);
            return this;
        }

        /**
         * Builds the asset.
         *
         * @return the validated asset
         * @throws NullPointerException when identity, pack, mime or path was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Asset build() {
            return new Asset(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(mime, "mime"),
                    Objects.requireNonNull(path, "path"),
                    title);
        }
    }
}
