package ai.speckitty.charter.kernel.validation;

import java.util.Objects;

/**
 * The machine-readable code of a {@link Reason}, such as {@code file_not_held}. A slice declares its codes as an enum
 * that implements this, so its codes are typed; {@link #of} names a code that has no enum of its own.
 */
public interface ReasonCode {

    /**
     * The code a value object's invariant carries when its rule names no code of its own. A {@code guard} breach
     * reports it; a check whose findings reach a caller names its own code instead.
     */
    ReasonCode INVARIANT = of("invariant");

    /**
     * The code as it is written on the wire.
     *
     * @return the code, never blank
     */
    String value();

    /**
     * A code by its wire value.
     *
     * @param value the wire value
     * @return the code
     */
    static ReasonCode of(String value) {
        return new Named(value);
    }

    /**
     * A code by its wire value alone.
     *
     * @param value the wire value, never blank
     */
    record Named(String value) implements ReasonCode {

        /** Rejects a null or blank value. */
        public Named {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("A reason code is not blank.");
            }
        }
    }
}
