package ai.speckitty.charter.kernel.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An ordered, immutable set of {@link Rule}s that is itself a rule, so a ruleset can hold rulesets. {@link #check}
 * collects every reason; {@link #guard} fails fast on the first violation, rule by rule, so a later rule may rely on
 * an earlier one having held.
 *
 * @param <T> the validated type
 */
public final class ModularRuleset<T> implements Rule<T> {

    private final List<Rule<T>> rules;

    private ModularRuleset(List<Rule<T>> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * A ruleset with no rules; every value passes.
     *
     * @param <T> the validated type
     * @return the empty ruleset
     */
    public static <T> ModularRuleset<T> emptyRules() {
        return new ModularRuleset<>(List.of());
    }

    /**
     * A ruleset of the given rules, applied in order.
     *
     * @param rules the rules
     * @param <T> the validated type
     * @return the ruleset
     */
    public static <T> ModularRuleset<T> of(List<? extends Rule<T>> rules) {
        return new ModularRuleset<>(List.copyOf(rules));
    }

    /**
     * A new ruleset with one more rule at the end; this ruleset is unchanged.
     *
     * @param rule the rule to add, a constraint or a ruleset
     * @return the extended ruleset
     */
    public ModularRuleset<T> must(Rule<T> rule) {
        Objects.requireNonNull(rule, "rule");
        var extended = new ArrayList<>(rules);
        extended.add(rule);
        return new ModularRuleset<>(extended);
    }

    /**
     * Collect-all: applies every rule.
     *
     * @param value the value to validate
     * @return every reason of every rule, in rule order
     */
    @Override
    public List<Reason> apply(T value) {
        List<Reason> reasons = new ArrayList<>();
        for (Rule<T> rule : rules) {
            reasons.addAll(rule.apply(value));
        }
        return List.copyOf(reasons);
    }

    /**
     * Fail-fast: applies the rules in order and stops at the first that finds a violation.
     *
     * @param value the value to validate
     * @throws ConstraintBreachException carrying the first violation
     */
    @Override
    public void guard(T value) {
        for (Rule<T> rule : rules) {
            rule.guard(value);
        }
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
