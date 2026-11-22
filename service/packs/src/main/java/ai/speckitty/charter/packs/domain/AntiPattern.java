package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * An anti-pattern of a {@link Pack}: a recognisable practice that guidance rejects, identified by its
 * {@link AntiPatternIdentifier} (ADR-0052 decision 8, as amended by ADR-0053 decision 30). Its file,
 * {@code anti_patterns/<id>.anti-pattern.yaml}, is its only form and holds it; a graph node of the same URN declares it
 * and is the target of a {@code rejects} edge. Two anti-patterns are equal when they have the same identity, whatever
 * their other fields hold.
 *
 * <p>It carries no relationship fields: guidance rejects it with an edge in the pack's graph documents (ADR-0064
 * decision 12). Its severity is always stated (ADR-0053 decision 32). Build one with {@link #builder()}.
 */
public final class AntiPattern implements CharterArtifact {

    private static final Pattern SCHEMA_VERSION = Pattern.compile("^1\\.0$");

    private static final ModularRuleset<AntiPattern> RULES = ModularRuleset.<AntiPattern>emptyRules()
            .must(Constraints.of(
                    "An anti-pattern needs a schema version of 1.0.",
                    antiPattern ->
                            SCHEMA_VERSION.matcher(antiPattern.schemaVersion).matches()))
            .must(Constraints.notBlank()
                    .described("An anti-pattern needs a title that is not blank.")
                    .on(antiPattern -> antiPattern.title))
            .must(Constraints.notBlank()
                    .described("An anti-pattern needs a description that is not blank.")
                    .on(antiPattern -> antiPattern.description))
            .must(Constraints.notBlank()
                    .described("An anti-pattern needs a bad example that is not blank.")
                    .on(antiPattern -> antiPattern.badExample));

    private final AntiPatternIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final String description;
    private final AntiPatternSeverity severity;
    private final String badExample;
    private final Optional<String> goodExample;

    /**
     * Reconstructs an anti-pattern from its parts; reachable only inside the domain package.
     *
     * @param id the anti-pattern's identity
     * @param packId the pack that owns the anti-pattern
     * @param schemaVersion the schema version text
     * @param title the anti-pattern's title
     * @param description what the practice is and why it is rejected
     * @param severity how much it weighs in a review
     * @param badExample a concrete instance of the practice
     * @param goodExample what to do instead, when stated
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    AntiPattern(
            AntiPatternIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String title,
            String description,
            AntiPatternSeverity severity,
            String badExample,
            Optional<String> goodExample) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.badExample = Objects.requireNonNull(badExample, "badExample");
        this.goodExample = Objects.requireNonNull(goodExample, "goodExample");
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
     * The anti-pattern's identity.
     *
     * @return the identifier
     */
    public AntiPatternIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ANTI_PATTERN;
    }

    /**
     * The pack that owns this anti-pattern.
     *
     * @return the pack's identifier
     */
    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    /**
     * The anti-pattern's {@code schema_version}, as text.
     *
     * @return the schema version
     */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    /**
     * The anti-pattern's {@code title}.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    @Override
    public String label() {
        return title;
    }

    /**
     * The anti-pattern's {@code description}: what the practice is and why guidance rejects it.
     *
     * @return the description, always present
     */
    @Override
    public Optional<String> description() {
        return Optional.of(description);
    }

    /**
     * The anti-pattern's {@code severity}.
     *
     * @return the severity
     */
    public AntiPatternSeverity severity() {
        return severity;
    }

    /**
     * The anti-pattern's {@code bad_example}.
     *
     * @return a concrete instance of the practice
     */
    public String badExample() {
        return badExample;
    }

    /**
     * The anti-pattern's {@code good_example}.
     *
     * @return what to do instead; empty when the file states none
     */
    public Optional<String> goodExample() {
        return goodExample;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof AntiPattern that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AntiPattern[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for an {@link AntiPattern}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable AntiPatternIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String title;
        private @Nullable String description;
        private @Nullable AntiPatternSeverity severity;
        private @Nullable String badExample;
        private Optional<String> goodExample = Optional.empty();

        private Builder() {}

        /**
         * Sets the anti-pattern's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(AntiPatternIdentifier value) {
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
         * Sets the schema version text.
         *
         * @param value the schema version
         * @return this builder
         */
        public Builder schemaVersion(String value) {
            this.schemaVersion = value;
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
         * Sets the description.
         *
         * @param value the description
         * @return this builder
         */
        public Builder description(String value) {
            this.description = value;
            return this;
        }

        /**
         * Sets the severity.
         *
         * @param value the severity
         * @return this builder
         */
        public Builder severity(AntiPatternSeverity value) {
            this.severity = value;
            return this;
        }

        /**
         * Sets the bad example.
         *
         * @param value the bad example
         * @return this builder
         */
        public Builder badExample(String value) {
            this.badExample = value;
            return this;
        }

        /**
         * Sets the good example.
         *
         * @param value the good example
         * @return this builder
         */
        public Builder goodExample(String value) {
            this.goodExample = Optional.of(value);
            return this;
        }

        /**
         * Sets the good example, or clears it.
         *
         * @param value the good example; empty for none
         * @return this builder
         */
        public Builder goodExample(Optional<String> value) {
            this.goodExample = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Builds the anti-pattern.
         *
         * @return the validated anti-pattern
         * @throws NullPointerException when identity, pack, schema version, title, description, severity or bad
         *     example was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public AntiPattern build() {
            return new AntiPattern(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(title, "title"),
                    Objects.requireNonNull(description, "description"),
                    Objects.requireNonNull(severity, "severity"),
                    Objects.requireNonNull(badExample, "badExample"),
                    goodExample);
        }
    }
}
