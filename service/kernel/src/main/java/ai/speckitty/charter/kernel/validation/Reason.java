package ai.speckitty.charter.kernel.validation;

import java.util.Objects;

/**
 * The outcome of one {@link Rule} on one subject: which rule, whether it held, how much a breach weighs, its code,
 * where it points and what it says.
 *
 * @param rule the rule's own description, the same for every subject
 * @param result whether the rule held
 * @param severity how much a breach weighs; carried by a held reason too, as the rule declares it
 * @param code the machine-readable code
 * @param location where the reason points; {@link Location#NOWHERE} for a held reason or a value object
 * @param detail what the reason says about this subject; the rule's description for a held reason
 */
public record Reason(
        String rule, Evaluation result, Severity severity, ReasonCode code, Location location, String detail) {

    /** Rejects a null part. */
    public Reason {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(detail, "detail");
    }

    /**
     * A held invariant: the reason a {@link Constraints#of} constraint gives when its predicate holds.
     *
     * @param description the invariant's description
     * @return the held reason
     */
    public static Reason passed(String description) {
        return new Reason(
                description, Evaluation.PASS, Severity.VIOLATION, ReasonCode.INVARIANT, Location.NOWHERE, description);
    }

    /**
     * A breached invariant: the reason a {@link Constraints#of} constraint gives when its predicate fails, and the one
     * a value object throws in a {@link ConstraintBreachException} for a check no predicate expresses.
     *
     * @param description the invariant's description
     * @return the breached reason, a {@link Severity#VIOLATION}
     */
    public static Reason failed(String description) {
        return new Reason(
                description, Evaluation.FAIL, Severity.VIOLATION, ReasonCode.INVARIANT, Location.NOWHERE, description);
    }

    /**
     * Whether the rule held.
     *
     * @return true when the result is {@link Evaluation#PASS}
     */
    public boolean isPassing() {
        return result == Evaluation.PASS;
    }

    /**
     * Whether the rule was breached, at any severity.
     *
     * @return true when the result is {@link Evaluation#FAIL}
     */
    public boolean isFailing() {
        return result == Evaluation.FAIL;
    }

    /**
     * Whether the rule was breached at the given severity.
     *
     * @param weight the severity
     * @return true when this is a breach of that severity
     */
    public boolean isFailing(Severity weight) {
        return isFailing() && severity == weight;
    }
}
