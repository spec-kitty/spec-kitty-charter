package ai.speckitty.charter.kernel.validation;

/** The outcome of applying one constraint to a value. */
public enum Evaluation {
    /** The constraint holds. */
    PASS,
    /** The constraint is breached. */
    FAIL
}
