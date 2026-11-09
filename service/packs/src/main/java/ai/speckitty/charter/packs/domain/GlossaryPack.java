package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * A glossary pack of a {@link Pack}: a named, provenance-tagged collection of canonical terms, identified by its
 * {@link GlossaryPackIdentifier}. It is pack content, unrelated to this repository's own docsite glossary
 * (ADR-0029). Two glossary packs are equal when they have the same identity, whatever their other fields hold.
 *
 * <p>Its terms keep their authored order; each surface appears once. Build one with {@link #builder()}.
 */
public final class GlossaryPack implements CharterArtifact {

    private static final ModularRuleset<GlossaryPack> RULES = ModularRuleset.<GlossaryPack>emptyRules()
            .must(Constraints.of("A glossary pack needs at least one term.", pack -> !pack.terms.isEmpty()));

    private final GlossaryPackIdentifier id;
    private final PackIdentifier packId;
    private final String provenance;
    private final Optional<String> description;
    private final List<GlossaryTerm> terms;

    /**
     * Reconstructs a glossary pack from its parts; reachable only inside the domain package.
     *
     * @param id the glossary pack's identity
     * @param packId the pack that owns it
     * @param provenance the provenance text
     * @param description the description, when stated
     * @param terms the terms, in authored order
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    GlossaryPack(
            GlossaryPackIdentifier id,
            PackIdentifier packId,
            String provenance,
            Optional<String> description,
            List<GlossaryTerm> terms) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.description = Objects.requireNonNull(description, "description");
        this.terms = List.copyOf(terms);
        RULES.guard(this);
        List<String> repeated = duplicateSurfaces(this.terms);
        if (!repeated.isEmpty()) {
            // A rule's description is fixed text; this one names the surfaces, as core's model does.
            throw new ConstraintBreachException("Each term's surface must be unique within glossary pack " + id.value()
                    + "; repeated: " + repeated);
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
     * The glossary pack's identity.
     *
     * @return the identifier
     */
    public GlossaryPackIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GLOSSARY_PACK;
    }

    /**
     * The pack that owns this glossary pack.
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
     * A glossary pack file has no {@code schema_version}.
     *
     * @return always empty
     */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.empty();
    }

    /**
     * The glossary pack's label, which is its id: the file has no title.
     *
     * @return the id
     */
    @Override
    public String label() {
        return id.value();
    }

    /**
     * The glossary pack's {@code description}, when stated.
     *
     * @return the description
     */
    @Override
    public Optional<String> description() {
        return description;
    }

    /**
     * The glossary pack's {@code provenance}, as text.
     *
     * @return the provenance
     */
    public String provenance() {
        return provenance;
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
     * A glossary pack mints no relationships: its terms' cross-references ({@code see_also}) name surfaces, not
     * artifacts, and the pinned shard has no glossary-pack edges.
     *
     * @return an empty list
     */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof GlossaryPack that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GlossaryPack[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** The surfaces that occur more than once, sorted; empty when every surface is unique. */
    private static List<String> duplicateSurfaces(List<GlossaryTerm> terms) {
        Map<String, Integer> counts = new HashMap<>();
        terms.forEach(term -> counts.merge(term.surface(), 1, Integer::sum));
        TreeSet<String> repeated = new TreeSet<>();
        counts.forEach((surface, count) -> {
            if (count > 1) {
                repeated.add(surface);
            }
        });
        return Collections.unmodifiableList(new ArrayList<>(repeated));
    }

    /** Fluent builder for a {@link GlossaryPack}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable GlossaryPackIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String provenance;
        private Optional<String> description = Optional.empty();
        private final List<GlossaryTerm> terms = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the glossary pack's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(GlossaryPackIdentifier value) {
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
         * Sets the provenance text.
         *
         * @param value the provenance
         * @return this builder
         */
        public Builder provenance(String value) {
            this.provenance = value;
            return this;
        }

        /**
         * Sets the description.
         *
         * @param value the description, possibly empty
         * @return this builder
         */
        public Builder description(Optional<String> value) {
            this.description = value;
            return this;
        }

        /**
         * Appends a term.
         *
         * @param value the term
         * @return this builder
         */
        public Builder term(GlossaryTerm value) {
            this.terms.add(value);
            return this;
        }

        /**
         * Builds the glossary pack.
         *
         * @return the validated glossary pack
         * @throws NullPointerException when identity, pack or provenance was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public GlossaryPack build() {
            return new GlossaryPack(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(provenance, "provenance"),
                    description,
                    terms);
        }
    }
}
