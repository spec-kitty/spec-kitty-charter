package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The pack aggregate root, identified by its {@link PackIdentifier}. It composes the pack's descriptor, its
 * {@link Tactic}, {@link Directive}, {@link Styleguide} and {@link Paradigm} child entities and the violations found while reading, so a partial-tolerant load can be
 * represented. Two packs are equal when they have the same identity, whatever else they hold.
 *
 * <p>It holds no doctrine graph. Build one with {@link #builder()}.
 */
public final class Pack {

    private static final ModularRuleset<Pack> RULES = ModularRuleset.<Pack>emptyRules()
            .must(Constraints.of(
                    "Every tactic of a pack must belong to that pack.",
                    pack -> pack.tactics.stream()
                            .allMatch(tactic -> tactic.packId().equals(pack.id))))
            .must(Constraints.of("A pack must not hold two tactics with the same identifier.", pack -> {
                var seen = new HashSet<TacticIdentifier>();
                return pack.tactics.stream().allMatch(tactic -> seen.add(tactic.id()));
            }))
            .must(Constraints.of(
                    "Every directive of a pack must belong to that pack.",
                    pack -> pack.directives.stream()
                            .allMatch(child -> child.packId().equals(pack.id))))
            .must(Constraints.of("A pack must not hold two directives with the same identifier.", pack -> {
                var seen = new HashSet<DirectiveIdentifier>();
                return pack.directives.stream().allMatch(child -> seen.add(child.id()));
            }))
            .must(Constraints.of(
                    "Every styleguide of a pack must belong to that pack.",
                    pack -> pack.styleguides.stream()
                            .allMatch(child -> child.packId().equals(pack.id))))
            .must(Constraints.of("A pack must not hold two styleguides with the same identifier.", pack -> {
                var seen = new HashSet<StyleguideIdentifier>();
                return pack.styleguides.stream().allMatch(child -> seen.add(child.id()));
            }))
            .must(Constraints.of(
                    "Every paradigm of a pack must belong to that pack.",
                    pack -> pack.paradigms.stream()
                            .allMatch(child -> child.packId().equals(pack.id))))
            .must(Constraints.of("A pack must not hold two paradigms with the same identifier.", pack -> {
                var seen = new HashSet<ParadigmIdentifier>();
                return pack.paradigms.stream().allMatch(child -> seen.add(child.id()));
            }));

    private final PackIdentifier id;
    private final PackDescriptor descriptor;
    private final List<Tactic> tactics;
    private final List<Directive> directives;
    private final List<Styleguide> styleguides;
    private final List<Paradigm> paradigms;
    private final List<PackElementViolation> violations;

    /**
     * Reconstructs a pack from its parts; reachable only inside the domain package.
     *
     * @param id the pack's identity
     * @param descriptor the pack's descriptor
     * @param tactics the readable tactics, in a stable order
     * @param directives the readable directives, in a stable order
     * @param styleguides the readable styleguides, in a stable order
     * @param paradigms the readable paradigms, in a stable order
     * @param violations the files that could not be read or validated
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Pack(
            PackIdentifier id,
            PackDescriptor descriptor,
            List<Tactic> tactics,
            List<Directive> directives,
            List<Styleguide> styleguides,
            List<Paradigm> paradigms,
            List<PackElementViolation> violations) {
        this.id = Objects.requireNonNull(id, "id");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.tactics = List.copyOf(tactics);
        this.directives = List.copyOf(directives);
        this.styleguides = List.copyOf(styleguides);
        this.paradigms = List.copyOf(paradigms);
        this.violations = List.copyOf(violations);
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
     * The pack's identity.
     *
     * @return the identifier
     */
    public PackIdentifier id() {
        return id;
    }

    /**
     * The pack's descriptor.
     *
     * @return the descriptor
     */
    public PackDescriptor descriptor() {
        return descriptor;
    }

    /**
     * The readable tactics, in a stable order.
     *
     * @return an immutable list; empty for a descriptor-only pack
     */
    public List<Tactic> tactics() {
        return tactics;
    }

    /**
     * The readable directives, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Directive> directives() {
        return directives;
    }

    /**
     * The readable styleguides, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Styleguide> styleguides() {
        return styleguides;
    }

    /**
     * The readable paradigms, in a stable order.
     *
     * @return an immutable list; empty when the pack holds none
     */
    public List<Paradigm> paradigms() {
        return paradigms;
    }

    /**
     * The files that could not be read or validated while loading. This is the single, canonical owner of
     * read-time violations; no other surface may report them.
     *
     * @return an immutable list
     */
    public List<PackElementViolation> violations() {
        return violations;
    }

    /** Entity identity: equal when the identifier is equal, whatever else the packs hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Pack that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Pack[" + id.value() + "]";
    }

    /** Fluent builder for a {@link Pack}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable PackIdentifier id;
        private @Nullable PackDescriptor descriptor;
        private final List<Tactic> tactics = new ArrayList<>();
        private final List<Directive> directives = new ArrayList<>();
        private final List<Styleguide> styleguides = new ArrayList<>();
        private final List<Paradigm> paradigms = new ArrayList<>();
        private final List<PackElementViolation> violations = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the pack's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(PackIdentifier value) {
            this.id = value;
            return this;
        }

        /**
         * Sets the descriptor.
         *
         * @param value the descriptor
         * @return this builder
         */
        public Builder descriptor(PackDescriptor value) {
            this.descriptor = value;
            return this;
        }

        /**
         * Appends a tactic.
         *
         * @param tactic the tactic
         * @return this builder
         */
        public Builder tactic(Tactic tactic) {
            this.tactics.add(tactic);
            return this;
        }

        /**
         * Appends a directive.
         *
         * @param value the directive
         * @return this builder
         */
        public Builder directive(Directive value) {
            this.directives.add(value);
            return this;
        }

        /**
         * Appends a styleguide.
         *
         * @param value the styleguide
         * @return this builder
         */
        public Builder styleguide(Styleguide value) {
            this.styleguides.add(value);
            return this;
        }

        /**
         * Appends a paradigm.
         *
         * @param value the paradigm
         * @return this builder
         */
        public Builder paradigm(Paradigm value) {
            this.paradigms.add(value);
            return this;
        }

        /**
         * Appends a read violation.
         *
         * @param violation the violation
         * @return this builder
         */
        public Builder violation(PackElementViolation violation) {
            this.violations.add(violation);
            return this;
        }

        /**
         * Builds the pack.
         *
         * @return the validated pack
         * @throws NullPointerException when identity or descriptor was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Pack build() {
            return new Pack(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(descriptor, "descriptor"),
                    tactics,
                    directives,
                    styleguides,
                    paradigms,
                    violations);
        }
    }
}
