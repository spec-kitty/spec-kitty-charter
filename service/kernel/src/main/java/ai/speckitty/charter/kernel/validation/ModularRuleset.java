package ai.speckitty.charter.kernel.validation;

import java.util.List;
import java.util.Objects;

/**
 * An ordered, immutable set of {@link Constraint}s with two evaluation strategies: {@link #guard} fails fast on the
 * first breach, {@link #check} collects every breach.
 *
 * @param <T> the validated type
 */
public final class ModularRuleset<T> {

    private final List<Constraint<T>> rules;

    private ModularRuleset(List<Constraint<T>> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * A ruleset with no constraints; every value passes.
     *
     * @param <T> the validated type
     * @return the empty ruleset
     */
    public static <T> ModularRuleset<T> emptyRules() {
        return new ModularRuleset<>(List.of());
    }

    /**
     * A ruleset of the given constraints, evaluated in order.
     *
     * @param rules the constraints
     * @param <T> the validated type
     * @return the ruleset
     */
    public static <T> ModularRuleset<T> of(List<Constraint<T>> rules) {
        return new ModularRuleset<>(rules);
    }

    /**
     * A new ruleset with one more constraint at the end; this ruleset is unchanged.
     *
     * @param rule the constraint to add
     * @return the extended ruleset
     */
    public ModularRuleset<T> must(Constraint<T> rule) {
        Objects.requireNonNull(rule, "rule");
        var extended = new java.util.ArrayList<>(rules);
        extended.add(rule);
        return new ModularRuleset<>(extended);
    }

    /**
     * Fail-fast: stops at the first breached constraint.
     *
     * @param value the value to validate
     * @throws ConstraintBreachException naming the first breached constraint
     */
    public void guard(T value) {
        for (var rule : rules) {
            if (rule.applyTo(value).isFailing()) {
                throw new ConstraintBreachException(rule.description());
            }
        }
    }

    /**
     * Collect-all: evaluates every constraint and reports each outcome.
     *
     * @param value the value to validate
     * @return one reason per constraint, in order
     */
    public Rationale check(T value) {
        return new Rationale(rules.stream().map(rule -> rule.applyTo(value)).toList());
    }

    /**
     * Binds a value to this ruleset.
     *
     * @param value the value to validate
     * @return the {@link Constrained} pairing
     */
    public Constrained<T> constrain(T value) {
        return Constrained.of(value, this);
    }
}
