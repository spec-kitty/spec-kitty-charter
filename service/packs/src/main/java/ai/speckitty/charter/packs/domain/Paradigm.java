package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A paradigm of a {@link Pack}: a way of working, identified by its {@link ParadigmIdentifier}. Two paradigms are
 * equal when they have the same identity, whatever their other fields hold.
 *
 * <p>It owns its directive references and typed references; their order and duplicates are significant. Build one
 * with {@link #builder()}.
 */
public final class Paradigm implements CharterArtifact {

    private static final Pattern SCHEMA_VERSION = Pattern.compile("^1\\.0$");

    private static final ModularRuleset<Paradigm> RULES = ModularRuleset.<Paradigm>emptyRules()
            .must(Constraints.of(
                    "A paradigm needs a schema version of 1.0.",
                    paradigm -> SCHEMA_VERSION.matcher(paradigm.schemaVersion).matches()))
            .must(Constraints.of("A paradigm needs a name that is not blank.", paradigm -> !paradigm.name.isBlank()))
            .must(Constraints.of(
                    "A paradigm needs a summary that is not blank.", paradigm -> !paradigm.summary.isBlank()))
            .must(Constraints.of(
                    "A paradigm's directive references must not be blank.",
                    paradigm -> paradigm.directiveRefs.stream().noneMatch(String::isBlank)));

    private final ParadigmIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String name;
    private final String summary;
    private final List<String> directiveRefs;
    private final List<ParadigmReference> references;

    /**
     * Reconstructs a paradigm from its parts; reachable only inside the domain package.
     *
     * @param id the paradigm's identity
     * @param packId the pack that owns the paradigm
     * @param schemaVersion the schema version text
     * @param name the paradigm's name
     * @param summary the paradigm's summary
     * @param directiveRefs the referenced directive ids, in authored order
     * @param references the typed references, in authored order with duplicates kept
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Paradigm(
            ParadigmIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String name,
            String summary,
            List<String> directiveRefs,
            List<ParadigmReference> references) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.name = Objects.requireNonNull(name, "name");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.directiveRefs = List.copyOf(directiveRefs);
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
     * The paradigm's identity.
     *
     * @return the identifier
     */
    public ParadigmIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.PARADIGM;
    }

    /**
     * The pack that owns this paradigm.
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
     * The paradigm's {@code schema_version}, as text.
     *
     * @return the schema version
     */
    @Override
    public String schemaVersion() {
        return schemaVersion;
    }

    /**
     * The paradigm's {@code name}.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    @Override
    public String label() {
        return name;
    }

    /**
     * The paradigm's {@code summary}.
     *
     * @return the summary
     */
    public String summary() {
        return summary;
    }

    @Override
    public Optional<String> description() {
        return Optional.of(summary);
    }

    /**
     * The referenced directive ids, in authored order.
     *
     * @return an immutable list
     */
    public List<String> directiveRefs() {
        return directiveRefs;
    }

    /**
     * The typed references, in authored order with duplicates kept.
     *
     * @return an immutable list
     */
    public List<ParadigmReference> references() {
        return references;
    }

    @Override
    public List<DrgEdge> edges() {
        List<ArtifactEdges.TypedRef> typed = references.stream()
                .map(ref -> new ArtifactEdges.TypedRef(ref.type(), ref.id(), Optional.empty(), Optional.empty()))
                .toList();
        return ArtifactEdges.build(urn(), typed, NodeKind.of(ArtifactKind.DIRECTIVE), directiveRefs);
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Paradigm that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Paradigm[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Paradigm}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable ParadigmIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String name;
        private @Nullable String summary;
        private final List<String> directiveRefs = new ArrayList<>();
        private final List<ParadigmReference> references = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the paradigm's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(ParadigmIdentifier value) {
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
         * Appends a directive reference.
         *
         * @param value the directive id
         * @return this builder
         */
        public Builder directiveRef(String value) {
            this.directiveRefs.add(value);
            return this;
        }

        /**
         * Appends a typed reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder reference(ParadigmReference value) {
            this.references.add(value);
            return this;
        }

        /**
         * Builds the paradigm.
         *
         * @return the validated paradigm
         * @throws NullPointerException when identity, pack, schema version, name or summary was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Paradigm build() {
            return new Paradigm(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(name, "name"),
                    Objects.requireNonNull(summary, "summary"),
                    directiveRefs,
                    references);
        }
    }
}
