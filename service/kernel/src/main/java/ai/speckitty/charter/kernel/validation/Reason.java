package ai.speckitty.charter.kernel.validation;

import java.util.Objects;

/**
 * The evaluation of a single {@link Constraint}: its human description and whether it held.
 *
 * @param description the description of the constraint that was applied
 * @param result whether the constraint held
 */
public record Reason(String description, Evaluation result) {

    /** Rejects a null description or result. */
    public Reason {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(result, "result");
    }

    /**
     * A reason for a constraint that held.
     *
     * @param description the constraint's description
     * @return the passing reason
     */
    public static Reason passed(String description) {
        return new Reason(description, Evaluation.PASS);
    }

    /**
     * A reason for a constraint that was breached.
     *
     * @param description the constraint's description
     * @return the failing reason
     */
    public static Reason failed(String description) {
        return new Reason(description, Evaluation.FAIL);
    }

    /**
     * Whether the constraint held.
     *
     * @return true when the result is {@link Evaluation#PASS}
     */
    public boolean isPassing() {
        return result == Evaluation.PASS;
    }

    /**
     * Whether the constraint was breached.
     *
     * @return true when the result is {@link Evaluation#FAIL}
     */
    public boolean isFailing() {
        return result == Evaluation.FAIL;
    }
}
