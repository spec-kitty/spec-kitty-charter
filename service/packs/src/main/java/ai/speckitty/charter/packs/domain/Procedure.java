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
import org.jspecify.annotations.Nullable;

/**
 * A procedure of a {@link Pack}: a reusable orchestrated workflow with an entry and an exit condition and ordered
 * steps, each naming the role that acts. It is a child entity identified by its {@link ProcedureIdentifier}; two
 * procedures are equal when they have the same identity, whatever their other fields hold.
 *
 * <p>It owns its steps, anti-patterns and references; their order and duplicates are significant. Build one with
 * {@link #builder()}.
 */
public final class Procedure implements CharterArtifact {

    private static final ModularRuleset<Procedure> RULES = ModularRuleset.<Procedure>emptyRules()
            .must(Constraints.of("A procedure needs at least one step.", procedure -> !procedure.steps.isEmpty()))
            .must(Constraints.of(
                    "A procedure needs a title that is not blank.", procedure -> !procedure.title.isBlank()))
            .must(Constraints.of(
                    "A procedure needs a description that is not blank.",
                    procedure -> !procedure.description.isBlank()))
            .must(Constraints.of(
                    "A procedure needs an entry condition that is not blank.",
                    procedure -> !procedure.entryCondition.isBlank()))
            .must(Constraints.of(
                    "A procedure needs an exit condition that is not blank.",
                    procedure -> !procedure.exitCondition.isBlank()))
            .must(Constraints.of(
                    "A procedure needs a schema version that is not blank.",
                    procedure -> !procedure.schemaVersion.isBlank()));

    private final ProcedureIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final String description;
    private final String entryCondition;
    private final String exitCondition;
    private final List<ProcedureStep> steps;
    private final List<AntiPatternEntry> antiPatterns;
    private final List<ProcedureReference> references;
    private final Optional<String> notes;
    private final List<String> appliesToLanguages;

