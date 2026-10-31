package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A template of a {@link Pack}: one file under the pack's top-level {@code templates/} directory, identified by its
 * path there, or under a mission type's {@code templates/} directory, identified by {@code <type>/<path>} and naming
 * the {@link #missionType()} it belongs to (#194, ADR-0044), the id the CRG's {@code template:} URNs use. It is carried
 * as opaque bytes (frontmatter is not parsed). Two templates are equal when they have the same identity. Build one with
 * {@link #builder()}.
 */
public final class Template implements CharterArtifact {

    private final TemplateIdentifier id;
    private final PackIdentifier packId;
    private final Payload content;
    private final Optional<String> missionType;

    /**
     * Reconstructs a template from its parts; reachable only inside the domain package.
     *
     * @param id the template's identity, its path under {@code templates/} ({@code <type>/<path>} for a mission type's)
     * @param packId the pack that owns the template
     * @param content the file's exact bytes
     * @param missionType the mission type whose {@code templates/} holds it, empty for the top-level one
     * @throws IllegalArgumentException when a mission type's template id does not start with {@code <type>/}
     */
    Template(TemplateIdentifier id, PackIdentifier packId, Payload content, Optional<String> missionType) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.content = Objects.requireNonNull(content, "content");
        this.missionType = Objects.requireNonNull(missionType, "missionType");
        missionType.ifPresent(type -> {
            if (type.isBlank() || type.contains("/") || !id.value().startsWith(type + "/")) {
                throw new IllegalArgumentException(
                        "a mission type's template id starts with '<type>/': '" + id.value() + "' of '" + type + "'");
            }
        });
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
     * The template's identity.
     *
     * @return the identifier
     */
    public TemplateIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.TEMPLATE;
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    /** The path under {@code templates/}, for example {@code writing/meeting-minutes.md}. */
    @Override
    public String localId() {
        return id.value();
    }

    /** A template has no schema version. */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.empty();
    }

    /** The file name: the last segment of the path. */
    @Override
    public String label() {
        return id.fileName();
    }

    /** A template has no description. */
    @Override
    public Optional<String> description() {
        return Optional.empty();
    }

    /** A template mints no relationships. */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /**
     * The file's content.
     *
     * @return the exact bytes
     */
    public Payload content() {
        return content;
    }

    /**
     * The mission type whose {@code templates/} directory holds this template (#194, ADR-0044).
     *
     * @return the type's id; empty for a template of the pack's top-level {@code templates/}
     */
    public Optional<String> missionType() {
        return missionType;
    }

    /** Entity identity: equal when the identifier is equal, whatever the content holds. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Template that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Template[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Template}. */
    public static final class Builder {
        private @Nullable TemplateIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable Payload content;
        private @Nullable String missionType;

        private Builder() {}

        /**
         * Sets the mission type whose {@code templates/} directory holds the template; {@code null} for the pack's
         * top-level {@code templates/}.
         *
         * @param value the type's id, or {@code null}
         * @return this builder
         */
        public Builder missionType(@Nullable String value) {
            this.missionType = value;
            return this;
        }

        /**
         * Sets the template's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(TemplateIdentifier value) {
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
         * Sets the content.
         *
         * @param value the file's exact bytes
         * @return this builder
         */
        public Builder content(Payload value) {
            this.content = value;
            return this;
        }

        /**
         * Builds the template.
         *
         * @return the template
         * @throws NullPointerException when identity, pack or content was never set
         */
        public Template build() {
            return new Template(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(content, "content"),
                    Optional.ofNullable(missionType));
        }
    }
}
