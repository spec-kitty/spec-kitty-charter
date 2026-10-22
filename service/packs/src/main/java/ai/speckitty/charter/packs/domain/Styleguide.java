package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A styleguide of a {@link Pack}: conventions for the shape of a work product, identified by its
 * {@link StyleguideIdentifier}. Two styleguides are equal when they have the same identity. Its {@code references} are
 * bare citation strings carried in the body: a styleguide mints no DRG edges and has no description. Build one with
 * {@link #builder()}.
 */
public final class Styleguide implements CharterArtifact {

    private static final ModularRuleset<Styleguide> RULES = ModularRuleset.<Styleguide>emptyRules()
            .must(Constraints.of(
                    "A styleguide needs a schema version of 1.0.", sg -> sg.schemaVersion.matches("^1\\.0$")))
            .must(Constraints.of("A styleguide needs a title that is not blank.", sg -> !sg.title.isBlank()))
            .must(Constraints.of("A styleguide needs at least one principle.", sg -> !sg.principles.isEmpty()))
            .must(Constraints.of(
                    "A styleguide's patterns, when present, must not be empty.",
                    sg -> sg.patterns.map(list -> !list.isEmpty()).orElse(true)))
            .must(Constraints.of(
                    "A styleguide's anti-patterns, when present, must not be empty.",
                    sg -> sg.antiPatterns.map(list -> !list.isEmpty()).orElse(true)));

    private final StyleguideIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final StyleguideScope scope;
    private final List<String> principles;
    private final Optional<List<StyleguidePattern>> patterns;
    private final Optional<List<StyleguideAntiPattern>> antiPatterns;
    private final Optional<Map<String, String>> tooling;
    private final Optional<String> qualityTest;
    private final Optional<ConfigValue> structuralLintConfig;
    private final List<String> appliesToLanguages;
    private final List<String> references;

    /**
     * Reconstructs a styleguide from its parts; reachable only inside the domain package.
     *
     * @param id the identity
     * @param packId the owning pack
     * @param schemaVersion the schema version text
     * @param title the title
     * @param scope the scope category
     * @param principles the principles, in authored order; never empty
     * @param patterns the patterns when the field is present; never an empty list
     * @param antiPatterns the anti-patterns when the field is present; never an empty list
     * @param tooling the tooling map, when stated
     * @param qualityTest the quality test, when stated
     * @param structuralLintConfig the structural lint config tree, when stated
     * @param appliesToLanguages the applicable languages
     * @param references the bare citation strings
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Styleguide(
            StyleguideIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String title,
            StyleguideScope scope,
            List<String> principles,
            Optional<List<StyleguidePattern>> patterns,
            Optional<List<StyleguideAntiPattern>> antiPatterns,
            Optional<Map<String, String>> tooling,
            Optional<String> qualityTest,
            Optional<ConfigValue> structuralLintConfig,
            List<String> appliesToLanguages,
            List<String> references) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(title, "title");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.principles = List.copyOf(principles);
        this.patterns = patterns.map(List::copyOf);
        this.antiPatterns = antiPatterns.map(List::copyOf);
        this.tooling = tooling.map(map -> Collections.unmodifiableMap(new LinkedHashMap<>(map)));
        this.qualityTest = Objects.requireNonNull(qualityTest, "qualityTest");
        this.structuralLintConfig = Objects.requireNonNull(structuralLintConfig, "structuralLintConfig");
        this.appliesToLanguages = List.copyOf(appliesToLanguages);
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
     * The styleguide's identity.
     *
     * @return the identifier
     */
    public StyleguideIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.STYLEGUIDE;
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
    public String schemaVersion() {
        return schemaVersion;
    }

    /**
     * The {@code title}.
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

    /** A styleguide has no description. */
    @Override
    public Optional<String> description() {
        return Optional.empty();
    }

    /** A styleguide mints no edges: its references are citations in the body. */
    @Override
    public List<DrgEdge> edges() {
        return List.of();
    }

    /**
     * The scope category.
     *
     * @return the scope
     */
    public StyleguideScope scope() {
        return scope;
    }

    /**
     * The principles, in authored order.
     *
     * @return an immutable, non-empty list
     */
    public List<String> principles() {
        return principles;
    }

    /**
     * The patterns.
     *
     * @return an immutable list; empty when the field is absent
     */
    public List<StyleguidePattern> patterns() {
        return patterns.orElse(List.of());
    }

    /**
     * The anti-patterns.
     *
     * @return an immutable list; empty when the field is absent
     */
    public List<StyleguideAntiPattern> antiPatterns() {
        return antiPatterns.orElse(List.of());
    }

    /**
     * The tooling map.
     *
     * @return the map, empty when absent
     */
    public Optional<Map<String, String>> tooling() {
        return tooling;
    }

    /**
     * The {@code quality_test}.
     *
     * @return the text, empty when absent
     */
    public Optional<String> qualityTest() {
        return qualityTest;
    }

    /**
     * The {@code structural_lint_config} tree.
     *
     * @return the tree, empty when absent
     */
    public Optional<ConfigValue> structuralLintConfig() {
        return structuralLintConfig;
    }

    /**
     * The languages the styleguide applies to.
     *
     * @return an immutable list
     */
    public List<String> appliesToLanguages() {
        return appliesToLanguages;
    }

    /**
     * The bare citation strings, kept in the body.
     *
     * @return an immutable list
     */
    public List<String> references() {
        return references;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Styleguide that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Styleguide[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Styleguide}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable StyleguideIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String title;
        private @Nullable StyleguideScope scope;
        private final List<String> principles = new ArrayList<>();
        private @Nullable List<StyleguidePattern> patterns;
        private @Nullable List<StyleguideAntiPattern> antiPatterns;
        private Optional<Map<String, String>> tooling = Optional.empty();
        private Optional<String> qualityTest = Optional.empty();
        private Optional<ConfigValue> structuralLintConfig = Optional.empty();
        private final List<String> appliesToLanguages = new ArrayList<>();
        private final List<String> references = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(StyleguideIdentifier value) {
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
         * Sets the scope.
         *
         * @param value the scope
         * @return this builder
         */
        public Builder scope(StyleguideScope value) {
            this.scope = value;
            return this;
        }

        /**
         * Appends a principle.
         *
         * @param value the principle
         * @return this builder
         */
        public Builder principle(String value) {
            this.principles.add(value);
            return this;
        }

        /**
         * Marks the patterns field present with exactly these patterns.
         *
         * @param value the patterns; an empty list is a breach at build
         * @return this builder
         */
        public Builder patterns(List<StyleguidePattern> value) {
            this.patterns = new ArrayList<>(value);
            return this;
        }

        /**
         * Appends a pattern, marking the patterns field present.
         *
         * @param value the pattern
         * @return this builder
         */
        public Builder pattern(StyleguidePattern value) {
            if (this.patterns == null) {
                this.patterns = new ArrayList<>();
            }
            this.patterns.add(value);
            return this;
        }

        /**
         * Marks the anti-patterns field present with exactly these anti-patterns.
         *
         * @param value the anti-patterns; an empty list is a breach at build
         * @return this builder
         */
        public Builder antiPatterns(List<StyleguideAntiPattern> value) {
            this.antiPatterns = new ArrayList<>(value);
            return this;
        }

        /**
         * Appends an anti-pattern, marking the anti-patterns field present.
         *
         * @param value the anti-pattern
         * @return this builder
         */
        public Builder antiPattern(StyleguideAntiPattern value) {
            if (this.antiPatterns == null) {
                this.antiPatterns = new ArrayList<>();
            }
            this.antiPatterns.add(value);
            return this;
        }

        /**
         * Sets the tooling map.
         *
         * @param value the map, or null for none
         * @return this builder
         */
        public Builder tooling(@Nullable Map<String, String> value) {
            this.tooling = Optional.ofNullable(value);
            return this;
        }

        /**
         * Sets the quality test.
         *
         * @param value the text, or null for none
         * @return this builder
         */
        public Builder qualityTest(@Nullable String value) {
            this.qualityTest = Optional.ofNullable(value);
            return this;
        }

        /**
         * Sets the structural lint config tree.
         *
         * @param value the tree, or null for none
         * @return this builder
         */
        public Builder structuralLintConfig(@Nullable ConfigValue value) {
            this.structuralLintConfig = Optional.ofNullable(value);
            return this;
        }

        /**
         * Appends an applicable language.
         *
         * @param value the language
         * @return this builder
         */
        public Builder appliesToLanguage(String value) {
            this.appliesToLanguages.add(value);
            return this;
        }

        /**
         * Appends a bare citation string.
         *
         * @param value the citation
         * @return this builder
         */
        public Builder reference(String value) {
            this.references.add(value);
            return this;
        }

        /**
         * Builds the styleguide.
         *
         * @return the validated styleguide
         * @throws NullPointerException when identity, pack, schema version, title or scope was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Styleguide build() {
            return new Styleguide(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(title, "title"),
                    Objects.requireNonNull(scope, "scope"),
                    principles,
                    Optional.ofNullable(patterns),
                    Optional.ofNullable(antiPatterns),
                    tooling,
                    qualityTest,
                    structuralLintConfig,
                    appliesToLanguages,
                    references);
        }
    }
}
