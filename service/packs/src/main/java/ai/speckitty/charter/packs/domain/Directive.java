package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
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
 * A directive of a {@link Pack}: a binding governance rule, a child entity identified by its
 * {@link DirectiveIdentifier}. Two directives are equal when they have the same identity, whatever their other
 * fields hold; the fidelity of a round trip is asserted on the storage representation, not here.
 *
 * <p>List order and duplicates are significant. Build one with {@link #builder()}.
 */
public final class Directive implements CharterArtifact {

    private static final Pattern SCHEMA_VERSION = Pattern.compile("^1\\.0$");

    private static final ModularRuleset<Directive> RULES = ModularRuleset.<Directive>emptyRules()
            .must(Constraints.of(
                    "A directive needs a schema version of 1.0.",
                    directive -> SCHEMA_VERSION.matcher(directive.schemaVersion).matches()))
            .must(Constraints.of(
                    "A directive needs a title that is not blank.", directive -> !directive.title.isBlank()))
            .must(Constraints.of(
                    "A directive needs an intent that is not blank.", directive -> !directive.intent.isBlank()))
            .must(Constraints.of(
                    "A lenient-adherence directive needs at least one explicit allowance.",
                    directive -> directive.enforcement != Enforcement.LENIENT_ADHERENCE
                            || !directive.explicitAllowances.isEmpty()))
            .must(Constraints.of(
                    "Every procedure of a directive must be non-blank.",
                    directive -> directive.procedures.stream().noneMatch(String::isBlank)));

    private final DirectiveIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final String intent;
    private final Enforcement enforcement;
    private final Optional<String> scope;
    private final List<String> procedures;
    private final List<String> integrityRules;
    private final List<String> validationCriteria;
    private final List<String> explicitAllowances;
    private final List<DirectiveReference> references;

    /**
     * Reconstructs a directive from its parts; reachable only inside the domain package.
     *
     * @param id the directive's identity
     * @param packId the pack that owns the directive
     * @param schemaVersion the schema version text
     * @param title the title
     * @param intent the intent
     * @param enforcement how strictly the directive binds
     * @param scope the scope, possibly empty
     * @param procedures the ids of the procedures it requires, in authored order
     * @param integrityRules the integrity rules, in authored order
     * @param validationCriteria the validation criteria, in authored order
     * @param explicitAllowances the explicit allowances, in authored order
     * @param references the references, in authored order with duplicates kept
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Directive(
            DirectiveIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String title,
            String intent,
            Enforcement enforcement,
            Optional<String> scope,
            List<String> procedures,
            List<String> integrityRules,
            List<String> validationCriteria,
            List<String> explicitAllowances,
            List<DirectiveReference> references) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(title, "title");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.procedures = List.copyOf(procedures);
        this.integrityRules = List.copyOf(integrityRules);
        this.validationCriteria = List.copyOf(validationCriteria);
        this.explicitAllowances = List.copyOf(explicitAllowances);
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
     * The directive's identity.
     *
     * @return the identifier
     */
    public DirectiveIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.DIRECTIVE;
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
        return Optional.of(intent);
    }

    @Override
    public List<Relationship> relationships() {
        List<ArtifactEdges.TypedRef> typed = references.stream()
                .map(ref -> new ArtifactEdges.TypedRef(ref.type(), ref.id(), ref.when(), ref.reason()))
                .toList();
        return ArtifactEdges.build(urn(), typed, NodeKind.of(ArtifactKind.PROCEDURE), procedures);
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
     * The {@code intent}.
     *
     * @return the intent
     */
    public String intent() {
        return intent;
    }

    /**
     * How strictly the directive binds.
     *
     * @return the enforcement level
     */
    public Enforcement enforcement() {
        return enforcement;
    }

    /**
     * The {@code scope}.
     *
     * @return the scope, empty when absent
     */
    public Optional<String> scope() {
        return scope;
    }

    /**
     * The ids of the procedures the directive requires, in authored order.
     *
     * @return an immutable list
     */
    public List<String> procedures() {
        return procedures;
    }

    /**
     * The integrity rules, in authored order.
     *
     * @return an immutable list
     */
    public List<String> integrityRules() {
        return integrityRules;
    }

    /**
     * The validation criteria, in authored order.
     *
     * @return an immutable list
     */
    public List<String> validationCriteria() {
        return validationCriteria;
    }

    /**
     * The explicit allowances, in authored order.
     *
     * @return an immutable list
     */
    public List<String> explicitAllowances() {
        return explicitAllowances;
    }

    /**
     * The references, in authored order with duplicates kept.
     *
     * @return an immutable list
     */
    public List<DirectiveReference> references() {
        return references;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Directive that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Directive[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Directive}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable DirectiveIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String title;
        private @Nullable String intent;
        private @Nullable Enforcement enforcement;
        private Optional<String> scope = Optional.empty();
        private final List<String> procedures = new ArrayList<>();
        private final List<String> integrityRules = new ArrayList<>();
        private final List<String> validationCriteria = new ArrayList<>();
        private final List<String> explicitAllowances = new ArrayList<>();
        private final List<DirectiveReference> references = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the identity.
         *
         * @param value the identity
         * @return this builder
         */
        public Builder id(DirectiveIdentifier value) {
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
         * Sets the intent.
         *
         * @param value the intent
         * @return this builder
         */
        public Builder intent(String value) {
            this.intent = value;
            return this;
        }

        /**
         * Sets the enforcement level.
         *
         * @param value the enforcement level
         * @return this builder
         */
        public Builder enforcement(Enforcement value) {
            this.enforcement = value;
            return this;
        }

        /**
         * Sets the scope.
         *
         * @param value the scope, or null for none
         * @return this builder
         */
        public Builder scope(@Nullable String value) {
            this.scope = Optional.ofNullable(value);
            return this;
        }

        /**
         * Appends a procedure id.
         *
         * @param value the procedure id
         * @return this builder
         */
        public Builder procedure(String value) {
            this.procedures.add(value);
            return this;
        }

        /**
         * Appends a integrity rule.
         *
         * @param value the integrity rule
         * @return this builder
         */
        public Builder integrityRule(String value) {
            this.integrityRules.add(value);
            return this;
        }

        /**
         * Appends a validation criterion.
         *
         * @param value the validation criterion
         * @return this builder
         */
        public Builder validationCriterion(String value) {
            this.validationCriteria.add(value);
            return this;
        }

        /**
         * Appends a explicit allowance.
         *
         * @param value the explicit allowance
         * @return this builder
         */
        public Builder explicitAllowance(String value) {
            this.explicitAllowances.add(value);
            return this;
        }

        /**
         * Appends a reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder reference(DirectiveReference value) {
            this.references.add(value);
            return this;
        }

        /**
         * Builds the directive.
         *
         * @return the validated directive
         * @throws NullPointerException when identity, pack, schema version, title, intent or enforcement was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Directive build() {
            return new Directive(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(title, "title"),
                    Objects.requireNonNull(intent, "intent"),
                    Objects.requireNonNull(enforcement, "enforcement"),
                    scope,
                    procedures,
                    integrityRules,
                    validationCriteria,
                    explicitAllowances,
                    references);
        }
    }
}
