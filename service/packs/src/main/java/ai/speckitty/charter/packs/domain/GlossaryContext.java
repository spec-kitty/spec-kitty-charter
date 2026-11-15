package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.TermHolder;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * A glossary context of a {@link Pack} (ADR-0049 decisions 1 to 6): a ubiquitous-language bounded context with its
 * terms, the artifact of the kind {@link ArtifactKind#GLOSSARY}, identified by its {@link GlossaryContextIdentifier}.
 * Its URN is {@code glossary_context:<id>} (ADR-0053 decision 13), and each of its terms is addressed
 * {@code term:<id>/<term-id>}. It is pack content, unrelated to this repository's own docsite glossary (ADR-0029).
 * Two contexts are equal when they have the same identity, whatever their other fields hold.
 *
 * <p>Its terms keep their authored order; each term id appears once. Build one with {@link #builder()}.
 */
public final class GlossaryContext implements CharterArtifact, TermHolder {

    /** The one {@code schema_version} the {@code glossary} schema admits. */
    public static final String SCHEMA_VERSION = "1.0";

    private static final ModularRuleset<GlossaryContext> RULES = ModularRuleset.<GlossaryContext>emptyRules()
            .must(Constraints.notBlank()
                    .described("A glossary context needs a name that is not blank.")
                    .on(context -> context.name))
            .must(Constraints.notBlank()
                    .described("A glossary context needs a description that is not blank.")
                    .on(context -> context.description))
            .must(Constraints.of(
                    "A glossary context's tags are not blank.",
                    context -> context.tags.stream().noneMatch(String::isBlank)))
            .must(Constraints.of(
                    "A glossary context names each tag once.",
                    context -> Set.copyOf(context.tags).size() == context.tags.size()))
            .must(Constraints.of("A glossary context needs at least one term.", context -> !context.terms.isEmpty()));

    private final GlossaryContextIdentifier id;
    private final PackIdentifier packId;
    private final String name;
    private final String description;
    private final List<String> tags;
    private final AppliesTo appliesTo;
    private final List<GlossaryTerm> terms;

    private GlossaryContext(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.packId = Objects.requireNonNull(builder.packId, "packId");
        this.name = Objects.requireNonNull(builder.name, "name");
        this.description = Objects.requireNonNull(builder.description, "description");
        this.tags = List.copyOf(builder.tags);
        this.appliesTo = Objects.requireNonNull(builder.appliesTo, "appliesTo");
        this.terms = List.copyOf(builder.terms);
        RULES.guard(this);
        List<String> repeated = repeatedTermIds(this.terms);
        if (!repeated.isEmpty()) {
            // The rule is fixed text; the detail names the context and the repeated ids.
            throw new ConstraintBreachException(Reason.failed(
                    "Each term's id is unique within its glossary context.",
                    "Each term's id must be unique within glossary context " + id.value() + "; repeated: " + repeated));
        }
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
     * The context's identity, which is also its file stem.
     *
     * @return the identifier
     */
    public GlossaryContextIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GLOSSARY;
    }

    /**
     * The pack that owns this context.
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
     * The {@code schema_version}, the one the {@code glossary} schema admits.
     *
     * @return {@value #SCHEMA_VERSION}
     */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(SCHEMA_VERSION);
    }

    /**
     * The context's label, its {@code name}.
     *
     * @return the name
     */
    @Override
    public String label() {
        return name;
    }

    /**
     * The context's {@code name}.
     *
     * @return the name, never blank
     */
    public String name() {
        return name;
    }

    /**
     * The context's {@code description}: its applicability, Contextive's domain vision statement.
     *
     * @return the description, always present
     */
    @Override
    public Optional<String> description() {
        return Optional.of(description);
    }

    /**
     * The context's {@code tags}, in authored order.
     *
     * @return an immutable list; empty when the file states none
     */
    public List<String> tags() {
        return tags;
    }

    /**
     * Where the context applies, advisory (ADR-0049 decision 14).
     *
     * @return the {@code applies_to}; {@link AppliesTo#NOWHERE_IN_PARTICULAR} when the file states none
     */
    public AppliesTo appliesTo() {
        return appliesTo;
    }

    /**
     * The terms, in authored order.
     *
     * @return an immutable list, never empty
     */
    public List<GlossaryTerm> terms() {
        return terms;
    }

    /**
     * The term of an id.
     *
     * @param termId the term's id
     * @return the term; empty when the context holds no term of that id
     */
    public Optional<GlossaryTerm> term(TermIdentifier termId) {
        return terms.stream().filter(term -> term.id().equals(termId)).findFirst();
    }

    /**
     * Whether the context holds a term of an id; an id outside the term grammar names no term, so it is not held, never
     * a fault.
     *
     * @param termId the term's id, as a request names it
     * @return {@code true} when one of its terms has that id
     */
    @Override
    public boolean holdsTerm(String termId) {
        return IdentifierGrammar.isLetterFirstSlug(termId)
                && term(new TermIdentifier(termId)).isPresent();
    }

    /**
     * The relationships the context derives from its terms' {@code see_also}: one {@code vocabulary} relationship from
     * the context to each distinct entry, bare or pack-qualified as written, in term order (operator ruling 2026-11-14).
     * A {@code see_also} entry is a reference like any other, so the compose's reference check, the reference reads and
     * the inbound scan judge it as they judge a graph-document edge. It is derived runtime data, never authored: the
     * round trip never compares it (ADR-0064), as a guard's {@code implemented_by}.
     *
     * @return the derived relationships; empty when no term names another
     */
    @Override
    public List<Relationship> derivedRelationships() {
        Urn source = urn();
        return terms.stream()
                .flatMap(term -> term.seeAlso().stream())
                .distinct()
                .map(reference -> Relationship.of(
                        source,
                        Optional.empty(),
                        reference.asUrn().orElseThrow(),
                        reference.packName(),
                        RelationType.VOCABULARY,
                        Optional.empty(),
                        Optional.empty()))
                .toList();
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof GlossaryContext that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GlossaryContext[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** The term ids that occur more than once, sorted; empty when every id is unique. */
    private static List<String> repeatedTermIds(List<GlossaryTerm> terms) {
        Set<String> seen = new HashSet<>();
        TreeSet<String> repeated = new TreeSet<>();
        for (GlossaryTerm term : terms) {
            if (!seen.add(term.id().value())) {
                repeated.add(term.id().value());
            }
        }
        return List.copyOf(repeated);
    }

    /** Fluent builder for a {@link GlossaryContext}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable GlossaryContextIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String name;
        private @Nullable String description;
        private List<String> tags = List.of();
        private AppliesTo appliesTo = AppliesTo.NOWHERE_IN_PARTICULAR;
        private final List<GlossaryTerm> terms = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the context's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(GlossaryContextIdentifier value) {
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
         * Sets the tags.
         *
         * @param value the tags, in authored order; empty for none
         * @return this builder
         */
        public Builder tags(List<String> value) {
            this.tags = List.copyOf(value);
            return this;
        }

        /**
         * Sets where the context applies.
         *
         * @param value the {@code applies_to}
         * @return this builder
         */
        public Builder appliesTo(AppliesTo value) {
            this.appliesTo = Objects.requireNonNull(value, "appliesTo");
            return this;
        }

        /**
         * Appends a term.
         *
         * @param value the term
         * @return this builder
         */
        public Builder term(GlossaryTerm value) {
            this.terms.add(Objects.requireNonNull(value, "term"));
            return this;
        }

        /**
         * Builds the context.
         *
         * @return the validated context
         * @throws NullPointerException when identity, pack, name or description was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public GlossaryContext build() {
            return new GlossaryContext(this);
        }
    }
}
