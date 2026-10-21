package ai.speckitty.charter.kernel.validation;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A single named rule that can be applied to a value of type {@code T}.
 *
 * <p>Equality is that of a record: both the description and the predicate must be equal, and a predicate is compared
 * by reference. Upstream ({@code modular-validators}) compared on the description alone, so two different rules with
 * the same wording were "equal"; that defect is deliberately not reproduced. Two constraints are therefore equal only
 * when they are built from the same predicate instance and the same description.
 *
 * @param description the human description, used in breach messages
 * @param predicate the rule; true means the value is acceptable
 * @param <T> the type of value the rule applies to
 */
public record Constraint<T>(String description, Predicate<T> predicate) {

    /** Rejects a null description or predicate. */
    public Constraint {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(predicate, "predicate");
    }

    /**
     * Applies the rule to a value.
     *
     * @param value the value to test
     * @return a {@link Reason} carrying this constraint's description and the outcome
     */
    public Reason applyTo(T value) {
        return predicate.test(value) ? Reason.passed(description) : Reason.failed(description);
    }
}
