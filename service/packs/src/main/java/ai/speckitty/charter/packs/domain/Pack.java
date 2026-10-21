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
 * {@link Tactic} child entities and the violations found while reading, so a partial-tolerant load can be
 * represented. Two packs are equal when they have the same identity, whatever else they hold.
 *
 * <p>Unlike {@link CharterPack} it holds no doctrine graph. Build one with {@link #builder()}.
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
            }));

    private final PackIdentifier id;
    private final PackDescriptor descriptor;
    private final List<Tactic> tactics;
    private final List<PackElementViolation> violations;

    /**
     * Reconstructs a pack from its parts; reachable only inside the domain package.
     *
     * @param id the pack's identity
     * @param descriptor the pack's descriptor
     * @param tactics the readable tactics, in a stable order
     * @param violations the files that could not be read or validated
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    Pack(PackIdentifier id, PackDescriptor descriptor, List<Tactic> tactics, List<PackElementViolation> violations) {
        this.id = Objects.requireNonNull(id, "id");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.tactics = List.copyOf(tactics);
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
     * The files that could not be read or validated while loading.
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
                    violations);
        }
    }
}
