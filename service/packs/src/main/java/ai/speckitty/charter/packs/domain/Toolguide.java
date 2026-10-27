package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A toolguide of a {@link Pack}: how to use one tool under the pack's governance, a child entity identified by its
 * {@link ToolguideIdentifier}. Two toolguides are equal when they have the same identity, whatever their other fields
 * hold; the fidelity of a round trip is asserted on the storage representation, not here.
 *
 * <p>A toolguide mints no relationships: its {@code references} are bare citation strings, not typed edges
 * (data-model). List order and duplicates are significant. Build one with {@link #builder()}.
 */
public final class Toolguide implements CharterArtifact {

    private static final Pattern SCHEMA_VERSION = Pattern.compile("^1\\.0$");
    private static final Pattern GUIDE_PATH = Pattern.compile("^[^/].*\\.md$");

    private static final ModularRuleset<Toolguide> RULES = ModularRuleset.<Toolguide>emptyRules()
            .must(Constraints.of(
                    "A toolguide needs a schema version of 1.0.",
                    toolguide -> SCHEMA_VERSION.matcher(toolguide.schemaVersion).matches()))
            .must(Constraints.of("A toolguide needs a tool that is not blank.", toolguide -> !toolguide.tool.isBlank()))
            .must(Constraints.of(
                    "A toolguide needs a title that is not blank.", toolguide -> !toolguide.title.isBlank()))
            .must(Constraints.of(
                    "A toolguide needs a summary that is not blank.", toolguide -> !toolguide.summary.isBlank()))
            .must(Constraints.of(
                    "A toolguide needs a guide path that is a relative *.md path.",
                    toolguide -> GUIDE_PATH.matcher(toolguide.guidePath).matches()));

    private final ToolguideIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String tool;
    private final String title;
    private final String guidePath;
    private final String summary;
    private final List<String> commands;
    private final List<String> appliesToLanguages;
    private final Optional<String> lastUpdated;
    private final List<String> references;

    /**
     * Reconstructs a toolguide from its parts; reachable only inside the domain package.
     *
     * @param id the toolguide's identity
     * @param packId the pack that owns the toolguide
     * @param schemaVersion the schema version text
     * @param tool the tool the guide is about
     * @param title the title
     * @param guidePath the relative {@code *.md} path of the guide body
     * @param summary the summary
     * @param commands the commands, in authored order
     * @param appliesToLanguages the languages it applies to, in authored order
     * @param lastUpdated the last-updated text, possibly empty
     * @param references the bare citation strings, in authored order with duplicates kept
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Toolguide(
            ToolguideIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String tool,
            String title,
            String guidePath,
            String summary,
            List<String> commands,
            List<String> appliesToLanguages,
            Optional<String> lastUpdated,
            List<String> references) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.tool = Objects.requireNonNull(tool, "tool");
        this.title = Objects.requireNonNull(title, "title");
        this.guidePath = Objects.requireNonNull(guidePath, "guidePath");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.commands = List.copyOf(commands);
        this.appliesToLanguages = List.copyOf(appliesToLanguages);
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated");
        this.references = List.copyOf(references);
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
     * The toolguide's identity.
     *
     * @return the identifier
     */
    public ToolguideIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.TOOLGUIDE;
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    @Override
    public String label() {
        return title;
    }

    @Override
    public Optional<String> description() {
        return Optional.of(summary);
    }

    /** A toolguide mints no relationships: its references are bare citation strings, not typed edges. */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /**
     * The tool the guide is about.
     *
     * @return the tool
     */
    public String tool() {
        return tool;
    }

    /**
     * The {@code title}.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * The relative {@code *.md} path of the guide body.
     *
     * @return the guide path
     */
    public String guidePath() {
        return guidePath;
    }

    /**
     * The {@code summary}.
     *
     * @return the summary
     */
    public String summary() {
        return summary;
    }

    /**
     * The commands, in authored order.
     *
     * @return an immutable list
     */
    public List<String> commands() {
        return commands;
    }

    /**
     * The languages the toolguide applies to, in authored order.
     *
     * @return an immutable list
     */
    public List<String> appliesToLanguages() {
        return appliesToLanguages;
    }

    /**
     * The last-updated text.
     *
     * @return the last-updated text, empty when absent
     */
    public Optional<String> lastUpdated() {
        return lastUpdated;
    }

    /**
     * The bare citation strings, in authored order with duplicates kept.
     *
     * @return an immutable list
     */
    public List<String> references() {
        return references;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Toolguide that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Toolguide[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Toolguide}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable ToolguideIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String tool;
        private @Nullable String title;
        private @Nullable String guidePath;
        private @Nullable String summary;
        private final List<String> commands = new ArrayList<>();
        private final List<String> appliesToLanguages = new ArrayList<>();
        private Optional<String> lastUpdated = Optional.empty();
        private final List<String> references = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the identity.
         *
         * @param value the identity
         * @return this builder
         */
        public Builder id(ToolguideIdentifier value) {
            this.id = value;
            return this;
        }

        /**
         * Sets the owning pack.
         *
         * @param value the owning pack
         * @return this builder
         */
        public Builder packId(PackIdentifier value) {
            this.packId = value;
            return this;
        }

        /**
         * Sets the schema version text.
         *
         * @param value the schema version text
         * @return this builder
         */
        public Builder schemaVersion(String value) {
            this.schemaVersion = value;
            return this;
        }

        /**
         * Sets the tool.
         *
         * @param value the tool
         * @return this builder
         */
        public Builder tool(String value) {
            this.tool = value;
            return this;
        }

        /**
         * Sets the title.
         *
         * @param value the title
         * @return this builder
         */
        public Builder title(String value) {
            this.title = value;
            return this;
        }

        /**
         * Sets the relative {@code *.md} guide path.
         *
         * @param value the guide path
         * @return this builder
         */
        public Builder guidePath(String value) {
            this.guidePath = value;
            return this;
        }

        /**
         * Sets the summary.
         *
         * @param value the summary
         * @return this builder
         */
        public Builder summary(String value) {
            this.summary = value;
            return this;
        }

        /**
         * Appends a command.
         *
         * @param value the command
         * @return this builder
         */
        public Builder command(String value) {
            this.commands.add(value);
            return this;
        }

        /**
         * Appends a language the toolguide applies to.
         *
         * @param value the language
         * @return this builder
         */
        public Builder appliesToLanguage(String value) {
            this.appliesToLanguages.add(value);
            return this;
        }

        /**
         * Sets the last-updated text.
         *
         * @param value the last-updated text, or null for none
         * @return this builder
         */
        public Builder lastUpdated(@Nullable String value) {
            this.lastUpdated = Optional.ofNullable(value);
            return this;
        }

        /**
         * Appends a bare citation reference.
         *
         * @param value the citation
         * @return this builder
         */
        public Builder reference(String value) {
            this.references.add(value);
            return this;
        }

        /**
         * Builds the toolguide.
         *
         * @return the validated toolguide
         * @throws NullPointerException when identity, pack, schema version, tool, title, guide path or summary was
         *     never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Toolguide build() {
            return new Toolguide(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(tool, "tool"),
                    Objects.requireNonNull(title, "title"),
                    Objects.requireNonNull(guidePath, "guidePath"),
                    Objects.requireNonNull(summary, "summary"),
                    commands,
                    appliesToLanguages,
                    lastUpdated,
                    references);
        }
    }
}
