package ai.speckitty.charter.kernel.validation;

import java.util.function.Consumer;

/**
 * A value together with the {@link ModularRuleset} it must adhere to, for fluent use of a validation result.
 *
 * @param <T> the validated type
 */
public final class Constrained<T> {

    private final T data;
    private final ModularRuleset<T> toAdhereTo;

    private Constrained(T data, ModularRuleset<T> toAdhereTo) {
        this.data = data;
        this.toAdhereTo = toAdhereTo;
    }

    /**
     * Pairs a value with a ruleset.
     *
     * @param value the value
     * @param ruleset the rules it must adhere to
     * @param <T> the validated type
     * @return the pairing
     */
    public static <T> Constrained<T> of(T value, ModularRuleset<T> ruleset) {
        return new Constrained<>(value, ruleset);
    }

    /**
     * Pairs a value with one more constraint on top of the current ones.
     *
     * @param constraint the extra constraint
     * @return the extended pairing; this one is unchanged
     */
    public Constrained<T> adheresTo(Constraint<T> constraint) {
        return new Constrained<>(data, toAdhereTo.must(constraint));
    }

    /**
     * Whether the value passes every constraint.
     *
     * @return true when no constraint is breached
     */
    public boolean isValid() {
        return rationale().isPassing();
    }

    /**
     * The full collect-all assessment.
     *
     * @return one reason per constraint
     */
    public Rationale rationale() {
        return toAdhereTo.check(data);
    }

    /**
     * Fail-fast: returns the value when valid.
     *
     * @return the validated value
     * @throws ConstraintBreachException naming the first breached constraint
     */
    public T guard() {
        toAdhereTo.guard(data);
        return data;
    }

    /**
     * Runs the consumer only when the value is valid.
     *
     * @param consumer receives the valid value
     */
    public void ifValid(Consumer<T> consumer) {
        if (isValid()) {
            consumer.accept(data);
        }
    }
}