    /**
     * Reconstructs a procedure from its parts; reachable only inside the domain package, for the adapter's use
     * through a builder and for the aggregate.
     *
     * @param id the procedure's identity
     * @param packId the pack that owns the procedure
     * @param schemaVersion the schema version text
     * @param title the display label
     * @param description the "what is this" summary
     * @param entryCondition the condition under which the procedure starts
     * @param exitCondition the condition under which the procedure is done
     * @param steps the steps, in authored order; never empty
     * @param antiPatterns the anti-patterns, in authored order
     * @param references the references, in authored order with duplicates kept
     * @param notes the notes, possibly empty
     * @param appliesToLanguages the applicable languages, in authored order
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Procedure(
            ProcedureIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String title,
            String description,
            String entryCondition,
            String exitCondition,
            List<ProcedureStep> steps,
            List<AntiPatternEntry> antiPatterns,
            List<ProcedureReference> references,
            Optional<String> notes,
            List<String> appliesToLanguages) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.entryCondition = Objects.requireNonNull(entryCondition, "entryCondition");
        this.exitCondition = Objects.requireNonNull(exitCondition, "exitCondition");
        this.steps = List.copyOf(steps);
        this.antiPatterns = List.copyOf(antiPatterns);
        this.references = List.copyOf(references);
        this.notes = Objects.requireNonNull(notes, "notes");
        this.appliesToLanguages = List.copyOf(appliesToLanguages);
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
     * The procedure's identity.
     *
     * @return the identifier
     */
    public ProcedureIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.PROCEDURE;
    }

    @Override
    public String localId() {
        return id.value();
    }

    /**
     * The procedure's {@code title}.
     *
     * @return the title
     */
    @Override
    public String label() {
        return title;
    }

    /**
     * The procedure's {@code description}; always present for a procedure.
     *
     * @return the description
     */
    @Override
    public Optional<String> description() {
        return Optional.of(description);
    }

    /**
     * A procedure mints no relationship: always empty, even when it carries {@link #references()}.
     *
     * <p>The references are kept on the procedure but deliberately not turned into edges (research D3, #189). Minting
     * them through {@link ArtifactEdges} would yield {@code SUGGESTS} edges while the pack's
     * {@code procedure.graph.yaml} holds the same edges as {@code requires}, producing conflicting duplicates; which
     * of the two is the edge authority is #189's decision, not this entity's.
     *
     * @return an empty list
     */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /**
     * The pack that owns this procedure.
     *
     * @return the pack's identifier
     */
    @Override
    public PackIdentifier packId() {
        return packId;
    }

    /**
     * The procedure's {@code schema_version}, as text.
     *
     * @return the schema version
     */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    /**
     * The procedure's {@code title}.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * The procedure's {@code entry_condition}.
     *
     * @return the entry condition
     */
    public String entryCondition() {
        return entryCondition;
    }

    /**
     * The procedure's {@code exit_condition}.
     *
     * @return the exit condition
     */
    public String exitCondition() {
        return exitCondition;
    }

    /**
     * The steps, in authored order.
     *
     * @return an immutable, non-empty list
     */
    public List<ProcedureStep> steps() {
        return steps;
    }

    /**
     * The anti-patterns, in authored order.
     *
     * @return an immutable list
     */
    public List<AntiPatternEntry> antiPatterns() {
        return antiPatterns;
    }

    /**
     * The references, in authored order with duplicates kept; never minted as relationships.
     *
     * @return an immutable list
     */
    public List<ProcedureReference> references() {
        return references;
    }

    /**
     * The {@code notes}.
     *
     * @return the notes, empty when absent
     */
    public Optional<String> notes() {
        return notes;
    }

    /**
     * The languages the procedure applies to, in authored order.
     *
     * @return an immutable list
     */
    public List<String> appliesToLanguages() {
        return appliesToLanguages;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Procedure that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Procedure[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Procedure}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable ProcedureIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String title;
        private @Nullable String description;
        private @Nullable String entryCondition;
        private @Nullable String exitCondition;
        private final List<ProcedureStep> steps = new ArrayList<>();
        private final List<AntiPatternEntry> antiPatterns = new ArrayList<>();
        private final List<ProcedureReference> references = new ArrayList<>();
        private Optional<String> notes = Optional.empty();
        private final List<String> appliesToLanguages = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the procedure's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(ProcedureIdentifier value) {
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
         * Sets the entry condition.
         *
         * @param value the entry condition
         * @return this builder
         */
        public Builder entryCondition(String value) {
            this.entryCondition = value;
            return this;
        }

        /**
         * Sets the exit condition.
         *
         * @param value the exit condition
         * @return this builder
         */
        public Builder exitCondition(String value) {
            this.exitCondition = value;
            return this;
        }

        /**
         * Appends a step.
         *
         * @param value the step
         * @return this builder
         */
        public Builder step(ProcedureStep value) {
            this.steps.add(value);
            return this;
        }

        /**
         * Appends an anti-pattern.
         *
         * @param value the anti-pattern
         * @return this builder
         */
        public Builder antiPattern(AntiPatternEntry value) {
            this.antiPatterns.add(value);
            return this;
        }

        /**
         * Appends a reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder reference(ProcedureReference value) {
            this.references.add(value);
            return this;
        }

        /**
         * Sets the notes.
         *
         * @param value the notes, or null for none
         * @return this builder
         */
        public Builder notes(@Nullable String value) {
            this.notes = Optional.ofNullable(value);
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
         * Builds the procedure.
         *
         * @return the validated procedure
         * @throws NullPointerException when identity, pack, schema version, title, description or a condition was
         *     never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Procedure build() {
            return new Procedure(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(title, "title"),
                    Objects.requireNonNull(description, "description"),
                    Objects.requireNonNull(entryCondition, "entryCondition"),
                    Objects.requireNonNull(exitCondition, "exitCondition"),
                    steps,
                    antiPatterns,
                    references,
                    notes,
                    appliesToLanguages);
        }
    }
}
