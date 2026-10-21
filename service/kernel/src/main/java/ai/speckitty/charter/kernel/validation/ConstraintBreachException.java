package ai.speckitty.charter.kernel.validation;

/** Thrown by {@link ModularRuleset#guard} on the first breached constraint; names that constraint. */
public final class ConstraintBreachException extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** The breached constraint. */
    private final String constraintDescription;

    /**
     * Creates the exception for a breached constraint.
     *
     * @param constraintDescription the description of the breached constraint
     */
    public ConstraintBreachException(String constraintDescription) {
        super("Constraint breached: " + constraintDescription);
        this.constraintDescription = constraintDescription;
    }

    /**
     * The breached constraint.
     *
     * @return its description
     */
    public String constraintDescription() {
        return constraintDescription;
    }
}
