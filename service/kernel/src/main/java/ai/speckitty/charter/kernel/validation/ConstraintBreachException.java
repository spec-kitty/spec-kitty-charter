package ai.speckitty.charter.kernel.validation;

import java.util.Objects;

/** Thrown by a {@code guard} on the first violation; carries that {@link Reason}. */
public final class ConstraintBreachException extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 2L;

    /** The violation. */
    private final Reason reason;

    /**
     * Creates the exception for a violation.
     *
     * @param reason the breached reason
     */
    public ConstraintBreachException(Reason reason) {
        super("Constraint breached: " + Objects.requireNonNull(reason, "reason").detail());
        this.reason = reason;
    }

    /**
     * The violation.
     *
     * @return the breached reason, with its code, location and detail
     */
    public Reason reason() {
        return reason;
    }
}
