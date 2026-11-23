package ai.speckitty.charter.kernel.validation;

import java.io.Serializable;
import java.util.Objects;

/**
 * The outcome of one {@link Rule} on one subject: which rule, which element it is about, whether it held, its code (and
 * so its {@link Severity}), where it points and what it says.
 *
 * @param rule the rule's own description, the same for every subject
 * @param origin the element the reason is about (#614, ADR-0082 decision 4); {@link Origin#NONE} for none
 * @param result whether the rule held
 * @param code the machine-readable code, which owns the severity
 * @param location where the reason points; {@link Location#NOWHERE} for a held reason or a value object
 * @param detail what the reason says about this subject; the rule's description for a held reason
 */
public record Reason(String rule, Origin origin, Evaluation result, ReasonCode code, Location location, String detail)
        implements Serializable {

    /** Serialization version. */
    private static final long serialVersionUID = 2L;

    /** Rejects a null part. */
    public Reason {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(detail, "detail");
    }

    /**
     * A held invariant: the reason a {@link Constraints#of} constraint gives when its predicate holds, about no element
     * ({@link Origin#NONE}).
     *
     * @param description the invariant's description
     * @return the held reason
     */
    public static Reason passed(String description) {
        return new Reason(
                description, Origin.NONE, Evaluation.PASS, ReasonCode.INVARIANT, Location.NOWHERE, description);
    }

    /**
     * A breached invariant whose detail is its description: the reason a {@link Constraints#of} constraint gives when
     * its predicate fails.
     *
     * @param description the invariant's description, fixed text
     * @return the breached reason, a {@link Severity#VIOLATION}
     */
    public static Reason failed(String description) {
        return failed(description, description);
    }

    /**
     * A breached invariant that says something of this subject: what a value object throws in a
     * {@link ConstraintBreachException} for a check no predicate expresses, about no element
     * ({@link Origin#NONE}).
     *
     * @param rule the invariant, fixed text
     * @param detail what the breach says about this subject
     * @return the breached reason, a {@link Severity#VIOLATION}
     */
    public static Reason failed(String rule, String detail) {
        return new Reason(rule, Origin.NONE, Evaluation.FAIL, ReasonCode.INVARIANT, Location.NOWHERE, detail);
    }

    /**
     * How much a breach weighs: its code's severity.
     *
     * @return the severity
     */
    public Severity severity() {
        return code.severity();
    }

    /**
     * This reason at another location, about the same element.
     *
     * @param where the new location
     * @return the relocated reason; this one is unchanged
     */
    public Reason at(Location where) {
        return new Reason(rule, origin, result, code, where, detail);
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
        return isFailing() && severity() == weight;
    }
}
