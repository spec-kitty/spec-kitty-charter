package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A tactic of a {@link Pack}: a child entity identified by its {@link TacticIdentifier}. Two tactics are equal when
 * they have the same identity, whatever their other fields hold; the fidelity of a round trip is asserted on the
 * storage representation, not here.
 *
 * <p>It owns its steps and references; their order and duplicates are significant. Build one with {@link #builder()}.
 */
public final class Tactic {

    private static final ModularRuleset<Tactic> RULES = ModularRuleset.<Tactic>emptyRules()
            .must(Constraints.of("A tactic needs at least one step.", tactic -> !tactic.steps.isEmpty()))
            .must(Constraints.of("A tactic needs a name that is not blank.", tactic -> !tactic.name.isBlank()))
            .must(Constraints.of(
                    "A tactic needs a schema version that is not blank.", tactic -> !tactic.schemaVersion.isBlank()));

    private final TacticIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String name;
    private final List<TacticStep> steps;
    private final Optional<String> purpose;
    private final Optional<String> notes;
    private final List<String> failureModes;
    private final List<String> appliesToLanguages;
    private final List<TacticReference> references;

    /**
     * Reconstructs a tactic from its parts; reachable only inside the domain package, for the adapter's use through
     * a builder and for the aggregate.
     *
     * @param id the tactic's identity
     * @param packId the pack that owns the tactic
     * @param schemaVersion the schema version text
     * @param name the tactic's name
     * @param steps the steps, in authored order; never empty
     * @param purpose the purpose, possibly empty
     * @param notes the notes, possibly empty
     * @param failureModes the failure modes, in authored order
     * @param appliesToLanguages the applicable languages, in authored order
     * @param references the references, in authored order with duplicates kept
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Tactic(
            TacticIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String name,
            List<TacticStep> steps,
            Optional<String> purpose,
            Optional<String> notes,
            List<String> failureModes,
            List<String> appliesToLanguages,
            List<TacticReference> references) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.name = Objects.requireNonNull(name, "name");
        this.steps = List.copyOf(steps);
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.notes = Objects.requireNonNull(notes, "notes");
        this.failureModes = List.copyOf(failureModes);
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
     * The tactic's identity.
     *
     * @return the identifier
     */
    public TacticIdentifier id() {
        return id;
    }

    /**
     * The pack that owns this tactic.
     *
     * @return the pack's identifier
     */
    public PackIdentifier packId() {
        return packId;
    }

    /**
     * The tactic's {@code schema_version}, as text.
     *
     * @return the schema version
     */
    public String schemaVersion() {
        return schemaVersion;
    }

    /**
     * The tactic's {@code name}.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The steps, in authored order.
     *
     * @return an immutable, non-empty list
     */
    public List<TacticStep> steps() {
        return steps;
    }

    /**
     * The {@code purpose}.
     *
     * @return the purpose, empty when absent
     */
    public Optional<String> purpose() {
        return purpose;
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
     * The failure modes, in authored order.
     *
     * @return an immutable list
     */
    public List<String> failureModes() {
        return failureModes;
    }

    /**
     * The languages the tactic applies to, in authored order.
     *
     * @return an immutable list
     */
    public List<String> appliesToLanguages() {
        return appliesToLanguages;
    }

    /**
     * The references, in authored order with duplicates kept.
     *
     * @return an immutable list
     */
    public List<TacticReference> references() {
        return references;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Tactic that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Tactic[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Tactic}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable TacticIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String name;
        private final List<TacticStep> steps = new ArrayList<>();
        private Optional<String> purpose = Optional.empty();
        private Optional<String> notes = Optional.empty();
        private final List<String> failureModes = new ArrayList<>();
        private final List<String> appliesToLanguages = new ArrayList<>();
        private final List<TacticReference> references = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the tactic's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(TacticIdentifier value) {
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
         * Sets the name.
         *
         * @param value the name
         * @return this builder
         */
        public Builder name(String value) {
            this.name = value;
            return this;
        }

        /**
         * Appends a step.
         *
         * @param step the step
         * @return this builder
         */
        public Builder step(TacticStep step) {
            this.steps.add(step);
            return this;
        }

        /**
         * Sets the purpose.
         *
         * @param value the purpose, or null for none
         * @return this builder
         */
        public Builder purpose(@Nullable String value) {
            this.purpose = Optional.ofNullable(value);
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
         * Appends a failure mode.
         *
         * @param value the failure mode
         * @return this builder
         */
        public Builder failureMode(String value) {
            this.failureModes.add(value);
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
         * Appends a reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder reference(TacticReference value) {
            this.references.add(value);
            return this;
        }

        /**
         * Builds the tactic.
         *
         * @return the validated tactic
         * @throws NullPointerException when identity, pack, schema version or name was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Tactic build() {
            return new Tactic(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(name, "name"),
                    steps,
                    purpose,
                    notes,
                    failureModes,
                    appliesToLanguages,
                    references);
        }
    }
}
