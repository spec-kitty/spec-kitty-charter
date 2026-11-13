package ai.speckitty.charter.kernel.validation;

import java.io.Serializable;
import java.util.Objects;

/**
 * The machine-readable code of a {@link Reason}, such as {@code file_not_held}, with the {@link Severity} a breach of
 * it weighs. A code owns its severity, as a pack finding's code does: one condition at two severities is two codes.
 *
 * <p>There is one form of code, so two codes with the same wire value and severity are equal wherever they come from.
 * A slice that types its codes declares an enum whose constants each hold a {@code ReasonCode}, and hands that to
 * {@link Constraint#coded}.
 *
 * @param value the code as it is written on the wire, never blank
 * @param severity how much a breach of it weighs
 */
public record ReasonCode(String value, Severity severity) implements Serializable {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * The code of a value object's invariant, a {@link Severity#VIOLATION}: what a rule reports when it names no code
     * of its own. A check whose findings reach a caller names its own code instead.
     */
    public static final ReasonCode INVARIANT = new ReasonCode("invariant", Severity.VIOLATION);

    /** Rejects a null part or a blank value. */
    public ReasonCode {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(severity, "severity");
        if (value.isBlank()) {
            throw new IllegalArgumentException("A reason code is not blank.");
        }
    }

    /**
     * A code whose breach is a {@link Severity#VIOLATION}.
     *
     * @param value the wire value
     * @return the code
     */
    public static ReasonCode of(String value) {
        return new ReasonCode(value, Severity.VIOLATION);
    }
}
